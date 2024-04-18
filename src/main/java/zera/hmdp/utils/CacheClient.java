package zera.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import zera.hmdp.entity.Shop;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static zera.hmdp.utils.RedisConstants.*;

@Component
@Slf4j
public class CacheClient {

    private StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,timeUnit);
    }

    public void setWithLogicalExpire(String key,Object value,Long time, TimeUnit timeUnit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallback,Long time, TimeUnit timeUnit) {

        String key = keyPrefix + id;
        //1.从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //3.存在，直接返回
            return JSONUtil.toBean(json, type);
        }

        //判读 命中的是否是空值
        if(json != null){
            //  防止缓存穿透  直接返回空值
            return null;
        }

        //4.不存在，查询数据库
//        Shop shop = this.getById(id);
        R r = dbFallback.apply(id);

        //5.数据库中不存在 直接返回错误 404

        //现在不仅 返回错误信息 还要将空值写入redis
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //6.数据库中存在，先写入redis缓存，再返回用户
        this.set(key,r,time,timeUnit);
        return r;
    }
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }
    public <ID,R> R queryWithLogicalExpire(String keyPrefix,ID id,Class<R> type,
                                        Function<ID,R> dbFallback,Long time, TimeUnit timeUnit) {

        String key = keyPrefix + id;
        //1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            //3.存在，直接返回
            return null;
        }

        //命中 需要把json 反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);

        LocalDateTime expireTime = redisData.getExpireTime();

        //判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //未过期
            return r;
        }

        //过期 先获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if(isLock){
            //获得：开启 独立线程 独立线程去执行 缓存重建
            CACHE_REBUILD_EXECUTOR.submit(
                    () -> {
                        try {
                            R r1 = dbFallback.apply(id);

                            this.setWithLogicalExpire(key,r1,time,TimeUnit.MINUTES);
                        }catch (Exception e){
                            throw new RuntimeException(e);
                        }finally {
                            unLock(lockKey);}

                    }
            );

        }
        return r;
        /*
         * 判断是否过期
         * 未过期 直接返回店铺信息
         * 已过期需要缓存重建
         *
         * 重建：先获取互斥锁
         * 判断是否获得互斥锁
         * 未获得：直接返回
         * 获得：开启 独立线程 独立线程去执行 缓存重建
         *最后 返回过期的商铺信息
         * */


    }

}

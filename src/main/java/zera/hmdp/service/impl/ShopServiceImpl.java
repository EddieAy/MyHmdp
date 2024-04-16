package zera.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zera.hmdp.dto.Result;
import zera.hmdp.entity.Shop;
import zera.hmdp.mapper.ShopMapper;
import zera.hmdp.service.IShopService;
import zera.hmdp.utils.RedisConstants;
import zera.hmdp.utils.RedisData;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static zera.hmdp.utils.RedisConstants.*;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
//        Shop shop = queryWithPassThrough(id);
//        Shop shop = queryWithMutex(id);
        Shop shop = queryWithLogicalExpire(id);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {

        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }


        //1.先更新数据库
        updateById(shop);
        //2.再删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    public   void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        Shop shop = getById(id);

        Thread.sleep(200);
        RedisData redisData = new RedisData();

        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
    }
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }

    public Shop queryWithMutex(Long id) {

        String key = CACHE_SHOP_KEY + id;
        //1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3.存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        //判读 命中的是否是空值
        if(shopJson != null){
            //  防止缓存穿透  直接返回空值
            return null;
        }


        //开始实现 缓存重建
        /*
        * 获取互斥锁 判断是否成功
        * 如果失败 休眠并重试
        * 如果成功  根据id查询数据库
        * 释放互斥锁
        * */
        Shop shop = null;
        try {
            boolean isLock = tryLock(LOCK_SHOP_KEY);
            if(!isLock){
                Thread.sleep(100);
                return queryWithMutex(id);
            }


            //4.不存在，查询数据库
            shop = this.getById(id);

            //模拟重建延迟
            Thread.sleep(200);
            //5.数据库中不存在 直接返回错误 404
            //现在不仅 返回错误信息 还要将空值写入redis
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            //6.数据库中存在，先写入redis缓存，再返回用户
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            unLock(LOCK_SHOP_KEY);
        }

        //释放互斥锁

        return shop;
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Shop queryWithLogicalExpire(Long id) {

        String key = CACHE_SHOP_KEY + id;
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
        Shop shop = JSONUtil.toBean(data, Shop.class);

        LocalDateTime expireTime = redisData.getExpireTime();

        //判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //未过期
            return shop;
        }

        //过期 先获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if(isLock){
            //获得：开启 独立线程 独立线程去执行 缓存重建
            CACHE_REBUILD_EXECUTOR.submit(
                    () -> {
                        try {
                            this.saveShop2Redis(id,20L);
                        }catch (Exception e){
                            throw new RuntimeException(e);
                        }finally {
                            unLock(lockKey);}

                        }
            );

        }
        return shop;
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

    public Shop queryWithPassThrough(Long id) {

        String key = CACHE_SHOP_KEY + id;
        //1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3.存在，直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        //判读 命中的是否是空值
        if(shopJson != null){
            //  防止缓存穿透  直接返回空值
            return null;
        }

        //4.不存在，查询数据库
        Shop shop = this.getById(id);
        //5.数据库中不存在 直接返回错误 404
        //现在不仅 返回错误信息 还要将空值写入redis
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //6.数据库中存在，先写入redis缓存，再返回用户
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }
}

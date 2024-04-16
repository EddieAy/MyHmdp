package zera.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import zera.hmdp.dto.Result;
import zera.hmdp.entity.ShopType;
import zera.hmdp.mapper.ShopTypeMapper;
import zera.hmdp.service.IShopTypeService;

import javax.annotation.Resource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static zera.hmdp.utils.RedisConstants.CACHE_SHOPTYPE_KEY;
import static zera.hmdp.utils.RedisConstants.CACHE_SHOPTYPE_TTL;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public Result queryTypeList(){
        String cacheShoptypeKey = CACHE_SHOPTYPE_KEY;
        String typeListJson = stringRedisTemplate.opsForValue().get(cacheShoptypeKey);

        if(typeListJson != null){
            List<ShopType> typeList = JSONUtil.toList(typeListJson, ShopType.class);
            return Result.ok(typeList);
        }

        List<ShopType> typeList = query().orderByAsc("sort").list();

        if(typeList == null || typeList.isEmpty()){
            return Result.fail("商品分类列表不存在");
        }

        stringRedisTemplate.opsForValue().set(cacheShoptypeKey,JSONUtil.toJsonStr(typeList),CACHE_SHOPTYPE_TTL, TimeUnit.MINUTES);

        return Result.ok(typeList);

    }

}

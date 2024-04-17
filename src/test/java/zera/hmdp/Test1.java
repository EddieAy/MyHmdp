package zera.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import zera.hmdp.entity.Shop;
import zera.hmdp.service.impl.ShopServiceImpl;
import zera.hmdp.utils.CacheClient;
import zera.hmdp.utils.RedisConstants;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@SpringBootTest
public class Test1 {
    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

//    @Test
//    void t1(){
//        shopService.saveShop2Redis(1L,10L);
//    }

    @Test
    void testSaveShop(){
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY + 1L,shop,10L, TimeUnit.SECONDS);
    }

}

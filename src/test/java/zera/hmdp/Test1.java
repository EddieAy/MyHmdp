package zera.hmdp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import zera.hmdp.service.impl.ShopServiceImpl;

import javax.annotation.Resource;

@SpringBootTest
public class Test1 {
    @Resource
    private ShopServiceImpl shopService;

    @Test
    void t1(){
        shopService.saveShop2Redis(1L,10L);
    }

}

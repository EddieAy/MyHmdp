package zera.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import zera.hmdp.dto.Result;
import zera.hmdp.entity.Shop;
import zera.hmdp.entity.ShopType;

public interface IShopService extends IService<Shop> {
    Result queryById(Long id);

    Result update(Shop shop);
}

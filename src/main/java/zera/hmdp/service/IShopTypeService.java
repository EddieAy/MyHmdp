package zera.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import zera.hmdp.dto.Result;
import zera.hmdp.entity.ShopType;

public interface IShopTypeService extends IService<ShopType> {

    Result queryTypeList();
}

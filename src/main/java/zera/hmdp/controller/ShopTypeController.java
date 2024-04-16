package zera.hmdp.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zera.hmdp.dto.Result;
import zera.hmdp.entity.ShopType;
import zera.hmdp.service.IShopTypeService;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {

    @Resource
    private IShopTypeService typeService;

//    @GetMapping("list")
//    public Result queryTypeList(){
//        List<ShopType> typeList = typeService.query().orderByAsc("sort").list();
//        return Result.ok(typeList);
//    }

    @GetMapping("list")
    public Result queryTypeList(){
        return typeService.queryTypeList();
    }

}

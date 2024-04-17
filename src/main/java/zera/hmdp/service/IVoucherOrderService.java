package zera.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import zera.hmdp.dto.Result;
import zera.hmdp.entity.VoucherOrder;

public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);
}

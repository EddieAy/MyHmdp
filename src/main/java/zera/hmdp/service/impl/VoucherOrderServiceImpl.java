package zera.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zera.hmdp.dto.Result;
import zera.hmdp.entity.SeckillVoucher;
import zera.hmdp.entity.VoucherOrder;
import zera.hmdp.mapper.VoucherMapper;
import zera.hmdp.mapper.VoucherOrderMapper;
import zera.hmdp.service.ISeckillVoucherService;
import zera.hmdp.service.IVoucherOrderService;
import zera.hmdp.utils.RedisIdWorker;
import zera.hmdp.utils.UserHolder;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }

        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }

        boolean success = seckillVoucherService.update().
                setSql("stock = stock - 1").
                eq("voucher_id", voucherId).update();

        if(!success){
            return Result.fail("库存不足");
        }

        VoucherOrder voucherOrder = new VoucherOrder();

        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);
        save(voucherOrder);
        return Result.ok(orderId);
    }
}

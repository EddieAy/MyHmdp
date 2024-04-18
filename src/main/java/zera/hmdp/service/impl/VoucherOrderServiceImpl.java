package zera.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
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
import zera.hmdp.utils.SimpleRedisLock;
import zera.hmdp.utils.UserHolder;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        //2.1秒杀尚未开始返回异常
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀尚未开始");
        }
        //2.2秒杀已结束返回异常
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已经结束");
        }
        voucher = seckillVoucherService.getById(voucherId);
        //3.判断库存是否充足
        if(voucher.getStock()<1){
            //3.1库存不足返回异常
            return Result.fail("库存不足！");
        }
        Long userId = UserHolder.getUser().getId();
/*        synchronized (userId.toString().intern()){
            //获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }*/

        SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        boolean isLock = simpleRedisLock.tryLock(1200);
        if (!isLock) {
            return Result.fail("一人下一单");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }finally {
            simpleRedisLock.unlock();
        }

    }
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //6.一人一单
        Long userId = UserHolder.getUser().getId();
        //6.1查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //6.2判断是否存在
        if(count>0){
            //用户已经购买过了
            return Result.fail("用户已经购买过一次！");
        }
        //3.2库存充足扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") //相当于set条件 set stock = stock - 1
                .eq("voucher_id", voucherId) //相当于where条件 where id = ? and stock = ?
                .gt("stock",0).update();
        if(!success){
            return Result.fail("库存不足！");
        }
        //4.创建订单，返回订单id
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");//订单id
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);//代金券id
        save(voucherOrder);
        return Result.ok(orderId);
    }
}

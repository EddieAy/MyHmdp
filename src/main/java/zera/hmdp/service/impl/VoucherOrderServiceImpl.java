package zera.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zera.hmdp.dto.Result;
import zera.hmdp.entity.VoucherOrder;
import zera.hmdp.mapper.VoucherOrderMapper;
import zera.hmdp.service.ISeckillVoucherService;
import zera.hmdp.service.IVoucherOrderService;
import zera.hmdp.utils.RedisIdWorker;
import zera.hmdp.utils.UserHolder;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    private static final ExecutorService seckillOrderExecutor = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init(){
        seckillOrderExecutor.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable{

        String queueName = "stream.orders";
        @Override
        public void run() {
            while (true){
                try {
                    List<MapRecord<String, Object, Object>> msgList = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );

                    if(msgList == null || msgList.isEmpty()){
                        continue;
                    }
                    MapRecord<String, Object, Object> record = msgList.get(0);
                    Map<Object, Object> value = record.getValue();

                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);

                    handleVoucherOrder(voucherOrder);

                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常",e);
                    handlePendingList();
                }

            }
        }

        private void handlePendingList() {
            while (true){
                try {
                    List<MapRecord<String, Object, Object>> msgList = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );

                    if(msgList == null || msgList.isEmpty()){
                        break;
                    }
                    MapRecord<String, Object, Object> record = msgList.get(0);
                    Map<Object, Object> value = record.getValue();

                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);

                    handleVoucherOrder(voucherOrder);

                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    log.error("处理pending-list异常",e);
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }

            }
        }
    }

/*    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while (true){
                try {
                    VoucherOrder voucherOrder = orderTasks.take();
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常",e);
                }

            }
        }
    }*/

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("不允许重复下单");
            return;
        }
        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            proxy.createVoucherOrder(voucherOrder);
        }finally {
//            simpleRedisLock.unlock();
            lock.unlock();
        }

    }

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private IVoucherOrderService proxy;

    @Override
    public Result seckillVoucher(Long voucherId) {
        long orderId = redisIdWorker.nextId("order");

        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT, Collections.emptyList(),
                voucherId.toString(), userId.toString(),String.valueOf(orderId));




        int res = result.intValue();
        System.out.println(res);
        if(res != 0){
            return Result.fail(res == 1 ? "库存不足" : "不能重复下单");
        }

        proxy = (IVoucherOrderService) AopContext.currentProxy();

        return Result.ok(orderId);
    }

    /*@Override
    public Result seckillVoucher(Long voucherId) {
        System.out.println("你在哪1");
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());




        int res = result.intValue();
        System.out.println(res);
        if(res != 0){
            return Result.fail(res == 1 ? "库存不足" : "不能重复下单");
        }
        VoucherOrder voucherOrder = new VoucherOrder();

        //TODO
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);//代金券id

        orderTasks.add(voucherOrder);

        proxy = (IVoucherOrderService) AopContext.currentProxy();

        return Result.ok(orderId);
    }*/


    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //6.一人一单
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        //6.1查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder).count();
        //6.2判断是否存在
        if(count>0){
            //用户已经购买过了
            log.error("用户已经购买过一次！");
//            return Result.fail("用户已经购买过一次！");
        }
        //3.2库存充足扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") //相当于set条件 set stock = stock - 1
                .eq("voucher_id", voucherId) //相当于where条件 where id = ? and stock = ?
                .gt("stock",0).update();
        if(!success){
            log.error("库存不足！");
//            return Result.fail("库存不足！");
        }
        //4.创建订单，返回订单id
        save(voucherOrder);

    }
}

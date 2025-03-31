package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    StringRedisTemplate stringRedisTemplate;
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private RedisIdWorker redisIdWorker;

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    private ExecutorService SECKILL_ORDER_EXCUTOR = Executors.newFixedThreadPool(1);
    @Resource
    private VoucherOrderMapper voucherOrderMapper;

    @Resource
    @Lazy
    IVoucherOrderService voucherOrderService;

    @Resource
    ISeckillVoucherService seckillVoucherService;


    private class SeckillOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    VoucherOrder task = orderTasks.take();
                    log.debug("start create order task");
                    voucherOrderService.createVoucherOrder(task);
                } catch (InterruptedException e) {
                    log.error("error when process task : {}", e.toString());
                }
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        LambdaQueryWrapper<VoucherOrder> voucherOrderLambdaQueryWrapper = new LambdaQueryWrapper<>();
        voucherOrderLambdaQueryWrapper.eq(VoucherOrder::getUserId, userId).eq(VoucherOrder::getVoucherId, voucherId);
        Long count = voucherOrderMapper.selectCount(voucherOrderLambdaQueryWrapper);
        if (count > 0) {
            throw new RuntimeException("duplicate purchase when create order.");
        }
        boolean isSuccess = seckillVoucherService.update(new LambdaUpdateWrapper<SeckillVoucher>().eq(SeckillVoucher::getVoucherId, voucherOrder.getVoucherId()).gt(SeckillVoucher::getStock, 0).setSql("stock=stock-1"));
        if (!isSuccess) {
            throw new RuntimeException("decrease stock failed");
        }
        save(voucherOrder);
    }

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXCUTOR.submit(new SeckillOrderHandler());
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        // lua 脚本判断购买资格和库存
        int result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), UserHolder.getUser().getId().toString()).intValue();
        log.debug("lua script result in seckill : {}", result);
        if (result == 1) {
            return Result.fail("sold out");
        }
        if (result == 2) {
            return Result.fail("duplicate purchase");
        }
        if (result == 0) {
            Long orderId = redisIdWorker.nextId("order");
            VoucherOrder voucherOrder = new VoucherOrder();
            voucherOrder.setId(orderId);
            voucherOrder.setVoucherId(voucherId);
            voucherOrder.setUserId(UserHolder.getUser().getId());
            orderTasks.add(voucherOrder);
            return Result.ok("orderId:" + orderId);
        }
        return Result.fail("unknown error");
    }
}

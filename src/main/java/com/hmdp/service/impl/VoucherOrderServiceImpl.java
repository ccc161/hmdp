package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillOrderLocalMessage;
import com.hmdp.entity.User;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillOrderLocalMessageService;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.Resource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import static com.hmdp.config.RabbitMQConfig.*;
import static com.hmdp.utils.MessageConstants.*;
import static com.hmdp.utils.OrderConstants.TYPE_SECKILL;

@Service
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Resource
    @Qualifier("seckillRabbitTemplate")
    private RabbitTemplate seckillRabbitTemplate;
    @Resource(name = "threadPoolExecutor")
    private ExecutorService executorService;
    private static final DefaultRedisScript<Long> REDUCE_STOCK_SCRIPT;
    private static final DefaultRedisScript<Long> ROLLBACK_STOCK_SCRIPT;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private VoucherOrderMapper voucherOrderMapper;
    @Resource
    ISeckillOrderLocalMessageService seckillOrderLocalMessageService;

    static {
        REDUCE_STOCK_SCRIPT = new DefaultRedisScript<>();
        REDUCE_STOCK_SCRIPT.setLocation(new ClassPathResource("lua/seckill/reduceStock.lua"));
        REDUCE_STOCK_SCRIPT.setResultType(Long.class);
        ROLLBACK_STOCK_SCRIPT = new DefaultRedisScript<>();
        ROLLBACK_STOCK_SCRIPT.setLocation(new ClassPathResource("lua/seckill/rollbackStock.lua"));
        ROLLBACK_STOCK_SCRIPT.setResultType(Long.class);
    }

    private Optional<VoucherOrder> createNewVoucherOrderTransaction(Long voucherId) {
        try {
            long orderId = redisIdWorker.nextId("order");
            VoucherOrder voucherOrder = new VoucherOrder(orderId, voucherId, UserHolder.getUser().getId());
            SeckillOrderLocalMessage message = seckillOrderLocalMessageService.createMessage(voucherOrder);
            Long userId = voucherOrder.getUserId();
            LambdaQueryWrapper<VoucherOrder> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(VoucherOrder::getUserId, userId).eq(VoucherOrder::getVoucherId, voucherId);
            return Optional.ofNullable(transactionTemplate.execute(status -> {
                try {
                    Long count = voucherOrderMapper.selectCount(queryWrapper);
                    if (count > 0) {
                        // 查询失败，只是查询，没有修改，不需要回滚
                        log.error("Duplicate purchase detected for user: {}, voucher: {}", userId, voucherId);
                        return null;
                    }
                    if (!save(voucherOrder)) {
                        // 保存失败，回滚
                        log.error("Failed to save voucher order: {}", voucherOrder);
                        status.setRollbackOnly();
                        return null;
                    }
                    // 成功保存订单，还要保存本地消息表
                    if (!seckillOrderLocalMessageService.save(message)) {
                        // 保存本地消息失败，回滚
                        log.error("Failed to save local message for order: {}", voucherOrder);
                        status.setRollbackOnly();
                        return null;
                    }
                    log.info("Order created successfully: {}", voucherOrder);
                    return voucherOrder;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    log.error("Transaction failed for order creation: ", e);
                    return null;
                }
            }));
        } catch (Exception e) {
            log.error("Error creating order for voucher: {}", voucherId, e);
            return Optional.empty();
        }
    }


    private boolean checkPurchaseEligibility(Long voucherId) {
        if (voucherId == null) {
            log.error("voucher id must not be null");
            return false;
        }
        if (UserHolder.getUser() == null) {
            log.error("user must not bu null");
            return false;
        }
        int result = stringRedisTemplate.execute(
                REDUCE_STOCK_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), UserHolder.getUser().getId().toString()).intValue();
        log.info("REDUCE_STOCK_SCRIPT, userId : {}, voucherId : {}, result : {}", UserHolder.getUser().getId(), voucherId, result);
        return result == 0;
    }


    @Override
    public Result seckillVoucher(Long voucherId) {
        // 检查购买资格
        boolean isEligible = checkPurchaseEligibility(voucherId);
        if (!isEligible) {
            return Result.fail("purchase failed, you are not eligible.");
        }
        // 创建保存订单和本地消息
        Optional<VoucherOrder> orderOptional = createNewVoucherOrderTransaction(voucherId);
        if (orderOptional.isEmpty()) {
            return onCreateOrderFailed(voucherId, UserHolder.getUser().getId());
        }
        // 异步发送消息
        asynchronousSendOrderMessage(orderOptional.get());
        return Result.ok(orderOptional.get().getId());
    }

    private void asynchronousSendOrderMessage(VoucherOrder voucherOrder) {
        executorService.submit(() -> {
            SeckillOrderLocalMessage message = seckillOrderLocalMessageService.createMessage(voucherOrder);
            seckillRabbitTemplate.convertAndSend(SECKILL_EXCHANGE, SECKILL_ROUTING_KEY, message, new CorrelationData(message.getMessageId().toString()));
        });
    }

    private Result onCreateOrderFailed(Long voucherId, Long userId) {
        long result = stringRedisTemplate.execute(ROLLBACK_STOCK_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
        log.info("ROLLBACK_STOCK_SCRIPT, userId : {}, voucherId : {}, result : {}", userId, voucherId, result);
        if (result == 0) {
            return Result.fail("purchase failed, please try again later");
        } else {
            // 如果回滚失败了不重试，直接告诉用户没购买资格
            return Result.fail("purchase failed, you are not eligible.");
        }
    }
}

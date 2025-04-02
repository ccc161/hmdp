package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hmdp.dto.SeckillOrderLocalMessage;
import com.hmdp.dto.SeckillOrderMessageConsumption;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillOrderMessageConsumptionService;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IStockService;
import com.rabbitmq.client.Channel;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.Optional;

import static com.hmdp.config.SeckillMessageQueueConfig.SECKILL_QUEUE;

@Service
@Slf4j
public class StockServiceImpl implements IStockService {
    @Resource
    ISeckillVoucherService seckillVoucherService;
    @Resource
    TransactionTemplate transactionTemplate;
    @Resource
    ISeckillOrderMessageConsumptionService consumptionService;

    private Optional<VoucherOrder> contentToOrder(String content) {
        if (content == null || content.isEmpty()) {
            log.warn("消息内容为空或无效: {}", content);
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(JSONUtil.toBean(content, VoucherOrder.class));
        } catch (Exception e) {
            log.error("订单内容转换失败: {}", content, e);
            return Optional.empty();
        }
    }

    @RabbitListener(queues = SECKILL_QUEUE, containerFactory = "seckillRabbitListenerContainerFactory")
    @Override
    public void handleSeckillMessage(SeckillOrderLocalMessage message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) {
        try {
            Optional<VoucherOrder> orderOptional = contentToOrder(message.getContent());
            if (orderOptional.isEmpty()) {
                handleFailure(message, channel, tag, "无效的订单内容");
                return;
            }

            VoucherOrder voucherOrder = orderOptional.get();
            boolean updated = updateStock(voucherOrder);
            if (updated) {
                channel.basicAck(tag, false);
                log.info("库存扣减成功，订单ID: {}", voucherOrder.getId());
            } else {
                handleFailure(message, channel, tag, "库存不足，订单ID: " + voucherOrder.getId());
            }
        } catch (Exception e) {
            handleFailure(message, channel, tag, "消息处理异常: " + e.getMessage());
        }
    }

    private boolean updateStock(VoucherOrder voucherOrder) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            try {
                boolean stockUpdated = seckillVoucherService.update(
                        new LambdaUpdateWrapper<SeckillVoucher>()
                                .eq(SeckillVoucher::getVoucherId, voucherOrder.getVoucherId())
                                .gt(SeckillVoucher::getStock, 0)
                                .setSql("stock = stock - 1")
                );

                if (!stockUpdated) {
                    log.error("库存不足，无法扣减，订单ID: {}", voucherOrder.getId());
                    status.setRollbackOnly();
                    return false;
                }

                if (!saveConsumptionMessage(voucherOrder)) {
                    log.error("本地消息表保存失败，订单ID: {}", voucherOrder.getId());
                    status.setRollbackOnly();
                    return false;
                }

                return true;
            } catch (Exception e) {
                status.setRollbackOnly();
                log.error("事务执行失败，订单ID: {}", voucherOrder.getId(), e);
                return false;
            }
        }));
    }

    private boolean saveConsumptionMessage(VoucherOrder voucherOrder) {
        SeckillOrderMessageConsumption messageConsumption = SeckillOrderMessageConsumption.builder()
                .messageId(voucherOrder.getId().toString())
                .orderId(voucherOrder.getId().toString())
                .productId(voucherOrder.getVoucherId().toString())
                .quantity(1)
                .processStatus(0)
                .build();
        return consumptionService.save(messageConsumption);
    }

    private void handleFailure(SeckillOrderLocalMessage message, Channel channel, long tag, String reason) {
        try {
            channel.basicNack(tag, false, false);
            log.warn("消息被拒绝并进入死信队列，原因: {} | 消息内容: {}", reason, message);
        } catch (IOException e) {
            log.error("消息拒绝失败，标签: {} | 消息内容: {}", tag, message, e);
        }
    }
}
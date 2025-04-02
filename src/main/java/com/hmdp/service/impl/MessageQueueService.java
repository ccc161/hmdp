package com.hmdp.service.impl;

import com.hmdp.dto.SeckillOrderLocalMessage;
import com.hmdp.service.IMessageQueueService;
import com.hmdp.service.ISeckillOrderLocalMessageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;

import static com.hmdp.config.SeckillMessageQueueConfig.SECKILL_EXCHANGE;
import static com.hmdp.config.SeckillMessageQueueConfig.SECKILL_ROUTING_KEY;

@Service
@Slf4j
public class MessageQueueService implements IMessageQueueService {
    @Resource
    ISeckillOrderLocalMessageService seckillOrderLocalMessageService;

    @Resource(name = "threadPoolExecutor")
    private ExecutorService executorService;

    @Resource
    @Qualifier("seckillRabbitTemplate")
    private RabbitTemplate seckillRabbitTemplate;

    @Override
    public void asynSendSeckillMessage(SeckillOrderLocalMessage message) {
        executorService.submit(() -> {
            CorrelationData correlationData = seckillOrderLocalMessageService.createNewCorrelationData(message);
            log.debug("message queue send message : {}, correlationData : {}", message, correlationData);
            seckillRabbitTemplate.convertAndSend(
                    SECKILL_EXCHANGE,
                    SECKILL_ROUTING_KEY,
                    message,
                    msg -> {
                        // 设置消息持久化
                        msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return msg;
                    },
                    correlationData
            );
        });
    }
}

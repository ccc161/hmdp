package com.hmdp.service;


import com.hmdp.dto.SeckillOrderLocalMessage;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;

import static com.hmdp.config.SeckillMessageQueueConfig.SECKILL_QUEUE;

public interface IStockService {
    @RabbitListener(queues = SECKILL_QUEUE,
            containerFactory = "seckillRabbitListenerContainerFactory")
    void handleSeckillMessage(SeckillOrderLocalMessage message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag);

}

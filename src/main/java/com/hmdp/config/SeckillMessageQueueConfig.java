package com.hmdp.config;

import com.hmdp.service.ISeckillOrderLocalMessageService;
import com.hmdp.utils.Combine;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class SeckillMessageQueueConfig {
    // 秒杀队列配置
    public static final String SECKILL_QUEUE = "seckill.queue";
    public static final String SECKILL_EXCHANGE = "seckill.exchange";
    public static final String SECKILL_ROUTING_KEY = "seckill.order";

    // 死信队列相关常量
    public static final String DLX_EXCHANGE = "dlx.seckill.exchange";
    public static final String DLX_QUEUE = "dlx.seckill.queue";
    public static final String DLX_ROUTING_KEY = "dlx.seckill";

    @Resource
    ISeckillOrderLocalMessageService seckillOrderLocalMessageService;

    // 定义秒杀队列的 RabbitTemplate
    @Bean
    @Qualifier("seckillRabbitTemplate")
    public RabbitTemplate seckillRabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        // 设置默认交换机和路由键
        template.setExchange(SECKILL_EXCHANGE);
        template.setRoutingKey(SECKILL_ROUTING_KEY);
        template.setMandatory(true);

        // 设置确认回调（发送到交换机成功或失败）
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) log.debug("秒杀消息成功投递到交换机，correlationData: {}", correlationData);
            else log.error("秒杀消息投递到交换机失败，correlationData: {}, cause: {}", correlationData, cause);
            onSeckillConfirm(ack, correlationData);
        });
        // 设置返回回调（消息从交换机路由到队列失败时触发）
        template.setReturnsCallback(returned -> {
            log.error("秒杀消息未能路由到队列，exchange: {}, routingKey: {}, replyCode: {}, replyText: {}, message: {}",
                    returned.getExchange(),
                    returned.getRoutingKey(),
                    returned.getReplyCode(),
                    returned.getReplyText(),
                    new String(returned.getMessage().getBody()));
            try {
                Message message = returned.getMessage();
                onSeckillReturns(message);
            } catch (Exception e) {
                log.error("获取correlationId出错，error : ", e);
            }
        });
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    private void onSeckillReturns(Message message) {
        log.error("send to queue failed, message : {}", message);
        Combine<Long, Long> longLongCombine = parseCorrelationData(message.getMessageProperties().getCorrelationId());
        seckillOrderLocalMessageService.handleReturnedMessageWithTransaction(longLongCombine.first(), longLongCombine.second());
    }

    private Combine<Long, Long> parseCorrelationData(String correlationDataId) {
        String[] split = correlationDataId.split(":");
        Long messageId = Long.parseLong(split[0]), version = Long.parseLong(split[1]);
        return new Combine<>(messageId, version);
    }

    private Combine<Long, Long> parseCorrelationData(CorrelationData correlationData) {
        return parseCorrelationData(correlationData.getId());
    }

    private void onSeckillConfirm(boolean ack, CorrelationData correlationData) {
        Combine<Long, Long> messageIdAndVersion = parseCorrelationData(correlationData);
        if (ack) {
            seckillOrderLocalMessageService.handleSuccessMessageWithTransaction(messageIdAndVersion.first(), messageIdAndVersion.second());
        } else {
            seckillOrderLocalMessageService.handleFailedMessageWithTransaction(messageIdAndVersion.first(), messageIdAndVersion.second());
        }
    }

    // 秒杀队列、交换机和绑定
    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(SECKILL_EXCHANGE, true, false);
    }

    @Bean
    public Binding seckillBinding() {
        return BindingBuilder.bind(seckillQueue())
                .to(seckillExchange())
                .with(SECKILL_ROUTING_KEY);
    }


    // 原始秒杀队列配置（增加死信参数）
    @Bean
    public Queue seckillQueue() {
        return QueueBuilder.durable(SECKILL_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE) // 死信交换器
                .withArgument("x-dead-letter-routing-key", DLX_ROUTING_KEY) // 死信路由键
                .build();
    }

    // 新增死信队列配置
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue dlxQueue() {
        return QueueBuilder.durable(DLX_QUEUE).build();
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue())
                .to(dlxExchange())
                .with(DLX_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory seckillRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setConcurrentConsumers(1); // 并发消费者数
        factory.setMaxConcurrentConsumers(10);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL); // 手动确认
        return factory;
    }
}
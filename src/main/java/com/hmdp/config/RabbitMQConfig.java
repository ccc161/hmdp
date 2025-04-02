package com.hmdp.config;

import com.hmdp.service.ISeckillOrderLocalMessageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionTemplate;


@Configuration
@Slf4j
public class RabbitMQConfig {

    private static final int MAX_RETRY_TIMES = 5;

    private final TransactionTemplate transactionTemplate;

    // 秒杀队列配置
    public static final String SECKILL_QUEUE = "seckill.queue";
    public static final String SECKILL_EXCHANGE = "seckill.exchange";
    public static final String SECKILL_ROUTING_KEY = "seckill.order";

    // operations 队列配置
    public static final String OPERATIONS_QUEUE = "operations.queue";
    public static final String OPERATIONS_EXCHANGE = "operations.exchange";
    public static final String OPERATIONS_ROUTING_KEY = "operations.action";

    @Resource
    ISeckillOrderLocalMessageService seckillOrderLocalMessageService;

    public RabbitMQConfig(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }


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
            if (ack) {
                log.info("秒杀消息成功投递到交换机，correlationData: {}", correlationData);
                if (correlationData != null) {
                    onSeckillSendSuccess(correlationData.getId());
                }
            } else {
                log.error("秒杀消息投递到交换机失败，correlationData: {}, cause: {}", correlationData, cause);
                if (correlationData != null) {
                    onSeckillSendFailed(correlationData.getId());
                }
            }
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
                String correlationId = returned.getMessage().getMessageProperties().getCorrelationId();
                onSeckillSendFailed(correlationId);
            } catch (Exception e) {
                log.error("获取correlationId出错，error : ", e);
            }

        });
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    private void onSeckillSendSuccess(String correlationId) {
        // todo
    }

    private void onSeckillSendFailed(String correlationId) {
        // todo
    }

    // 定义 operations 队列的 RabbitTemplate
    @Bean
    @Qualifier("operationsRabbitTemplate")
    public RabbitTemplate operationsRabbitTemplate(ConnectionFactory connectionFactory) {
        // todo operations应该是streams形式，追加写日志的方式
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        // 设置默认交换机和路由键
        template.setExchange(OPERATIONS_EXCHANGE);
        template.setRoutingKey(OPERATIONS_ROUTING_KEY);
        template.setMandatory(true);
        // 配置确认回调
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.info("operations消息成功投递到交换机，correlationData: {}", correlationData);
                // 此处可以更新本地消息状态为成功
            } else {
                log.error("operations消息投递到交换机失败，correlationData: {}, cause: {}", correlationData, cause);
                // 此处可以进行重试或者记录失败信息
            }
        });
        // 配置返回回调
        template.setReturnsCallback(returned -> {
            log.error("operations消息未能路由到队列，exchange: {}, routingKey: {}, replyCode: {}, replyText: {}, message: {}",
                    returned.getExchange(),
                    returned.getRoutingKey(),
                    returned.getReplyCode(),
                    returned.getReplyText(),
                    new String(returned.getMessage().getBody()));
        });
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    // 秒杀队列、交换机和绑定
    @Bean
    public DirectExchange seckillExchange() {
        return new DirectExchange(SECKILL_EXCHANGE);
    }

    @Bean
    public Queue seckillQueue() {
        return QueueBuilder.durable(SECKILL_QUEUE).build();
    }

    @Bean
    public Binding seckillBinding() {
        return BindingBuilder.bind(seckillQueue())
                .to(seckillExchange())
                .with(SECKILL_ROUTING_KEY);
    }

    // operations 队列、交换机和绑定
    @Bean
    public DirectExchange operationsExchange() {
        return new DirectExchange(OPERATIONS_EXCHANGE);
    }

    @Bean
    public Queue operationsQueue() {
        return QueueBuilder.durable(OPERATIONS_QUEUE).build();
    }

    @Bean
    public Binding operationsBinding() {
        return BindingBuilder.bind(operationsQueue())
                .to(operationsExchange())
                .with(OPERATIONS_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.SeckillOrderLocalMessage;
import com.hmdp.entity.VoucherOrder;
import org.springframework.amqp.rabbit.connection.CorrelationData;

public interface ISeckillOrderLocalMessageService extends IService<SeckillOrderLocalMessage> {
    SeckillOrderLocalMessage createNewMessage(VoucherOrder voucherOrder);

    CorrelationData createNewCorrelationData(SeckillOrderLocalMessage message);

    void handleFailedMessageWithTransaction(Long messageId, Long version);

    void handleSuccessMessageWithTransaction(Long messageId, Long version);

    void handleReturnedMessageWithTransaction(Long messageId, Long version);
}
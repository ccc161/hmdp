package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.SeckillOrderLocalMessage;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillOrderLocalMessageMapper;
import com.hmdp.service.ISeckillOrderLocalMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

import static com.hmdp.utils.MessageConstants.*;

@Slf4j
@Service
public class SeckillOrderLocalMessageServiceImpl
        extends ServiceImpl<SeckillOrderLocalMessageMapper, SeckillOrderLocalMessage>
        implements ISeckillOrderLocalMessageService {
    @Override
    public SeckillOrderLocalMessage createNewMessage(VoucherOrder voucherOrder) {
        SeckillOrderLocalMessage message = new SeckillOrderLocalMessage();
        message.setMessageId(voucherOrder.getId());
        message.setBusinessType(TYPE_SECKILL_ORDER);
        message.setStatus(STATUS_PENDING);
        message.setContent(JSONUtil.toJsonStr(voucherOrder));
        message.setNextSendTime(new Date(System.currentTimeMillis() + DEFAULT_RETRY_INTERVAL));
        message.setRetryCount(0);
        message.setVersion(0);
        return message;
    }

    @Override
    public CorrelationData createNewCorrelationData(SeckillOrderLocalMessage message) {
        CorrelationData correlationData = new CorrelationData();
        correlationData.setId(message.getMessageId() + ":" + message.getVersion());
        return correlationData;
    }

    @Override
    @Transactional
    public void handleFailedMessageWithTransaction(Long messageId, Long version) {
        // 检查并获取消息
        Optional<SeckillOrderLocalMessage> messageOptional = checkAndGetMessage(messageId, version);
        if (messageOptional.isEmpty()) {
            log.warn("Message not found or invalid for messageId: {}, version: {}", messageId, version);
            return;
        }

        SeckillOrderLocalMessage message = messageOptional.get();

        // 更新重试次数和下次发送时间
        int newRetryCount = message.getRetryCount() + 1;
        Date nextSendTime = calculateNextSendTime(message.getUpdateTime(), newRetryCount);
        message.setRetryCount(newRetryCount);
        message.setNextSendTime(nextSendTime);

        // 如果达到最大重试次数，将状态置为失败
        if (newRetryCount >= DEFAULT_MAX_RETRY_TIMES) {
            message.setStatus(STATUS_SEND_FAILED);
        }

        // 更新数据库记录
        updateById(message);
    }

    @Override
    @Transactional
    public void handleSuccessMessageWithTransaction(Long messageId, Long version) {
        // 检查并获取消息
        Optional<SeckillOrderLocalMessage> messageOptional = checkAndGetMessage(messageId, version);
        if (messageOptional.isEmpty()) {
            log.warn("Message not found or invalid for messageId: {}, version: {}", messageId, version);
            return;
        }
        SeckillOrderLocalMessage message = messageOptional.get();
        message.setStatus(STATUS_SEND_SUCCESSFUL);
        updateById(message);
    }

    @Override
    @Transactional
    public void handleReturnedMessageWithTransaction(Long messageId, Long version) {
        SeckillOrderLocalMessage message = getById(messageId);
        if (message == null) return;
        if (message.getStatus() != STATUS_SEND_SUCCESSFUL) return;
        message.setStatus(STATUS_NEED_MANUAL_INTERVENTION);
        updateById(message);
    }

    private Optional<SeckillOrderLocalMessage> checkAndGetMessage(Long messageId, Long version) {
        // 参数校验
        if (messageId == null || version == null) {
            log.warn("Invalid parameters: messageId and version cannot be null");
            return Optional.empty();
        }

        // 查询消息
        SeckillOrderLocalMessage message = getById(messageId);
        if (message == null || message.getVersion().longValue() != version || message.getStatus() != STATUS_PENDING) {
            log.warn("Message not found or invalid state for messageId: {}, version: {}", messageId, version);
            return Optional.empty();
        }

        return Optional.of(message);
    }

    private Date calculateNextSendTime(Date updateTime, int retryCount) {
        long baseInterval = (long) (DEFAULT_RETRY_INTERVAL * Math.pow(10, retryCount));
        return new Date(updateTime.getTime() + baseInterval);
    }
}


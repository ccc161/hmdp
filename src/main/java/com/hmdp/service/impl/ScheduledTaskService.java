package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.SeckillOrderLocalMessage;
import com.hmdp.service.IMessageQueueService;
import com.hmdp.service.IScheduledTaskService;
import com.hmdp.service.ISeckillOrderLocalMessageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static com.hmdp.utils.MessageConstants.*;

@Slf4j
@Service
public class ScheduledTaskService implements IScheduledTaskService {
    @Resource
    IMessageQueueService messageQueueService;
    @Resource(name = "threadPoolExecutor")
    private ExecutorService executorService;
    @Resource
    private ISeckillOrderLocalMessageService seckillOrderLocalMessageService;

    @Scheduled(cron = "0 * * * * *")
    @Override
    public void scanAndResendSeckillOrderMessage() {
        Date now = new Date(System.currentTimeMillis());
        LambdaQueryWrapper<SeckillOrderLocalMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SeckillOrderLocalMessage::getStatus, STATUS_PENDING)
                .le(SeckillOrderLocalMessage::getNextSendTime, now);
        List<SeckillOrderLocalMessage> messages = seckillOrderLocalMessageService.list(queryWrapper);
        if (messages.isEmpty()) return;
        for (SeckillOrderLocalMessage message : messages) {
            messageQueueService.asynSendSeckillMessage(message);
        }
    }

    @Scheduled(cron = "0 */5 * * * *")
    @Override
    public void archiveFailedSeckillOrderMessage() {
        Date interventionTime = new Date(System.currentTimeMillis() - DEFAULT_INTERVENTION_MINUTES * 60 * 1000);
        LambdaQueryWrapper<SeckillOrderLocalMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper
                // 条件1：状态非人工干预
                .ne(SeckillOrderLocalMessage::getStatus, STATUS_NEED_MANUAL_INTERVENTION)
                // 嵌套条件：AND (条件2 OR 条件3)
                .and(wrapper -> wrapper
                        // 条件2：状态为失败
                        .eq(SeckillOrderLocalMessage::getStatus, STATUS_SEND_FAILED)
                        // OR 连接条件3
                        .or()
                        // 条件3：status = 'PENDING' AND create_time < 3小时前
                        .and(w -> w
                                .eq(SeckillOrderLocalMessage::getStatus, STATUS_PENDING)
                                .lt(SeckillOrderLocalMessage::getCreateTime, interventionTime)
                        )
                );
        List<SeckillOrderLocalMessage> failedMessages = seckillOrderLocalMessageService.list(queryWrapper);
        for (SeckillOrderLocalMessage failedMessage : failedMessages) {
            failedMessage.setStatus(STATUS_NEED_MANUAL_INTERVENTION);
            failedMessage.setRetryCount(0);
        }
        executorService.submit(() -> {
                    try {
                        seckillOrderLocalMessageService.updateBatchById(failedMessages);
                    } catch (Exception e) {
                        log.error("error when update failed messages : {}", failedMessages, e);
                    }
                }
        );
    }
}

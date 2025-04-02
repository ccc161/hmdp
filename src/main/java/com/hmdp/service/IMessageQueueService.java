package com.hmdp.service;

import com.hmdp.dto.SeckillOrderLocalMessage;

public interface IMessageQueueService {
    void asynSendSeckillMessage(SeckillOrderLocalMessage message);
}

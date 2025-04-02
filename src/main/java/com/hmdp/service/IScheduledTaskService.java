package com.hmdp.service;

import org.springframework.scheduling.annotation.Scheduled;

public interface IScheduledTaskService {
    @Scheduled(cron = "0 * * * * *")
    void scanAndResendSeckillOrderMessage();

    @Scheduled(cron = "0 */5 * * * *")
    void archiveFailedSeckillOrderMessage();
}

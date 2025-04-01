package com.hmdp.utils;

import com.hmdp.HmDianPingApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.annotation.Resource;


@SpringBootTest(classes = HmDianPingApplication.class)
class RedisIdWorkerTest {
    @Resource
    RedisIdWorker redisIdWorker;

    @Test
    void nextId() {
        for (int i = 0; i < 0; i++) {
            System.out.println(redisIdWorker.nextId("test"));
        }
    }
}
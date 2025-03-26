package com.hmdp.utils;

import com.hmdp.HmDianPingApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = HmDianPingApplication.class)
class RedisIdWorkerTest {
    @Resource
    RedisIdWorker redisIdWorker;

    @Test
    void nextId() {
        for (int i = 0; i < 100; i++) {
            System.out.println(redisIdWorker.nextId("test"));
        }
    }
}
package com.hmdp.utils;

import com.hmdp.HmDianPingApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = HmDianPingApplication.class)
class CacheClientTest {
    @Resource
    CacheClient cacheClient;

    @Test
    void testToString() {
        System.out.println(cacheClient.toString());
    }
}
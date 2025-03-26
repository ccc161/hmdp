package com.hmdp.utils;

import lombok.val;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    private static final Long BEGIN_TIMESTAMP = 1640995200L;

    private static final int SERIAL_BITS = 32;

    @Resource
    StringRedisTemplate stringRedisTemplate;

    public Long nextId(String prefix) {
        LocalDateTime now = LocalDateTime.now();
        long deltaTime = now.toEpochSecond(ZoneOffset.UTC) - BEGIN_TIMESTAMP;
        String key = "ID:" + prefix + ":" + now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd:HH"));
        long increment = stringRedisTemplate.opsForValue().increment(key);
        return (deltaTime << SERIAL_BITS) | increment;
    }
}

package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(8);
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
    private static final DefaultRedisScript<Long> DELETE_REBUILD_LOCK_SCRIPT;
    private static final DefaultRedisScript<Long> REBUILD_SCRIPT;

    static {
        DELETE_REBUILD_LOCK_SCRIPT = new DefaultRedisScript<>();
        DELETE_REBUILD_LOCK_SCRIPT.setLocation(new ClassPathResource("/lua/cache/delete_rebuild_lock.lua"));
        REBUILD_SCRIPT = new DefaultRedisScript<>();
        REBUILD_SCRIPT.setLocation(new ClassPathResource("/lua/cache/rebuild.lua"));
    }

    private String getRebuildKey(Object o, LocalDateTime localDateTime) {
        String input = o.toString() + localDateTime;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder(hashBytes.length * 2);
            hexString.append("rebuild:" + o.getClass() + ":");
            for (byte b : hashBytes) {
                int value = b & 0xFF;
                hexString.append(HEX_CHARS[value >>> 4]);
                hexString.append(HEX_CHARS[value & 0x0F]);
            }
            log.debug("generate rebuild task key done : {}", hexString);
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public CacheClient(StringRedisTemplate stringRedisTemplate, RedissonClient redissonClient) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redissonClient = redissonClient;
    }

    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    public void setWithLogicalExpireTime(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plus(time, TimeUnitConverter.toTemporalUnit(timeUnit)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <ReturnType, KeyType> ReturnType queryByKeyWithMutex(String keyPrefix, KeyType key, Class<ReturnType> returnTypeClass,
                                                                Function<KeyType, ReturnType> function, Long time, TimeUnit timeUnit) {
        String redisGetKey = keyPrefix + key;
        String redisValue = stringRedisTemplate.opsForValue().get(redisGetKey);
        if (redisValue != null)
            return redisValue.equals(RedisConstants.NULL_VALUE) ? null : JSONUtil.toBean(redisValue, returnTypeClass);
        String redisLockKey = keyPrefix + "lock:" + key;
        RLock lock = redissonClient.getLock(redisLockKey);
        ReturnType returnObject;
        try {
            lock.lock();
            redisValue = stringRedisTemplate.opsForValue().get(redisGetKey);
            if (redisValue != null)
                return redisValue.equals(RedisConstants.NULL_VALUE) ? null : JSONUtil.toBean(redisValue, returnTypeClass);
            returnObject = function.apply(key);
            if (returnObject == null) {
                // 缓存空值防止缓存穿透
                stringRedisTemplate.opsForValue().set(redisGetKey, RedisConstants.NULL_VALUE, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            } else {
                stringRedisTemplate.opsForValue().set(redisGetKey, JSONUtil.toJsonStr(returnObject), time, timeUnit);
            }
            return returnObject;
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }


    }

    public <ReturnType, KeyType> ReturnType queryByIdWithLogicalExpireTime(String keyPrefix, KeyType key, Class<ReturnType> returnTypeClass,
                                                                           Function<KeyType, ReturnType> function, Long time, TimeUnit timeUnit) {
        String redisKey = keyPrefix + key;
        String valueJSON = stringRedisTemplate.opsForValue().get(redisKey);
        if (StrUtil.isBlank(valueJSON)) {
            log.warn("{} data for {} {} is blank in Redis", returnTypeClass, key.getClass(), key);
            return null;
        }

        RedisData<ReturnType> redisData;
        try {
            redisData = JSONUtil.toBean(valueJSON, RedisData.class);
        } catch (Exception e) {
            log.error("Failed to parse RedisData from JSON for key {}: {}", key, e.getMessage());
            return null;
        }


        ReturnType returnObject;
        try {
            returnObject = JSONUtil.toBean((JSONObject) redisData.getData(), returnTypeClass);
        } catch (Exception e) {
            log.error("Failed to parse {} from RedisData for key {}: {}", returnTypeClass, key, e.getMessage());
            return null;
        }

        // 使用统一时间源，如果需要可以使用 Clock 注入
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(redisData.getExpireTime())) {
            log.debug("{} {} is still valid. Now : {}. Expire Time: {}", returnTypeClass, key, now, redisData.getExpireTime());
            return returnObject;
        }

        log.debug("{} {} expired, submitting rebuild task. Expire Time: {}", returnTypeClass, key, redisData.getExpireTime());

        // 获取重建任务的唯一标识及随机值
        String rebuildTaskKey = getRebuildKey(returnObject, redisData.getExpireTime());
        String rebuildTaskValue = UUID.randomUUID().toString(true);

        Boolean lockAcquired = stringRedisTemplate.opsForValue().setIfAbsent(
                rebuildTaskKey, rebuildTaskValue, RedisConstants.LOCK_TTL, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(lockAcquired)) {
            log.debug("Another thread already submitted rebuild task for {} key {}.", returnTypeClass, key);
            return returnObject;
        }

        log.debug("Thread acquired lock and submitting rebuild task for {} key {}.", returnTypeClass, key);

        CACHE_REBUILD_EXECUTOR.submit(() -> {
            {
                try {
                    ReturnType applied = function.apply(key);
                    RedisData<ReturnType> returnTypeRedisData = new RedisData<>();
                    returnTypeRedisData.setExpireTime(LocalDateTime.now().plus(time, TimeUnitConverter.toTemporalUnit(timeUnit)));
                    returnTypeRedisData.setData(applied);
                    stringRedisTemplate.execute(REBUILD_SCRIPT, Arrays.asList(rebuildTaskKey, redisKey), rebuildTaskValue, JSONUtil.toJsonStr(returnTypeRedisData));
                } catch (Exception ex) {
                    log.error("Error rebuilding cache for {} key {}: {}", returnTypeClass, key, ex.getMessage());
                } finally {
                    log.debug("Deleting rebuild task key {}", rebuildTaskKey);
                    try {
                        stringRedisTemplate.execute(
                                DELETE_REBUILD_LOCK_SCRIPT,
                                Collections.singletonList(rebuildTaskKey),
                                rebuildTaskValue
                        );
                    } catch (Exception e) {
                        log.error("Failed to release lock for key {}: {}", rebuildTaskKey, e.getMessage());
                    }
                }
            }
        });
        return returnObject;
    }


    @Override
    public String toString() {
        return stringRedisTemplate.toString() + "\t" + redissonClient.toString();
    }
}

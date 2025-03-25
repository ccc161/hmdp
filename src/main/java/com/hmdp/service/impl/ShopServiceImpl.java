package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.temporal.TemporalUnit;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    RedissonClient redissonClient;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(8);

    @Override
    public Result queryById(Long id) {
        Shop shop = queryByIdWithLogicalExpireTime(id);
        if (shop == null) return Result.fail("query shop failed");
        return Result.ok(shop);
    }

    private String getRebuildShopTaskId(Shop shop, LocalDateTime expireTime) {
        return shop.toString() + expireTime.toString();
    }

    public Shop queryByIdWithLogicalExpireTime(Long id) {
        String redisShopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJSON = stringRedisTemplate.opsForValue().get(redisShopKey);
        if (StrUtil.isBlank(shopJSON)) {
            log.warn("Shop data for id {} is blank in Redis", id);
            return null;
        }

        RedisData redisData;
        try {
            redisData = JSONUtil.toBean(shopJSON, RedisData.class);
        } catch (Exception e) {
            log.error("Failed to parse RedisData from JSON for id {}: {}", id, e.getMessage());
            return null;
        }


        Shop shop;
        try {
            shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        } catch (Exception e) {
            log.error("Failed to parse Shop from RedisData for id {}: {}", id, e.getMessage());
            return null;
        }

        // 使用统一时间源，如果需要可以使用 Clock 注入
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(redisData.getExpireTime())) {
            log.debug("Shop id {} is still valid. Now : {}. Expire Time: {}", id, now, redisData.getExpireTime());
            return shop;
        }

        log.debug("Shop id {} expired, submitting rebuild task. Now : {}. Expire Time: {}", id, now, redisData.getExpireTime());

        // 获取重建任务的唯一标识及随机值
        String rebuildTaskId = getRebuildShopTaskId(shop, redisData.getExpireTime());
        String rebuildTaskValue = UUID.randomUUID().toString();

        Boolean lockAcquired = stringRedisTemplate.opsForValue().setIfAbsent(
                rebuildTaskId, rebuildTaskValue, RedisConstants.LOCK_TTL, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(lockAcquired)) {
            log.debug("Another thread already submitted rebuild task for shop id {}.", id);
            return shop;
        }

        log.debug("Thread acquired lock and submitting rebuild task for shop id {}.", id);

        CACHE_REBUILD_EXECUTOR.submit(() -> {
            try {
                saveShopToRedis(id, RedisConstants.CACHE_SHOP_TTL);
            } catch (Exception ex) {
                log.error("Error rebuilding cache for shop id {}: {}", id, ex.getMessage());
            } finally {
                log.debug("Deleting rebuild task key {}", rebuildTaskId);
                final String LUA_SCRIPT =
                        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                                "   return redis.call('del', KEYS[1]) " +
                                "else " +
                                "   return 0 " +
                                "end";
                try {
                    stringRedisTemplate.execute(
                            new DefaultRedisScript<>(LUA_SCRIPT, Long.class),
                            Collections.singletonList(rebuildTaskId),
                            rebuildTaskValue
                    );
                } catch (Exception e) {
                    log.error("Failed to release lock for key {}: {}", rebuildTaskId, e.getMessage());
                }
            }
        });
        return shop;
    }

    public Shop queryByIdWithMutex(Long id) {
        String redisShopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJSON = stringRedisTemplate.opsForValue().get(redisShopKey);
        if (shopJSON != null)
            return shopJSON.equals(RedisConstants.NULL_VALUE) ? null : JSONUtil.toBean(shopJSON, Shop.class);
        Shop shop;
        String redisShopLockKey = RedisConstants.LOCK_SHOP_KEY + id;
        RLock lock = redissonClient.getLock(redisShopLockKey);
        try {
            lock.lock(RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
            shopJSON = stringRedisTemplate.opsForValue().get(redisShopKey);
            if (shopJSON != null)
                return shopJSON.equals(RedisConstants.NULL_VALUE) ? null : JSONUtil.toBean(shopJSON, Shop.class);
            shop = getById(id);
            if (shop == null) {
                // 缓存空值防止缓存穿透
                stringRedisTemplate.opsForValue().set(redisShopKey, RedisConstants.NULL_VALUE, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            } else {
                stringRedisTemplate.opsForValue().set(redisShopKey, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            }
            return shop;
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    public Shop queryByIdRaw(Long id) {
        String redisShopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJSON = stringRedisTemplate.opsForValue().get(redisShopKey);
        Shop shop;
        boolean cached = shopJSON != null;
        if (!cached) {
            shop = getById(id);
            if (shop == null) {
                // 缓存空值防止缓存穿透
                stringRedisTemplate.opsForValue().set(redisShopKey, RedisConstants.NULL_VALUE, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            } else {
                stringRedisTemplate.opsForValue().set(redisShopKey, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            }
        } else {
            shop = shopJSON.equals(RedisConstants.NULL_VALUE) ? null : JSONUtil.toBean(shopJSON, Shop.class);
        }
        return shop;
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        if (shop == null || shop.getId() == null) return Result.fail("shop and shop id can not be null");
        updateById(shop);
        String redisShopKey = RedisConstants.CACHE_SHOP_KEY + shop.getId();
        stringRedisTemplate.delete(redisShopKey);
        return Result.ok();
    }

    public void saveShopToRedis(Long id, Long expireSeconds) {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }
}

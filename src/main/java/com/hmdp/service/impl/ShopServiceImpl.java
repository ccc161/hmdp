package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
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
    CacheClient cacheClient;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(8);

    @Override
    public Result queryById(Long id) {
        Shop shop = queryByIdWithLogicalExpireTime(id);
        if (shop == null) return Result.fail("query shop failed");
        return Result.ok(shop);
    }

    public Shop queryByIdWithLogicalExpireTime(Long id) {
        return cacheClient.queryByIdWithLogicalExpireTime(
                RedisConstants.CACHE_SHOP_KEY,
                id,
                Shop.class,
                this::getById,
                RedisConstants.CACHE_SHOP_TTL,
                TimeUnit.SECONDS
        );
    }

    public Shop queryByIdWithMutex(Long id) {
        Shop shop = cacheClient.queryByKeyWithMutex(RedisConstants.CACHE_SHOP_KEY,
                id,
                Shop.class,
                this::getById,
                RedisConstants.CACHE_SHOP_TTL,
                TimeUnit.SECONDS);
        return shop;
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

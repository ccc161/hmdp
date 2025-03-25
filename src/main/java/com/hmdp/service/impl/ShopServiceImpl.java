package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Resource
    RedissonClient redissonClient;

    @Override
    public Result queryById(Long id) {
        Shop shop = queryByIdWithMutex(id);
        if (shop == null) return Result.fail("query shop failed");
        return Result.ok(shop);
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
            lock.unlock();
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
}

package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String typeListKey = RedisConstants.CACHE_SHOP_TYPE_LIST_KEY;
        Long size = stringRedisTemplate.opsForList().size(typeListKey);
        List<ShopType> typeList;
        boolean cached = !(size == null || size <= 0);
        if (!cached) {
            typeList = query().orderByAsc("sort").list();
            if (typeList == null || typeList.isEmpty()) {
                return Result.fail("internal error when query shop type list");
            }
            size = (long) typeList.size();
            List<String> typeJsonList = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                typeJsonList.add(JSONUtil.toJsonStr(typeList.get(i)));
            }
            stringRedisTemplate.opsForList().rightPushAll(typeListKey, typeJsonList);
            stringRedisTemplate.expire(typeListKey, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } else {
            typeList = new ArrayList<>();
            List<String> stringList = stringRedisTemplate.opsForList().range(typeListKey, 0, size - 1);
            for (int i = 0; i < size; i++) {
                typeList.add(JSONUtil.toBean(stringList.get(i), ShopType.class));
            }
        }
        return Result.ok(typeList);
    }
}

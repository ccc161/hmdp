package com.hmdp.utils;

public class RedisConstants {
    public static final String NULL_VALUE = "";
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final String LOGIN_USER_TOKENS_KEY = "login:user:";
    public static final Long LOGIN_USER_TTL_SECONDS = 60 * 24 * 365L;

    public static String getLoginTokenKey(String token) {
        return LOGIN_USER_KEY + token;
    }

    public static String getLoginTokensKey(String userId) {
        return LOGIN_USER_TOKENS_KEY + userId;
    }

    public static final Long LOCK_TTL = 5L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 10L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String CACHE_SHOP_TYPE_LIST_KEY = "cache:shop_type_list:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String SECKILL_VOUCHER_USER_KEY = "seckill:voucher:";

    public static final String SECKILL_LOCK_ORDER = "seckill:lock:order";
    public static final String SECKILL_LOCK_USER = "seckill:lock:user";
    public static final String SECKILL_LOCK_VOUCHER = "seckill:lock:voucher";

    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}

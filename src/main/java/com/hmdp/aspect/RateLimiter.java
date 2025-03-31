package com.hmdp.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimiter {
    /**
     * 每秒生成令牌数
     */
    long rate() default 3;

    /**
     * 最多获取令牌数（突发流量）
     */
    long maxPermits() default 10;

    /**
     * 等待令牌超时时间（毫秒）
     */
    long timeoutMillis() default 10;

    String keyPattern() default "user_{userId}_method_{methodName}";
}
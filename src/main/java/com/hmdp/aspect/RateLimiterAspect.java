package com.hmdp.aspect;

import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Slf4j
public class RateLimiterAspect {
    private final RedissonClient redissonClient;
    private final HttpServletRequest request;
    private final StringRedisTemplate stringRedisTemplate;

    public RateLimiterAspect(RedissonClient redissonClient, HttpServletRequest request, StringRedisTemplate stringRedisTemplate) {
        this.redissonClient = redissonClient;
        this.request = request;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Around("@annotation(RateLimiter) || @within(RateLimiter)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> targetClass = method.getDeclaringClass();

        // 显式获取注解（方法优先）
        RateLimiter methodAnnotation = method.getAnnotation(RateLimiter.class);
        RateLimiter classAnnotation = targetClass.getAnnotation(RateLimiter.class);
        RateLimiter effectiveLimiter = Optional.ofNullable(methodAnnotation)
                .orElseGet(() -> Optional.ofNullable(classAnnotation)
                        .orElseThrow(() -> new IllegalStateException("RateLimiter annotation not found")));

        // 生成限流Key（支持类级和方法级）
        String userId = effectiveLimiter.keyPattern().contains("userId") ? parseUserIdFromRequest(request) : "";
        String rateLimitKey = buildDynamicKey(effectiveLimiter.keyPattern(), targetClass, method, userId);

        RRateLimiter limiter = redissonClient.getRateLimiter(rateLimitKey);

        // 初始化限流规则
        limiter.trySetRate(
                RateType.OVERALL,
                effectiveLimiter.rate(),
                effectiveLimiter.maxPermits(),
                RateIntervalUnit.SECONDS
        );
        boolean acquired = limiter.tryAcquire(effectiveLimiter.timeoutMillis(), TimeUnit.MILLISECONDS);
        if (!acquired) {
            log.warn("reach maximum limiting rate : {}", rateLimitKey);
            throw new RuntimeException("Trigger maximum rate limiter");
        }
        return joinPoint.proceed();
    }

    private String parseUserIdFromRequest(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isEmpty()) {
            throw new RuntimeException("Unauthorized user!");
        }
        String tokenKey = RedisConstants.getLoginTokenKey(authorization);
        Object res = stringRedisTemplate.opsForHash().get(tokenKey, "id");
        if (res == null) {
            throw new RuntimeException("Unauthorized user!");
        }
        return res.toString();
    }

    private String buildDynamicKey(String pattern, Class<?> clazz, Method method, String userId) {
        String clazzString = clazz == null ? "" : clazz.getSimpleName();
        String methodString = method == null ? "" : method.getName();
        if (userId == null) userId = "";
        return pattern.replace("{userId}", userId)
                .replace("{className}", clazzString)
                .replace("{methodName}", methodString)
                .replace("{uri}", request.getRequestURI());
    }
}

package com.hmdp.aspect;

import com.hmdp.dto.Result;
import com.hmdp.utils.RedisConstants;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration;

@Aspect
@Component
@Slf4j
public class RateLimiterAspect {
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final String LIMIT_RATE_INFO = "Too many requests, please try again later.";

    public RateLimiterAspect(RedissonClient redissonClient, StringRedisTemplate stringRedisTemplate) {
        this.redissonClient = redissonClient;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Around("@annotation(RateLimiter) || @within(RateLimiter)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();


        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Class<?> targetClass = method.getDeclaringClass();

        // 显式获取注解（方法优先）
        RateLimiter methodAnnotation = method.getAnnotation(RateLimiter.class);
        RateLimiter classAnnotation = targetClass.getAnnotation(RateLimiter.class);
        if (methodAnnotation == null && classAnnotation == null) {
            return Result.ok(LIMIT_RATE_INFO);
        }
        RateLimiter effectiveLimiter = methodAnnotation != null ? methodAnnotation : classAnnotation;

        // 生成限流Key（支持类级和方法级）
        String userId = effectiveLimiter.keyPattern().contains("userId") ? parseUserIdFromRequest(request) : "";
        String rateLimitKey = buildDynamicKey(effectiveLimiter.keyPattern(), targetClass, method, userId, request);

        RRateLimiter limiter = redissonClient.getRateLimiter(rateLimitKey);

        // 初始化限流规则
        limiter.trySetRate(RateType.OVERALL, effectiveLimiter.rate(), Duration.ofSeconds(effectiveLimiter.durationSeconds()));
        boolean acquired = limiter.tryAcquire(Duration.ofMillis(effectiveLimiter.timeoutMillis()));
        if (!acquired) {
            return Result.ok(LIMIT_RATE_INFO);
        }
        return joinPoint.proceed();
    }

    private void writeLimitedResponse(HttpServletResponse response) {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        try {
            response.getWriter().write(LIMIT_RATE_INFO);
        } catch (IOException e) {
            log.error("error in rate limiter : {}", e.toString());
        }
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

    private String buildDynamicKey(String pattern, Class<?> clazz, Method method, String userId, HttpServletRequest request) {
        String clazzString = clazz == null ? "" : clazz.getSimpleName();
        String methodString = method == null ? "" : method.getName();
        if (userId == null) userId = "";
        String res = pattern.replace("{userId}", userId).replace("{className}", clazzString).replace("{methodName}", methodString).replace("{uri}", request.getRequestURI());
        res = "rate:imiter:" + res;
        return res;
    }
}

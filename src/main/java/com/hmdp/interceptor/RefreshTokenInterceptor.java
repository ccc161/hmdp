package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {
    StringRedisTemplate stringRedisTemplate;
    private static final DefaultRedisScript<Long> REFRESH_TOKEN_SCRIPT;

    static {
        REFRESH_TOKEN_SCRIPT = new DefaultRedisScript<>();
        REFRESH_TOKEN_SCRIPT.setLocation(new ClassPathResource("lua/user/refresh_token.lua"));
        REFRESH_TOKEN_SCRIPT.setResultType(Long.class);
    }

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("authorization");
        if (token == null || token.isEmpty()) {
            log.debug("token is empty.");
            return true;
        }
        String tokenKey = RedisConstants.getLoginTokenKey(token);
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);
        if (userMap.isEmpty()) {
            response.setStatus(401);
            log.debug("user map is empty.");
            return true;
        }
        UserDTO userDTO;
        try {
            userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        } catch (Exception e) {
            response.setStatus(401);
            log.debug(e.toString());
            return true;
        }
        UserHolder.saveUser(userDTO);
        //local token_key = KEYS[1]
        //local tokens_key = KEYS[2]
        //local expire_millis = tonumber(ARGV[1])
        Long execute = stringRedisTemplate.execute(REFRESH_TOKEN_SCRIPT, Arrays.asList(RedisConstants.getLoginTokensKey(userDTO.getId().toString()), RedisConstants.getLoginTokenKey(token)), String.valueOf(RedisConstants.LOGIN_USER_TTL_SECONDS * 1000));
        if (execute != null && execute == 0L) {
            log.debug("refresh token expire time");
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}

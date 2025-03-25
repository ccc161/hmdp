package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (phone == null) return Result.nullFail("phone");
        if (session == null) return Result.nullFail("session");
        // 1. 校验手机号
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);
        if (phoneInvalid) return Result.fail("invalid phone number");
        // 2. 生成验证码
        String code = RandomUtil.randomNumbers(6);
        // 3. 保存验证码
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 4. 发送验证码
        log.debug("phone : {}, code : {}", phone, code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone(), code = loginForm.getCode(), password = loginForm.getPassword();
        if (phone == null) return Result.nullFail("phone");
        if (RegexUtils.isPhoneInvalid(phone)) return Result.fail("invalid phone number");
        if (code != null) {
            return loginByCode(loginForm, session, code);
        }
        if (password != null) {
            return loginByPassword(loginForm, session, password);
        }
        return Result.fail("login failed");
    }

    private Result loginByPassword(LoginFormDTO loginForm, HttpSession session, String password) {
        // todo
        return Result.fail("todo");
    }

    private Result loginByCode(LoginFormDTO loginForm, HttpSession session, String code) {
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginForm.getPhone());
        if (cacheCode == null || !cacheCode.equals(code)) return Result.nullFail("invalid code");
        User user = query().eq("phone", loginForm.getPhone()).one();
        if (user == null) {
            Optional<User> userByPhone = createUserByPhone(loginForm.getPhone());
            if (!userByPhone.isPresent()) {
                return Result.fail("login failed");
            }
            user = userByPhone.get();
        }


        String token = UUID.randomUUID().toString();
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        Map<String, Object> userMap = BeanUtil.beanToMap(BeanUtil.copyProperties(user, UserDTO.class), new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((s, o) -> o.toString()));
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    private Optional<User> createUserByPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        boolean saved = save(user);
        if (saved) return Optional.of(user);
        else return Optional.empty();
    }
}

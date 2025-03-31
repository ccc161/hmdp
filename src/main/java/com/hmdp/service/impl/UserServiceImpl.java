package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONUtil;
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
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.*;
import java.util.concurrent.TimeUnit;

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

    private static final int MAX_LOGIN_DEVICES = 5;

    private static final DefaultRedisScript<Long> DELETE_TOKEN_SCRIPT;
    private static final DefaultRedisScript<Long> LOGIN_BY_TOKEN_SCRIPT;
    private static final DefaultRedisScript<Long> DELETE_OTHERS_SCRIPT;

    static {
        DELETE_TOKEN_SCRIPT = new DefaultRedisScript<>();
        DELETE_TOKEN_SCRIPT.setLocation(new ClassPathResource("lua/user/delete_token.lua"));
        LOGIN_BY_TOKEN_SCRIPT = new DefaultRedisScript<>();
        LOGIN_BY_TOKEN_SCRIPT.setLocation(new ClassPathResource("lua/user/login_by_token.lua"));
        DELETE_OTHERS_SCRIPT = new DefaultRedisScript<>();
        DELETE_OTHERS_SCRIPT.setLocation(new ClassPathResource("lua/user/delete_others.lua"));
    }

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
            return loginByCode(loginForm, code);
        }
        if (password != null) {
            return loginByPassword(loginForm, session, password);
        }
        return Result.fail("login failed");
    }

    private Result loginByToken(UserDTO userDTO, String token) {
        if (userDTO == null || userDTO.getId() == null) return Result.fail("user id can not be null");
        if (token == null) return Result.fail("token can not be null");
        String tokenKey = RedisConstants.getLoginTokenKey(token);
        String tokensKey = RedisConstants.getLoginTokensKey(userDTO.getId().toString());
        long currentTimeMillis = System.currentTimeMillis();
        long expireTimeMillis = RedisConstants.LOGIN_USER_TTL_SECONDS * 1000;
        Map<String, Object> map = BeanUtil.beanToMap(userDTO, new HashMap<>()
                , CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor(
                                (name, value) -> value.toString()
                        ));
        stringRedisTemplate.execute(LOGIN_BY_TOKEN_SCRIPT, Arrays.asList(tokensKey, tokenKey), String.valueOf(currentTimeMillis), String.valueOf(expireTimeMillis), String.valueOf(MAX_LOGIN_DEVICES), JSONUtil.toJsonStr(map));
        return Result.ok(token);
    }

    @Override
    public Result logout(String token) {
        if (token == null || token.isEmpty()) return Result.fail("token must not be empty");
        String tokenKey = RedisConstants.getLoginTokenKey(token);
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) return Result.fail("user must not be empty");
        String userTokensKey = RedisConstants.getLoginTokensKey(user.getId().toString());
        stringRedisTemplate.execute(DELETE_TOKEN_SCRIPT, Collections.singletonList(userTokensKey), tokenKey);
        return Result.ok("log out successfully");
    }

    @Override
    public Result logoutOtherDevices(String token) {
        UserDTO user = UserHolder.getUser();
        if (user == null || user.getId() == null) return Result.fail("user must not be empty");
        String userTokensKey = RedisConstants.getLoginTokensKey(user.getId().toString());
        if (token == null || token.isEmpty()) return Result.fail("token must not be empty");
        String excludedToken = RedisConstants.getLoginTokenKey(token);
        stringRedisTemplate.execute(DELETE_OTHERS_SCRIPT, Arrays.asList(userTokensKey), excludedToken);
        return Result.ok("log out other devices successfully");
    }

    private Result loginByPassword(LoginFormDTO loginForm, HttpSession session, String password) {
        // todo
        return Result.fail("todo");
    }

    private Result loginByCode(LoginFormDTO loginForm, String code) {
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
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return loginByToken(userDTO, token);
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

package com.lis.auth.service;

import com.lis.api.dto.LoginDTO;
import com.lis.api.vo.LoginVO;
import com.lis.common.annotation.OperLog;
import com.lis.common.core.constant.CacheKey;
import com.lis.common.core.exception.BusinessException;
import com.lis.common.redis.RedisUtil;
import com.lis.common.security.JwtUtil;
import com.lis.common.security.SecurityContext;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 认证业务逻辑
 * <p>
 * 通过 JdbcTemplate 直接查询 sys_user 表,
 * BCrypt 校验密码, 生成 JWT, 缓存用户权限到 Redis。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String SQL_FIND_USER_BY_USERNAME =
            "SELECT id, username, password, real_name, phone, email, department_id, status " +
                    "FROM sys_user WHERE username = ?";

    private static final String SQL_UPDATE_LAST_LOGIN =
            "UPDATE sys_user SET last_login_time = ? WHERE id = ?";

    private static final String SQL_FIND_PERMISSIONS_BY_USER =
            "SELECT DISTINCT p.perm_code FROM sys_user_role ur " +
                    "JOIN sys_role_permission rp ON ur.role_id = rp.role_id " +
                    "JOIN sys_permission p ON rp.permission_id = p.id " +
                    "WHERE ur.user_id = ? AND p.status = 1";

    private final JdbcTemplate jdbcTemplate;
    private final RedisUtil redisUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    public AuthService(JdbcTemplate jdbcTemplate, RedisUtil redisUtil) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisUtil = redisUtil;
    }

    /**
     * 登录
     */
    @OperLog(module = "认证管理", operation = "用户登录", recordParams = false, recordResult = true)
    public LoginVO login(LoginDTO dto) {
        if (dto == null || dto.getUsername() == null || dto.getPassword() == null) {
            throw new BusinessException(400, "用户名或密码不能为空");
        }

        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                SQL_FIND_USER_BY_USERNAME, dto.getUsername());
        if (users.isEmpty()) {
            throw new BusinessException(400, "用户不存在或密码错误");
        }

        Map<String, Object> user = users.get(0);
        Integer status = (Integer) user.get("status");
        if (status != null && status == 0) {
            throw new BusinessException(400, "账号已停用, 请联系管理员");
        }

        String storedPassword = (String) user.get("password");
        if (!passwordEncoder.matches(dto.getPassword(), storedPassword)) {
            throw new BusinessException(400, "用户不存在或密码错误");
        }

        Long userId = ((Number) user.get("id")).longValue();
        String username = (String) user.get("username");
        String realName = (String) user.get("real_name");

        // 查询用户权限
        List<String> permissions = jdbcTemplate.queryForList(
                SQL_FIND_PERMISSIONS_BY_USER, String.class, userId);
        if (permissions == null) {
            permissions = Collections.emptyList();
        }

        // 生成 JWT
        Map<String, Object> extra = new HashMap<>();
        extra.put("realName", realName);
        String token = JwtUtil.generateToken(userId, username, extra);

        // 缓存用户权限到 Redis
        redisUtil.set(CacheKey.USER_PERMISSIONS + userId, permissions,
                CacheKey.DEFAULT_TTL, TimeUnit.SECONDS);

        // 缓存用户会话
        SecurityContext.LoginUser loginUser = new SecurityContext.LoginUser(userId, username, realName);
        loginUser.setPermissions(permissions);
        Object deptId = user.get("department_id");
        if (deptId != null) {
            loginUser.setDepartmentId(((Number) deptId).longValue());
        }
        redisUtil.set(CacheKey.USER_SESSION + token, loginUser,
                CacheKey.DEFAULT_TTL, TimeUnit.SECONDS);

        // 更新最后登录时间
        try {
            jdbcTemplate.update(SQL_UPDATE_LAST_LOGIN, LocalDateTime.now(), userId);
        } catch (Exception e) {
            log.warn("更新最后登录时间失败: {}", e.getMessage());
        }

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserId(userId);
        vo.setUsername(username);
        vo.setRealName(realName);
        vo.setPermissions(permissions);
        return vo;
    }

    /**
     * 注销 - 将 token 加入 Redis 黑名单
     */
    public void logout(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        try {
            Claims claims = JwtUtil.parseToken(token);
            long expiration = claims.getExpiration().getTime();
            long now = System.currentTimeMillis();
            long ttl = expiration - now;
            if (ttl > 0) {
                redisUtil.set(CacheKey.TOKEN_BLACKLIST + token, "1",
                        ttl, TimeUnit.MILLISECONDS);
            }
            // 清除会话缓存
            Long userId = Long.parseLong(claims.getSubject());
            redisUtil.delete(CacheKey.USER_SESSION + token);
            redisUtil.delete(CacheKey.USER_PERMISSIONS + userId);
        } catch (Exception e) {
            log.warn("注销时解析 token 失败: {}", e.getMessage());
        }
    }

    /**
     * 获取当前登录用户信息 - 从 SecurityContext 获取
     */
    public SecurityContext.LoginUser getUserInfo() {
        SecurityContext.LoginUser user = SecurityContext.get();
        if (user == null) {
            throw new BusinessException(401, "未登录或登录已过期");
        }
        return user;
    }
}

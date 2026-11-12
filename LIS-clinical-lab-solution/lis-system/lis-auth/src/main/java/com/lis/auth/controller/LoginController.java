package com.lis.auth.controller;

import com.lis.api.dto.LoginDTO;
import com.lis.api.vo.LoginVO;
import com.lis.auth.service.AuthService;
import com.lis.common.core.result.R;
import com.lis.common.security.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录认证接口
 */
@RestController
@RequestMapping("/api/auth")
public class LoginController {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    @Autowired
    public LoginController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 登录 - 校验用户名密码, 生成 JWT, 返回 LoginVO
     */
    @PostMapping("/login")
    public R<LoginVO> login(@RequestBody LoginDTO dto) {
        LoginVO vo = authService.login(dto);
        return R.ok(vo);
    }

    /**
     * 注销 - 将 token 加入 Redis 黑名单
     */
    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            authService.logout(token);
        }
        return R.ok();
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/info")
    public R<SecurityContext.LoginUser> info() {
        return R.ok(authService.getUserInfo());
    }
}

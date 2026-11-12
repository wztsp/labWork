package com.lis.reagent.controller;

import com.lis.common.core.result.R;
import com.lis.reagent.config.AuthConfig;
import com.lis.reagent.config.LoginProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 登录 / 退出 / 当前登录用户。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginProperties loginProperties;

    /** 登录：账号密码取自 config/application.yml 的 app.login */
    @PostMapping("/login")
    public R<Map<String, String>> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String username = body.get("username") == null ? "" : body.get("username").trim();
        String password = body.get("password") == null ? "" : body.get("password");

        if (!loginProperties.getUsername().equals(username)
                || !loginProperties.getPassword().equals(password)) {
            return R.fail(401, "用户名或密码错误");
        }

        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(AuthConfig.SESSION_USER, username);
        session.setMaxInactiveInterval(loginProperties.getSessionTimeoutMinutes() * 60);

        Map<String, String> data = new LinkedHashMap<>();
        data.put("username", username);
        return R.ok(data);
    }

    /** 退出登录 */
    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return R.ok();
    }

    /** 当前登录状态（供前端判断） */
    @GetMapping("/current")
    public R<Map<String, String>> current(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object username = session == null ? null : session.getAttribute(AuthConfig.SESSION_USER);
        if (username == null) {
            return R.fail(401, "未登录");
        }
        Map<String, String> data = new LinkedHashMap<>();
        data.put("username", username.toString());
        return R.ok(data);
    }
}

package com.lis.reagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 登录配置：账号写在 config/application.yml 的 app.login 下，
 * 不依赖数据库，升级时只需替换 jar 即可。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.login")
public class LoginProperties {

    /** 是否启用登录校验（排障时可临时设为 false） */
    private boolean enabled = true;

    /** 登录账号 */
    private String username = "admin";

    /** 登录密码 */
    private String password = "admin123";

    /** 会话闲置有效期（分钟），默认 8 小时 */
    private int sessionTimeoutMinutes = 480;
}

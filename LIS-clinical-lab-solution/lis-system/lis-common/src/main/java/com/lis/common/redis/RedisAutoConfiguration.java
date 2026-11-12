package com.lis.common.redis;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * LIS 公共模块自动配置 - 让所有服务自动扫描 com.lis.common 下的组件
 * 包括: RedisUtil, RedisConfig, MybatisPlusConfig, GlobalExceptionHandler, SwaggerConfig 等
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.lis.common")
public class RedisAutoConfiguration {
}

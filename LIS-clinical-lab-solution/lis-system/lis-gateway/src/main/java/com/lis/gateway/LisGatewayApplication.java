package com.lis.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * LIS API 网关启动类
 * <p>
 * 网关基于 WebFlux, 不依赖 MyBatis-Plus/数据库。
 * 通过 lis-common 中 @ConditionalOnClass 注解自动跳过 MybatisPlusConfig。
 */
@SpringBootApplication
@EnableDiscoveryClient
public class LisGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(LisGatewayApplication.class, args);
    }
}

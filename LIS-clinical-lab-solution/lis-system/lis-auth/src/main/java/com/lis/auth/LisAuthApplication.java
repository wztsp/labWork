package com.lis.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * LIS 认证服务启动类
 */
@SpringBootApplication
@EnableDiscoveryClient
public class LisAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(LisAuthApplication.class, args);
    }
}

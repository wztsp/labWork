package com.lis.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * HIS 对接服务启动类
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
public class LisIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(LisIntegrationApplication.class, args);
    }
}

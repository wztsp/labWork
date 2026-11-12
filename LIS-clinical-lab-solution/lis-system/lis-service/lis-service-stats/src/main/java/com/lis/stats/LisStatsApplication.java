package com.lis.stats;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 统计分析服务启动类
 */
@SpringBootApplication
@EnableDiscoveryClient
public class LisStatsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LisStatsApplication.class, args);
    }
}

package com.lis.system;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * LIS 系统管理服务启动类
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.lis.system.mapper")
public class LisSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(LisSystemApplication.class, args);
    }
}

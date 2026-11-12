package com.lis.base;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.lis.base.mapper")
public class LisBaseApplication {

    public static void main(String[] args) {
        SpringApplication.run(LisBaseApplication.class, args);
    }
}

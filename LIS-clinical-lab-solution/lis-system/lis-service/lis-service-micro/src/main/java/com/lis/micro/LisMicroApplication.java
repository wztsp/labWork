package com.lis.micro;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.lis.micro.mapper")
public class LisMicroApplication {

    public static void main(String[] args) {
        SpringApplication.run(LisMicroApplication.class, args);
    }
}

package com.lis.sample;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@EnableRabbit
@MapperScan("com.lis.sample.mapper")
public class LisSampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(LisSampleApplication.class, args);
    }
}

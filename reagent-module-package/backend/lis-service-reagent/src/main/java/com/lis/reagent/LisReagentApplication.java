package com.lis.reagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@MapperScan("com.lis.reagent.mapper")
public class LisReagentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LisReagentApplication.class, args);
    }
}

package com.lis.instrumentmgmt;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@MapperScan("com.lis.instrumentmgmt.mapper")
public class LisInstrumentMgmtApplication {

    public static void main(String[] args) {
        SpringApplication.run(LisInstrumentMgmtApplication.class, args);
    }
}

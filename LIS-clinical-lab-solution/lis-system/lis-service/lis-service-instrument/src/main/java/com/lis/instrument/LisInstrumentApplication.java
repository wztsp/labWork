package com.lis.instrument;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 仪器通讯服务启动类
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@EnableRabbit
@MapperScan("com.lis.instrument.mapper")
public class LisInstrumentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LisInstrumentApplication.class, args);
    }
}

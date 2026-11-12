package com.lis.report;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;

/**
 * 报告管理服务启动类
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableRabbit
@MapperScan("com.lis.report.mapper")
@ComponentScan(basePackages = {"com.lis.report", "com.lis.common.notify"})
public class LisReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(LisReportApplication.class, args);
    }
}

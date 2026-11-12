package com.lis.reagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 试剂管理独立服务启动类
 * 说明：独立部署版已移除 Nacos 注册发现，仅保留 Web + MyBatis-Plus + 定时任务。
 */
@SpringBootApplication
@EnableScheduling
@MapperScan("com.lis.reagent.mapper")
public class LisReagentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LisReagentApplication.class, args);
    }
}

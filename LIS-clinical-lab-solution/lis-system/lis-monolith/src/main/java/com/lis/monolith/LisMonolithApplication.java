package com.lis.monolith;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * LIS 单体应用启动类
 * 将所有业务模块整合到一个进程中运行
 */
@SpringBootApplication(scanBasePackages = {
    "com.lis",           // 扫描所有 com.lis 包下的组件
})
@MapperScan(basePackages = {
    "com.lis.base.mapper",
    "com.lis.system.mapper",
    "com.lis.hr.mapper",
    "com.lis.sample.mapper",
    "com.lis.report.mapper",
    "com.lis.qc.mapper",
    "com.lis.instrument.mapper",
    "com.lis.instrumentmgmt.mapper",
    "com.lis.reagent.mapper",
    "com.lis.micro.mapper",
    "com.lis.integration.mapper",
    "com.lis.stats.mapper",
    "com.lis.monolith.mapper"
})
@EnableScheduling
public class LisMonolithApplication {

    /**
     * 存放 @Bean 方式手动声明的 MessageListenerContainer（例如显式 new SimpleMessageListenerContainer）
     */
    @Autowired(required = false)
    private Map<String, MessageListenerContainer> beanDeclaredContainers;

    /**
     * 存放 @RabbitListener 注解驱动的容器（这些容器不由 Spring 直接注册为 Bean，
     * 而是由 RabbitListenerEndpointRegistry 统一托管，不能直接 Map<String, MLC> 注入）
     */
    @Autowired(required = false)
    private RabbitListenerEndpointRegistry rabbitListenerEndpointRegistry;

    /**
     * 是否在启动完成后强制停止所有 MQ 监听容器。
     * 默认 true，保持开发环境（本机无 RabbitMQ）的既有行为；
     * 生产/离线部署包在外部配置中设为 false，让 MQ 消费者正常工作。
     */
    @Value("${lis.mq.stop-listeners-on-startup:true}")
    private boolean stopListenersOnStartup;

    public static void main(String[] args) {
        SpringApplication.run(LisMonolithApplication.class, args);
    }

    /**
     * 开发环境/本机无 RabbitMQ 时，强制停止所有已启动的 MQ 监听器容器。
     * 背景：@RabbitListener 工厂配置 auto-startup=false 仅能限制注解驱动的容器，
     * 而手动 @Bean 创建的容器会在启动期立即连接 localhost:5672，连接失败后每个容器
     * 又会立即创建新的消费者线程重连，短时间内把 Tomcat 线程池挤爆，导致正常 HTTP 请求
     * 拿不到工作线程，变成 "空响应体 + status 500" 的容器级故障。
     * 生产环境只需把 spring.rabbitmq.listener.simple.auto-startup 改回 true，
     * 或把本方法的 stop 逻辑按环境开关即可。
     */
    @EventListener(ContextRefreshedEvent.class)
    public void devForceStopAllMqListenerContainers() {
        if (!stopListenersOnStartup) return;
        Set<MessageListenerContainer> all = new HashSet<>();
        if (beanDeclaredContainers != null && !beanDeclaredContainers.isEmpty()) {
            all.addAll(beanDeclaredContainers.values());
        }
        if (rabbitListenerEndpointRegistry != null) {
            Collection<? extends MessageListenerContainer> fromRegistry = rabbitListenerEndpointRegistry.getListenerContainers();
            if (fromRegistry != null && !fromRegistry.isEmpty()) {
                all.addAll(fromRegistry);
            }
        }
        if (all.isEmpty()) return;
        int stopped = 0;
        for (MessageListenerContainer container : all) {
            try {
                if (container != null && container.isRunning()) {
                    container.stop();
                    stopped++;
                }
            } catch (Exception ignore) {
                // ignore
            }
        }
        System.out.println("[DEV-RABBITMQ] 共检测到 " + all.size() + " 个 MQ 监听器容器，本次已 STOP " + stopped + " 个运行中容器");
    }
}

package com.lis.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关路由配置 - 按路径前缀转发到下游微服务
 * 使用直接 URL 路由, 不依赖 Nacos 服务发现
 */
@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("auth-service", r -> r.path("/api/auth/**").uri("http://localhost:8090"))
            .route("sample-service", r -> r.path("/api/sample/**").uri("http://localhost:8081"))
            .route("report-service", r -> r.path("/api/report/**").uri("http://localhost:8082"))
            .route("qc-service", r -> r.path("/api/qc/**").uri("http://localhost:8083"))
            // 校准/维修记录在 base 服务 (8085), 需在 instrument-service 路由之前匹配
            .route("base-instrument-calibration", r -> r.path("/api/instrument/calibration/**").uri("http://localhost:8085"))
            .route("base-instrument-repair", r -> r.path("/api/instrument/repair/**").uri("http://localhost:8085"))
            .route("instrument-service", r -> r.path("/api/instrument/**").uri("http://localhost:8084"))
            .route("base-service", r -> r.path("/api/base/**").uri("http://localhost:8085"))
            .route("system-service", r -> r.path("/api/system/**").uri("http://localhost:8087"))
            .route("stats-service", r -> r.path("/api/stats/**").uri("http://localhost:8088"))
            .route("instrument-mgmt-service", r -> r.path("/api/instrument-mgmt/**").uri("http://localhost:8091"))
            .route("reagent-service", r -> r.path("/api/reagent/**").uri("http://localhost:8092"))
            .route("micro-service", r -> r.path("/api/micro/**").uri("http://localhost:8093"))
            .route("hr-service", r -> r.path("/api/hr/**").uri("http://localhost:8094"))
            .route("integration-service", r -> r.path("/api/integration/**").uri("http://localhost:8086"))
            .build();
    }
}

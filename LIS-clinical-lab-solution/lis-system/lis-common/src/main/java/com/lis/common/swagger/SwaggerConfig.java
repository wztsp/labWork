package com.lis.common.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("LIS 临床实验室信息系统 API")
                        .version("1.0.0")
                        .description("临床实验室信息系统接口文档")
                        .contact(new Contact().name("LIS Team").email("lis@hospital.com"))
                        .license(new License().name("Apache 2.0")));
    }
}

package com.ravan.SpringBootLab.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI springBootLabOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Spring Boot Lab API")
                        .version("1.0.0")
                        .description("A Spring Boot REST API with PostgreSQL, JPA, DTO, Validation, Exception Handler, ApiResponse, Pagination and Sorting."));
    }
}
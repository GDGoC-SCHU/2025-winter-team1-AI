package com.example.gemini_server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**") // ✅ 올바른 문법
                        .allowedOrigins("http://localhost:3000") // ✅ 올바른 문법
                        .allowedMethods("GET", "POST", "PUT", "DELETE") // ✅ 올바른 문법
                        .allowCredentials(true); // ✅ 올바른 문법
            }
        };
    }
}   
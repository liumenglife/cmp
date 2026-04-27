package com.cmp.platform.health;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class HealthCorsConfig {

    @Bean
    WebMvcConfigurer healthEndpointCorsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/actuator/health")
                        .allowedOrigins("http://localhost:5173")
                        .allowedMethods("GET");
            }
        };
    }
}

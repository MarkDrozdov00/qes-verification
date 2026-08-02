package com.notarity.validator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${app.allowed-origin:http://localhost:5173}") String origin;
    private final ValidationDiagnosticInterceptor validationDiagnosticInterceptor;

    public WebConfig(ValidationDiagnosticInterceptor validationDiagnosticInterceptor) {
        this.validationDiagnosticInterceptor = validationDiagnosticInterceptor;
    }

    @Override public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(origin).allowedMethods("GET", "POST").allowedHeaders("*");
    }
    @Override public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(validationDiagnosticInterceptor).addPathPatterns("/api/v1/validations");
    }
}

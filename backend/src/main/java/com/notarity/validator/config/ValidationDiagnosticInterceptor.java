package com.notarity.validator.config;

import com.notarity.validator.validation.dss.ValidationDiagnosticContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class ValidationDiagnosticInterceptor implements HandlerInterceptor {
    private final ValidationDiagnosticContext context;

    public ValidationDiagnosticInterceptor(ValidationDiagnosticContext context) {
        this.context = context;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        context.clear();
    }
}

package com.notarity.validator.api;

import com.notarity.validator.validation.dss.DssProcessingStage;
import com.notarity.validator.validation.dss.ValidationDiagnosticContext;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice
public class ValidationResponseStageAdvice implements ResponseBodyAdvice<Object> {
    private final ValidationDiagnosticContext context;

    public ValidationResponseStageAdvice(ValidationDiagnosticContext context) {
        this.context = context;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (request.getURI().getPath().equals("/api/v1/validations")) {
            context.stage(DssProcessingStage.RESPONSE_SERIALIZATION);
        }
        return body;
    }
}

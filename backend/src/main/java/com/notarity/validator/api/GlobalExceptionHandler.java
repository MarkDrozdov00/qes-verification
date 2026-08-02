package com.notarity.validator.api;

import com.notarity.validator.api.ApiModels.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;
import java.util.Map;
import com.notarity.validator.validation.dss.DssValidationException;
import com.notarity.validator.validation.dss.ValidationDiagnosticContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import com.notarity.validator.report.ReportGenerationException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String DSS_FAILURE_MESSAGE = "The PDF could be read, but its embedded signatures could not be processed.";
    private final ValidationDiagnosticContext diagnostics;
    private final boolean includeFailureStage;

    public GlobalExceptionHandler(ValidationDiagnosticContext diagnostics,
        @Value("${app.diagnostics.include-failure-stage:true}") boolean includeFailureStage) {
        this.diagnostics = diagnostics;
        this.includeFailureStage = includeFailureStage;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e, HttpServletRequest request) {
        return response(e.status(), e.code(), e.getMessage(), request.getRequestURI());
    }
    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiError> missing(Exception e, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MISSING_DOCUMENT", "Select a PDF document to validate.", request.getRequestURI());
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> large(Exception e, HttpServletRequest request) {
        return response(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "The document exceeds the 25 MB upload limit.", request.getRequestURI());
    }
    @ExceptionHandler(DssValidationException.class)
    ResponseEntity<ApiError> dss(DssValidationException e, HttpServletRequest request) {
        var state = diagnostics.current();
        String validationId = state == null ? null : state.validationId();
        String filename = state == null ? "document.pdf" : state.sanitizedFilename();
        Throwable root = rootCause(e);
        log.error("DSS validation failed validationId={} filename={} stage={} exceptionClass={}",
            validationId, filename, e.processingStage(), root.getClass().getName(), e);
        Map<String, Object> details = includeFailureStage
            ? Map.of("stage", e.processingStage().name()) : Map.of();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ApiError(Instant.now(), 422,
            HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(), e.safeErrorCode(), DSS_FAILURE_MESSAGE,
            request.getRequestURI(), validationId, details));
    }
    @ExceptionHandler(ReportGenerationException.class)
    ResponseEntity<ApiError> report(ReportGenerationException e, HttpServletRequest request) {
        var state = diagnostics.current();
        String validationId = state == null ? null : state.validationId();
        log.error("Report generation failed validationId={} exceptionClass={}", validationId,
            rootCause(e).getClass().getName(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(Instant.now(), 500,
            HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), "REPORT_GENERATION_FAILED",
            "The verification report could not be generated.", request.getRequestURI(), validationId, Map.of()));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest request) {
        var state = diagnostics.current();
        if (state != null) {
            Throwable root = rootCause(e);
            log.error("Validation request failed validationId={} filename={} stage={} exceptionClass={}",
                state.validationId(), state.sanitizedFilename(), state.stage(), root.getClass().getName(), e);
            Map<String, Object> details = includeFailureStage ? Map.of("stage", state.stage().name()) : Map.of();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(Instant.now(), 500,
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), "SIGNATURE_VALIDATION_FAILED",
                DSS_FAILURE_MESSAGE, request.getRequestURI(), state.validationId(), details));
        }
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "The document could not be validated due to an unexpected error.", request.getRequestURI());
    }
    private Throwable rootCause(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root;
    }
    private ResponseEntity<ApiError> response(HttpStatus status, String code, String message, String path) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), status.getReasonPhrase(),
            code, message, path, null, Map.of()));
    }
}

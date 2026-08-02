package com.notarity.validator.validation.dss;

public class DssValidationException extends RuntimeException {
    private final String safeErrorCode;
    private final DssProcessingStage processingStage;

    public DssValidationException(String safeErrorCode, DssProcessingStage processingStage, Throwable cause) {
        super(cause);
        this.safeErrorCode = safeErrorCode;
        this.processingStage = processingStage;
    }

    public String safeErrorCode() {
        return safeErrorCode;
    }

    public DssProcessingStage processingStage() {
        return processingStage;
    }
}

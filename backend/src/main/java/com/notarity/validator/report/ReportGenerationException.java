package com.notarity.validator.report;

public class ReportGenerationException extends RuntimeException {
    public ReportGenerationException(Throwable cause) { super("Verification report generation failed", cause); }
}

package com.notarity.validator.api;

import com.notarity.validator.validation.dss.DssProcessingStage;
import com.notarity.validator.validation.dss.DssValidationException;
import com.notarity.validator.validation.dss.ValidationDiagnosticContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void dssFailureReturnsOnlySafeDevelopmentDiagnostics() {
        var diagnostics = new ValidationDiagnosticContext();
        diagnostics.begin("../../private signer document.pdf");
        diagnostics.stage(DssProcessingStage.DSS_MAPPING);
        var handler = new GlobalExceptionHandler(diagnostics, true);
        var request = new MockHttpServletRequest("POST", "/api/v1/validations");
        var exception = new DssValidationException(
            "SIGNATURE_VALIDATION_FAILED",
            DssProcessingStage.DSS_MAPPING,
            new IllegalStateException("sensitive library message"));

        var response = handler.dss(exception, request);
        var body = response.getBody();

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("SIGNATURE_VALIDATION_FAILED");
        assertThat(body.message()).isEqualTo(
            "The PDF could be read, but its embedded signatures could not be processed.");
        assertThat(body.details()).containsEntry("stage", "DSS_MAPPING");
        assertThat(body.message()).doesNotContain("sensitive", "IllegalStateException");
    }

    @Test
    void productionStyleResponseCanSuppressFailureStage() {
        var diagnostics = new ValidationDiagnosticContext();
        diagnostics.begin("document.pdf");
        var handler = new GlobalExceptionHandler(diagnostics, false);
        var request = new MockHttpServletRequest("POST", "/api/v1/validations");
        var exception = new DssValidationException(
            "SIGNATURE_VALIDATION_FAILED",
            DssProcessingStage.DSS_VALIDATION,
            new ExceptionInInitializerError("provider detail"));

        var body = handler.dss(exception, request).getBody();

        assertThat(body).isNotNull();
        assertThat(body.details()).isEmpty();
    }
}

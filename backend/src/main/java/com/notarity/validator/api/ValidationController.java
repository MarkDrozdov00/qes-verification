package com.notarity.validator.api;

import com.notarity.validator.api.ApiModels.ApiError;
import com.notarity.validator.api.ApiModels.DocumentValidationResponse;
import com.notarity.validator.document.DocumentInspectionService;
import com.notarity.validator.validation.SignatureValidationService;
import com.notarity.validator.validation.ValidationOptions;
import com.notarity.validator.validation.dss.DssProcessingStage;
import com.notarity.validator.validation.dss.DssValidationException;
import com.notarity.validator.validation.dss.ValidationDiagnosticContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/validations")
public class ValidationController {
    private final DocumentInspectionService inspection;
    private final SignatureValidationService validation;
    private final ValidationDiagnosticContext diagnostics;
    public ValidationController(DocumentInspectionService inspection, SignatureValidationService validation,
                                ValidationDiagnosticContext diagnostics) {
        this.inspection = inspection; this.validation = validation; this.diagnostics = diagnostics;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Validate a PDF document",
        description = "Accepts one PDF up to 25 MB. PDF inspection, embedded PAdES extraction, and cryptographic integrity validation use EU DSS; trust and registry conclusions remain unconfigured or prototype-backed.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Normalized validation result"),
            @ApiResponse(responseCode = "400", description = "Missing document", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "413", description = "File exceeds 25 MB", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported file type", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "422", description = "Invalid or encrypted PDF", content = @Content(schema = @Schema(implementation = ApiError.class)))
        })
    public DocumentValidationResponse validate(
        @RequestPart(value = "document", required = false) MultipartFile document,
        @RequestParam(defaultValue = "true") boolean includeTechnicalDetails,
        @RequestParam(defaultValue = "en") String locale) {
        String filename = DocumentInspectionService.sanitize(document == null ? null : document.getOriginalFilename());
        diagnostics.begin(filename);
        diagnostics.stage(DssProcessingStage.DOCUMENT_INSPECTION);
        try {
            var inspected = inspection.inspect(document);
            return validation.validate(inspected.bytes(), inspected.metadata(), new ValidationOptions(includeTechnicalDetails, locale));
        } catch (ApiException | DssValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DssValidationException("SIGNATURE_VALIDATION_FAILED", DssProcessingStage.DOCUMENT_INSPECTION, exception);
        }
    }
}

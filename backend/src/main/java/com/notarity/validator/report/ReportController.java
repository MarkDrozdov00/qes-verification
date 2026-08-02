package com.notarity.validator.report;

import com.notarity.validator.document.DocumentInspectionService;
import com.notarity.validator.validation.SignatureValidationService;
import com.notarity.validator.validation.ValidationOptions;
import com.notarity.validator.validation.dss.ValidationDiagnosticContext;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private final DocumentInspectionService inspection;
    private final SignatureValidationService validation;
    private final ValidationReportMapper mapper;
    private final PdfReportService reports;
    private final ValidationDiagnosticContext diagnostics;
    public ReportController(DocumentInspectionService inspection, SignatureValidationService validation,
                            ValidationReportMapper mapper, PdfReportService reports,
                            ValidationDiagnosticContext diagnostics) {
        this.inspection = inspection; this.validation = validation; this.mapper = mapper;
        this.reports = reports; this.diagnostics = diagnostics;
    }

    @PostMapping(value = "/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Generate a PDF verification report", description = "Revalidates the uploaded PDF and renders the resulting normalized validation response as a professional, non-persistent PDF report.")
    public ResponseEntity<byte[]> pdf(@RequestPart(value = "document", required = false) MultipartFile document) {
        String filename = DocumentInspectionService.sanitize(document == null ? null : document.getOriginalFilename());
        diagnostics.begin(filename);
        var inspected = inspection.inspect(document);
        var result = validation.validate(inspected.bytes(), inspected.metadata(), new ValidationOptions(true, "en"));
        byte[] report = reports.generate(mapper.map(result));
        String base = filename.toLowerCase().endsWith(".pdf") ? filename.substring(0, filename.length() - 4) : filename;
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(base + "-verification-report.pdf").build().toString())
            .contentType(MediaType.APPLICATION_PDF).contentLength(report.length).body(report);
    }
}

package com.notarity.validator.report;

import com.notarity.validator.api.ApiModels.DocumentValidationResponse;
import java.time.Instant;

public record ReportTemplateModel(DocumentValidationResponse validation, Instant generatedAt) {}

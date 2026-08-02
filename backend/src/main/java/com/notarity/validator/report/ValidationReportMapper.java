package com.notarity.validator.report;

import com.notarity.validator.api.ApiModels.DocumentValidationResponse;
import java.time.Clock;
import org.springframework.stereotype.Component;

@Component
public class ValidationReportMapper {
    private final Clock clock;
    public ValidationReportMapper() { this(Clock.systemUTC()); }
    ValidationReportMapper(Clock clock) { this.clock = clock; }
    public ReportTemplateModel map(DocumentValidationResponse response) {
        return new ReportTemplateModel(response, clock.instant());
    }
}

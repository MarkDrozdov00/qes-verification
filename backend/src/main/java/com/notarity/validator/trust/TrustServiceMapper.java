package com.notarity.validator.trust;

import com.notarity.validator.api.ApiModels.TrustServiceResult;
import eu.europa.esig.dss.diagnostic.CertificateWrapper;
import eu.europa.esig.dss.diagnostic.TrustServiceWrapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class TrustServiceMapper {
    public TrustServiceResult map(CertificateWrapper certificate, Instant relevantTime) {
        if (certificate == null || certificate.getTrustServices() == null) return null;
        TrustServiceWrapper service = certificate.getTrustServices().stream()
            .filter(item -> activeAt(item, relevantTime)).findFirst()
            .orElseGet(() -> certificate.getTrustServices().stream().findFirst().orElse(null));
        if (service == null) return null;
        return new TrustServiceResult(first(service.getTspNames()), first(service.getServiceNames()),
            service.getCountryCode(), service.getType(), service.getStatus(),
            activeAt(service, relevantTime) ? Boolean.TRUE : null,
            service.getTrustedList() == null ? null : service.getTrustedList().getCountryCode());
    }

    private boolean activeAt(TrustServiceWrapper service, Instant time) {
        if (time == null) return false;
        Instant start = service.getStartDate() == null ? null : service.getStartDate().toInstant();
        Instant end = service.getEndDate() == null ? null : service.getEndDate().toInstant();
        return (start == null || !time.isBefore(start)) && (end == null || time.isBefore(end));
    }
    private String first(List<String> values) {
        return values == null ? null : values.stream().filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
    }
}

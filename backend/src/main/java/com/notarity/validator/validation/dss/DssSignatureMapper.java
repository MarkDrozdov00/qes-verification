package com.notarity.validator.validation.dss;

import com.notarity.validator.api.ApiModels.*;
import eu.europa.esig.dss.diagnostic.SignatureWrapper;
import eu.europa.esig.dss.diagnostic.TimestampWrapper;
import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.enumerations.SubIndication;
import eu.europa.esig.dss.simplereport.SimpleReport;
import com.notarity.validator.trust.QualificationMapper;
import com.notarity.validator.trust.TrustServiceMapper;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class DssSignatureMapper {
    private final DssCertificateMapper certificates;
    private final DssTimestampMapper timestamps;
    private final QualificationMapper qualifications;
    private final TrustServiceMapper trustServices;

    @Autowired
    public DssSignatureMapper(DssCertificateMapper certificates, DssTimestampMapper timestamps,
                              QualificationMapper qualifications, TrustServiceMapper trustServices) {
        this.certificates = certificates;
        this.timestamps = timestamps;
        this.qualifications = qualifications;
        this.trustServices = trustServices;
    }

    DssSignatureMapper(DssCertificateMapper certificates, DssTimestampMapper timestamps) {
        this(certificates, timestamps, new QualificationMapper(), new TrustServiceMapper());
    }

    public MappedSignature map(SignatureWrapper source, SimpleReport report, int index, Instant validationTime) {
        String id = source.getId();
        Indication indication = reportValue(() -> report == null ? null : report.getIndication(id));
        SubIndication subIndication = reportValue(() -> report == null ? null : report.getSubIndication(id));
        boolean byteRangeValid = source.isSignatureByteRangeValid();
        boolean signatureValueValid = source.isSignatureValid();
        boolean contentIntact = source.isSignatureIntact() && byteRangeValid;
        boolean suspiciousRevision = source.arePdfModificationsDetected();
        List<ValidationMessage> messages = new ArrayList<>();
        if (!byteRangeValid) messages.add(error("PDF_SIGNATURE_BYTE_RANGE_INVALID", "Invalid PDF signature byte range",
            "DSS determined that the PDF signature byte range is invalid.", id, indication, subIndication));
        if (!source.isSignatureIntact()) messages.add(error("DOCUMENT_INTEGRITY_INVALID", "Signed content integrity failed",
            "DSS determined that the signed content no longer matches the embedded signature.", id, indication, subIndication));
        if (!signatureValueValid) messages.add(error("SIGNATURE_VALUE_INVALID", "Signature value invalid",
            "The embedded cryptographic signature value could not be verified.", id, indication, subIndication));
        if (suspiciousRevision) {
            messages.add(warning("PDF_INCREMENTAL_UPDATE_DETECTED", "Later PDF revision detected",
                "DSS detected changes in a later PDF revision. Review whether those changes are permitted.", id, indication, subIndication));
            messages.add(warning("DOCUMENT_HAS_LATER_REVISION", "Document has a later revision",
                "This signature may cover an earlier PDF revision rather than the complete current document.", id, indication, subIndication));
            messages.add(warning("SIGNATURE_DOES_NOT_COVER_CURRENT_DOCUMENT", "Signature may not cover the current document",
                "DSS modification analysis found later changes after this signature revision.", id, indication, subIndication));
        }
        if (subIndication != null) messages.add(new ValidationMessage("DSS_" + subIndication.name(), MessageSeverity.INFO,
            "DSS validation detail", "DSS reported " + subIndication.name().replace('_', ' ').toLowerCase() + ".",
            id, technical(indication, subIndication)));

        SignatureStatus status;
        TernaryStatus integrity;
        TernaryStatus signatureValue;
        if (!byteRangeValid || !contentIntact || !signatureValueValid || suspiciousRevision) {
            status = SignatureStatus.INVALID;
            integrity = TernaryStatus.INVALID;
            signatureValue = signatureValueValid ? TernaryStatus.VALID : TernaryStatus.INVALID;
        } else if (indication == Indication.INDETERMINATE) {
            status = SignatureStatus.INDETERMINATE;
            integrity = TernaryStatus.VALID;
            signatureValue = TernaryStatus.VALID;
        } else {
            status = indication == Indication.TOTAL_PASSED ? SignatureStatus.VALID : SignatureStatus.VALID_WITH_WARNINGS;
            integrity = TernaryStatus.VALID;
            signatureValue = TernaryStatus.VALID;
        }

        List<TimestampWrapper> timestampSources = source.getTimestampList() == null
            ? List.of() : source.getTimestampList().stream().filter(java.util.Objects::nonNull).toList();
        var mappedTimestamps = timestampSources.stream().map(timestamps::map).toList();
        TimestampWrapper trusted = timestampSources.stream().filter(timestamps::isTrustedSignatureTimestamp)
            .filter(timestamp -> timestamp.getProductionTime() != null)
            .min(Comparator.comparing(TimestampWrapper::getProductionTime)).orElse(null);
        Instant claimed = DssCertificateMapper.instant(source.getClaimedSigningTime());
        Instant trustedTime = trusted == null ? null : DssCertificateMapper.instant(trusted.getProductionTime());
        Instant best = trustedTime != null ? trustedTime : claimed;
        TimeSource timeSource = trustedTime != null ? TimeSource.TRUSTED_TIMESTAMP
            : claimed != null ? TimeSource.CLAIMED_SIGNING_TIME : TimeSource.VALIDATION_TIME;
        Instant relevantTime = trustedTime != null ? trustedTime : validationTime;

        var signerCertificate = source.getSigningCertificate();
        String format = source.getSignatureFormat() == null ? "PAdES" : source.getSignatureFormat().name();
        var qualification = qualifications.map(reportValue(() -> report == null ? null : report.getSignatureQualification(id)));
        SignatureResult result = new SignatureResult(id, index, status,
            new ValidationSummary(title(status), description(status), messages),
            certificates.signer(signerCertificate), new SigningTime(claimed, trustedTime, best, timeSource),
            new SignatureEvidence("PAdES", format, baseline(format), qualification,
                qualifications.label(qualification), integrity, signatureValue),
            certificates.map(signerCertificate, validationTime, relevantTime),
            trustServices.map(signerCertificate, relevantTime), mappedTimestamps, null, messages);
        return new MappedSignature(result, indication == null ? null : indication.name(),
            subIndication == null ? null : subIndication.name());
    }

    private String baseline(String value) {
        if (value == null) return "UNKNOWN";
        if (value.endsWith("_BASELINE_LTA")) return "B-LTA";
        if (value.endsWith("_BASELINE_LT")) return "B-LT";
        if (value.endsWith("_BASELINE_T")) return "B-T";
        if (value.endsWith("_BASELINE_B")) return "B-B";
        return "UNKNOWN";
    }
    private String title(SignatureStatus status) {
        return switch (status) {
            case INVALID -> "Signature integrity failed";
            case INDETERMINATE -> "Signature result is indeterminate";
            default -> "Cryptographic integrity verified";
        };
    }
    private String description(SignatureStatus status) {
        return switch (status) {
            case INVALID -> "DSS found an invalid signature value, signed-content digest, PDF byte range, or suspicious later revision.";
            case INDETERMINATE -> "DSS found the cryptographic material but could not reach a reliable integrity conclusion.";
            default -> "The signature value and signed PDF content are intact. Certificate trust and eIDAS qualification are not evaluated in this phase.";
        };
    }
    private ValidationMessage error(String code, String title, String message, String id, Indication i, SubIndication s) {
        return new ValidationMessage(code, MessageSeverity.ERROR, title, message, id, technical(i, s));
    }
    private ValidationMessage warning(String code, String title, String message, String id, Indication i, SubIndication s) {
        return new ValidationMessage(code, MessageSeverity.WARNING, title, message, id, technical(i, s));
    }
    private String technical(Indication indication, SubIndication subIndication) {
        return "DSS indication=" + (indication == null ? "UNKNOWN" : indication.name())
            + "; subIndication=" + (subIndication == null ? "NONE" : subIndication.name());
    }
    private <T> T reportValue(java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
    public record MappedSignature(SignatureResult result, String indication, String subIndication) {}
}

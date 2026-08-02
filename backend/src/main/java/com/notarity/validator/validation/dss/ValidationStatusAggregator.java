package com.notarity.validator.validation.dss;

import com.notarity.validator.api.ApiModels.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ValidationStatusAggregator {
    public CheckStatus cryptographicIntegrity(List<SignatureResult> signatures) {
        if (signatures.isEmpty()) return CheckStatus.NOT_APPLICABLE;
        if (signatures.stream().anyMatch(this::cryptographicallyInvalid)) return CheckStatus.INVALID;
        if (signatures.stream().anyMatch(this::cryptographicallyIndeterminate)) return CheckStatus.INDETERMINATE;
        return CheckStatus.VALID;
    }

    public OverallValidationStatus overall(List<SignatureResult> signatures, boolean trustedListsAvailable) {
        if (signatures.isEmpty()) return OverallValidationStatus.UNSIGNED;
        CheckStatus cryptographic = cryptographicIntegrity(signatures);
        if (cryptographic == CheckStatus.INVALID) return OverallValidationStatus.INVALID;
        if (signatures.stream().anyMatch(this::conclusiveCertificateFailure)) {
            return OverallValidationStatus.INVALID;
        }
        if (trustedListsAvailable
            && signatures.stream().anyMatch(signature -> signature.certificate().chainStatus() == ChainStatus.UNTRUSTED)) {
            return OverallValidationStatus.INVALID;
        }
        if (cryptographic == CheckStatus.INDETERMINATE || !trustedListsAvailable) {
            return OverallValidationStatus.INDETERMINATE;
        }
        if (signatures.stream().anyMatch(this::materialTrustGap)) {
            return OverallValidationStatus.INDETERMINATE;
        }
        boolean warnings = signatures.stream().anyMatch(signature ->
            signature.status() == SignatureStatus.VALID_WITH_WARNINGS
                || !signature.warnings().isEmpty());
        return warnings ? OverallValidationStatus.VERIFIED_WITH_WARNINGS : OverallValidationStatus.VERIFIED;
    }

    private boolean cryptographicallyInvalid(SignatureResult signature) {
        return signature.signature().signatureValue() == TernaryStatus.INVALID
            || signature.signature().integrity() == TernaryStatus.INVALID;
    }
    private boolean cryptographicallyIndeterminate(SignatureResult signature) {
        return signature.signature().signatureValue() == TernaryStatus.INDETERMINATE
            || signature.signature().integrity() == TernaryStatus.INDETERMINATE;
    }
    private boolean conclusiveCertificateFailure(SignatureResult signature) {
        return signature.certificate().revocationStatus() == RevocationStatus.REVOKED
            || signature.certificate().statusAtSigningTime() == CertificateStatus.REVOKED
            || signature.certificate().statusAtSigningTime() == CertificateStatus.EXPIRED
            || signature.certificate().statusAtSigningTime() == CertificateStatus.NOT_YET_VALID;
    }
    private boolean materialTrustGap(SignatureResult signature) {
        return signature.status() == SignatureStatus.INDETERMINATE
            || signature.certificate().chainStatus() == ChainStatus.INCOMPLETE
            || signature.certificate().chainStatus() == ChainStatus.UNKNOWN
            || signature.certificate().revocationStatus() == RevocationStatus.UNKNOWN
            || signature.certificate().revocationStatus() == RevocationStatus.NOT_CHECKED
            || signature.signature().qualification() == Qualification.UNKNOWN;
    }
}

package com.notarity.validator.validation.dss;

import com.notarity.validator.api.ApiModels.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ValidationStatusAggregatorTest {
    private final ValidationStatusAggregator aggregator = new ValidationStatusAggregator();

    @Test void intactSignaturesWithUnavailableTrustedListsAreIndeterminateButCryptographicallyValid() {
        var signature = signature(TernaryStatus.VALID, TernaryStatus.VALID,
            ChainStatus.UNKNOWN, RevocationStatus.UNKNOWN, CertificateStatus.UNKNOWN);
        assertThat(aggregator.cryptographicIntegrity(List.of(signature))).isEqualTo(CheckStatus.VALID);
        assertThat(aggregator.overall(List.of(signature), false)).isEqualTo(OverallValidationStatus.INDETERMINATE);
    }
    @Test void invalidSignatureValueIsInvalid() {
        var signature = signature(TernaryStatus.INVALID, TernaryStatus.VALID,
            ChainStatus.UNKNOWN, RevocationStatus.UNKNOWN, CertificateStatus.UNKNOWN);
        assertThat(aggregator.cryptographicIntegrity(List.of(signature))).isEqualTo(CheckStatus.INVALID);
        assertThat(aggregator.overall(List.of(signature), false)).isEqualTo(OverallValidationStatus.INVALID);
    }
    @Test void invalidSignedContentIsInvalid() {
        var signature = signature(TernaryStatus.VALID, TernaryStatus.INVALID,
            ChainStatus.UNKNOWN, RevocationStatus.UNKNOWN, CertificateStatus.UNKNOWN);
        assertThat(aggregator.overall(List.of(signature), true)).isEqualTo(OverallValidationStatus.INVALID);
    }
    @Test void incompleteChainAndUnknownRevocationAreNotInvalid() {
        var signature = signature(TernaryStatus.VALID, TernaryStatus.VALID,
            ChainStatus.INCOMPLETE, RevocationStatus.UNKNOWN, CertificateStatus.UNKNOWN);
        assertThat(aggregator.overall(List.of(signature), true)).isEqualTo(OverallValidationStatus.INDETERMINATE);
    }
    @Test void conclusivelyRevokedCertificateIsInvalid() {
        var signature = signature(TernaryStatus.VALID, TernaryStatus.VALID,
            ChainStatus.TRUSTED, RevocationStatus.REVOKED, CertificateStatus.REVOKED);
        assertThat(aggregator.overall(List.of(signature), true)).isEqualTo(OverallValidationStatus.INVALID);
    }
    @Test void untrustedChainOnlyInvalidUnderAvailableAuthoritativePolicy() {
        var signature = signature(TernaryStatus.VALID, TernaryStatus.VALID,
            ChainStatus.UNTRUSTED, RevocationStatus.UNKNOWN, CertificateStatus.UNKNOWN);
        assertThat(aggregator.overall(List.of(signature), false)).isEqualTo(OverallValidationStatus.INDETERMINATE);
        assertThat(aggregator.overall(List.of(signature), true)).isEqualTo(OverallValidationStatus.INVALID);
    }

    private SignatureResult signature(TernaryStatus value, TernaryStatus integrity, ChainStatus chain,
                                      RevocationStatus revocation, CertificateStatus certificateStatus) {
        SignatureResult signature = mock(SignatureResult.class);
        CertificateResult certificate = mock(CertificateResult.class);
        when(signature.signature()).thenReturn(new SignatureEvidence("PAdES", "PAdES_BASELINE_B", "B-B",
            Qualification.UNKNOWN, "Unable to determine", integrity, value));
        when(signature.certificate()).thenReturn(certificate);
        when(signature.status()).thenReturn(SignatureStatus.INDETERMINATE);
        when(signature.warnings()).thenReturn(List.of());
        when(certificate.chainStatus()).thenReturn(chain);
        when(certificate.revocationStatus()).thenReturn(revocation);
        when(certificate.statusAtSigningTime()).thenReturn(certificateStatus);
        return signature;
    }
}

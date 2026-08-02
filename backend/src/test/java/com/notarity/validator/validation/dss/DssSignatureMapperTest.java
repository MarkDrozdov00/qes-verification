package com.notarity.validator.validation.dss;

import eu.europa.esig.dss.diagnostic.SignatureWrapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DssSignatureMapperTest {

    @Test
    void missingOptionalDssMetadataDoesNotCrashMapping() {
        var source = mock(SignatureWrapper.class);
        when(source.getId()).thenReturn("signature-1");
        when(source.getTimestampList()).thenReturn(null);
        when(source.getSigningCertificate()).thenReturn(null);
        when(source.getClaimedSigningTime()).thenReturn(null);
        when(source.getSignatureFormat()).thenReturn(null);

        var mapper = new DssSignatureMapper(new DssCertificateMapper(new SignerDisplayNameNormalizer()), new DssTimestampMapper());
        var mapped = mapper.map(source, null, 1, Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(mapped.result().signer().displayName()).isEqualTo("Unknown signer");
        assertThat(mapped.result().timestamps()).isEmpty();
        assertThat(mapped.result().signingTime().claimedTime()).isNull();
        assertThat(mapped.result().signingTime().trustedTime()).isNull();
        assertThat(mapped.result().signature().baselineLevel()).isEqualTo("UNKNOWN");
        assertThat(mapped.indication()).isNull();
        assertThat(mapped.subIndication()).isNull();
    }
}

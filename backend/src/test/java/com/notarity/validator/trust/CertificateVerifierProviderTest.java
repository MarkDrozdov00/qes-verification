package com.notarity.validator.trust;

import eu.europa.esig.dss.service.http.commons.CommonsDataLoader;
import eu.europa.esig.dss.enumerations.CertificateSourceType;
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CertificateVerifierProviderTest {
    @Test
    void partialSourceIsNeverPublished() {
        CertificateVerifierProvider provider = provider();
        var original = provider.snapshot();
        TrustedListsCertificateSource partial = mock(TrustedListsCertificateSource.class);
        when(partial.getNumberOfTrustedEntityKeys()).thenReturn(0);

        assertThatThrownBy(() -> provider.promote(partial)).isInstanceOf(IllegalArgumentException.class);
        assertThat(provider.snapshot()).isSameAs(original);
        assertThat(provider.snapshot().trustEnabled()).isFalse();
    }

    @Test
    void completeSourceIsAtomicallyPromotedAndFailedRefreshPreservesIt() {
        CertificateVerifierProvider provider = provider();
        TrustedListsCertificateSource complete = mock(TrustedListsCertificateSource.class);
        when(complete.getNumberOfTrustedEntityKeys()).thenReturn(12);
        when(complete.getCertificateSourceType()).thenReturn(CertificateSourceType.TRUSTED_LIST);
        provider.promote(complete);
        var published = provider.snapshot();

        TrustedListsCertificateSource failedCandidate = mock(TrustedListsCertificateSource.class);
        when(failedCandidate.getNumberOfTrustedEntityKeys()).thenReturn(0);
        assertThatThrownBy(() -> provider.promote(failedCandidate)).isInstanceOf(IllegalArgumentException.class);

        assertThat(provider.snapshot()).isSameAs(published);
        assertThat(published.trustEnabled()).isTrue();
        assertThat(published.trustedEntities()).isEqualTo(12);
        assertThat(published.verifier().getTrustedCertSources().getSources()).contains(complete);
    }

    private CertificateVerifierProvider provider() {
        TrustProperties properties = new TrustProperties();
        properties.setOnlineCertificateFetching(true);
        return new CertificateVerifierProvider(properties, new CommonsDataLoader());
    }
}

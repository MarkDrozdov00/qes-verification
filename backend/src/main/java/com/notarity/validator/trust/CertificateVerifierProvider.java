package com.notarity.validator.trust;

import eu.europa.esig.dss.service.crl.OnlineCRLSource;
import eu.europa.esig.dss.service.http.commons.CommonsDataLoader;
import eu.europa.esig.dss.service.ocsp.OnlineOCSPSource;
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.x509.aia.DefaultAIASource;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/** Publishes complete verifier instances; published instances are never mutated. */
@Component
public class CertificateVerifierProvider {
    private final TrustProperties properties;
    private final CommonsDataLoader networkLoader;
    private final VerifierSnapshot cryptographicOnly;
    private final AtomicReference<VerifierSnapshot> current;

    public CertificateVerifierProvider(TrustProperties properties, CommonsDataLoader networkLoader) {
        this.properties = properties;
        this.networkLoader = networkLoader;
        this.cryptographicOnly = new VerifierSnapshot(buildCryptographicOnly(), false, 0);
        this.current = new AtomicReference<>(cryptographicOnly);
    }

    public VerifierSnapshot snapshot() {
        return current.get();
    }

    public VerifierSnapshot cryptographicOnly() {
        return cryptographicOnly;
    }

    public void promote(TrustedListsCertificateSource authenticatedSource) {
        int trustedEntities = authenticatedSource == null ? 0 : authenticatedSource.getNumberOfTrustedEntityKeys();
        if (trustedEntities < 1) {
            throw new IllegalArgumentException("An empty Trusted List source cannot be published");
        }
        current.set(new VerifierSnapshot(buildTrustEnabled(authenticatedSource), true, trustedEntities));
    }

    private CommonCertificateVerifier buildCryptographicOnly() {
        CommonCertificateVerifier verifier = new CommonCertificateVerifier(true);
        verifier.setCheckRevocationForUntrustedChains(false);
        return verifier;
    }

    private CommonCertificateVerifier buildTrustEnabled(TrustedListsCertificateSource trustedSource) {
        CommonCertificateVerifier verifier = new CommonCertificateVerifier(true);
        verifier.setTrustedCertSources(trustedSource);
        verifier.setCheckRevocationForUntrustedChains(false);
        if (properties.isOnlineCertificateFetching()) {
            verifier.setAIASource(new DefaultAIASource(networkLoader));
        }
        if (properties.getRevocation().isEnabled()) {
            if (properties.getRevocation().isOcspEnabled()) {
                verifier.setOcspSource(new OnlineOCSPSource(networkLoader));
            }
            if (properties.getRevocation().isCrlEnabled()) {
                verifier.setCrlSource(new OnlineCRLSource(networkLoader));
            }
        }
        return verifier;
    }

    public record VerifierSnapshot(CertificateVerifier verifier, boolean trustEnabled, int trustedEntities) {}
}

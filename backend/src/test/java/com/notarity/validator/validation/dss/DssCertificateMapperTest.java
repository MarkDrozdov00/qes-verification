package com.notarity.validator.validation.dss;

import eu.europa.esig.dss.diagnostic.CertificateWrapper;
import org.junit.jupiter.api.Test;

import static com.notarity.validator.api.ApiModels.SignerEntityType.ORGANIZATION;
import static com.notarity.validator.api.ApiModels.SignerEntityType.PERSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DssCertificateMapperTest {
    private final DssCertificateMapper mapper = new DssCertificateMapper(new SignerDisplayNameNormalizer());

    @Test
    void personAttributesDriveEntityTypeAndReadableDisplayName() {
        var certificate = mock(CertificateWrapper.class);
        when(certificate.getGivenName()).thenReturn("SEBASTIAN");
        when(certificate.getSurname()).thenReturn("WODNIANSKY-WILDENFELD");
        when(certificate.getOrganizationName()).thenReturn("notarity GmbH");

        var signer = mapper.signer(certificate);

        assertThat(signer.entityType()).isEqualTo(PERSON);
        assertThat(signer.displayName()).isEqualTo("Sebastian Wodniansky-Wildenfeld");
    }

    @Test
    void organizationAttributesProduceConservativeSealIdentity() {
        var certificate = mock(CertificateWrapper.class);
        when(certificate.getOrganizationName()).thenReturn("notarity GmbH");

        var signer = mapper.signer(certificate);

        assertThat(signer.entityType()).isEqualTo(ORGANIZATION);
        assertThat(signer.displayName()).isEqualTo("Notarity GmbH");
    }
}

package com.notarity.validator.trust;

import com.notarity.validator.api.ApiModels.Qualification;
import eu.europa.esig.dss.enumerations.SignatureQualification;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QualificationMapperTest {
    private final QualificationMapper mapper = new QualificationMapper();

    @Test void mapsOnlyAuthoritativeDssQualificationConclusions() {
        assertThat(mapper.map(SignatureQualification.QESIG)).isEqualTo(Qualification.QES);
        assertThat(mapper.map(SignatureQualification.QESEAL)).isEqualTo(Qualification.QESEAL);
        assertThat(mapper.map(SignatureQualification.ADESIG_QC)).isEqualTo(Qualification.ADES_WITH_QC);
        assertThat(mapper.map(SignatureQualification.ADESEAL_QC)).isEqualTo(Qualification.ADESEAL_WITH_QC);
    }

    @Test void missingDssQualificationRemainsUnknownEvenIfCertificateMetadataCouldLookQualified() {
        assertThat(mapper.map(null)).isEqualTo(Qualification.UNKNOWN);
        assertThat(mapper.map(SignatureQualification.UNKNOWN_QC_QSCD)).isEqualTo(Qualification.UNKNOWN);
    }
}

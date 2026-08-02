package com.notarity.validator.trust;

import com.notarity.validator.api.ApiModels.Qualification;
import eu.europa.esig.dss.enumerations.SignatureQualification;
import org.springframework.stereotype.Component;

@Component
public class QualificationMapper {
    public Qualification map(SignatureQualification value) {
        if (value == null) return Qualification.UNKNOWN;
        return switch (value) {
            case QESIG -> Qualification.QES;
            case QESEAL -> Qualification.QESEAL;
            case ADESIG_QC -> Qualification.ADES_WITH_QC;
            case ADESEAL_QC -> Qualification.ADESEAL_WITH_QC;
            case ADESIG -> Qualification.ADES;
            case ADESEAL -> Qualification.ADESEAL;
            case NOT_ADES, NOT_ADES_QC, NOT_ADES_QC_QSCD -> Qualification.ELECTRONIC_SIGNATURE;
            default -> Qualification.UNKNOWN;
        };
    }

    public String label(Qualification value) {
        return switch (value) {
            case QES -> "Qualified electronic signature";
            case QESEAL -> "Qualified electronic seal";
            case ADES_WITH_QC -> "Advanced electronic signature with qualified certificate";
            case ADESEAL_WITH_QC -> "Advanced electronic seal with qualified certificate";
            case ADES -> "Advanced electronic signature";
            case ADESEAL -> "Advanced electronic seal";
            case ELECTRONIC_SIGNATURE -> "Basic electronic signature";
            case ELECTRONIC_SEAL -> "Basic electronic seal";
            case UNKNOWN -> "Unable to determine";
        };
    }
}

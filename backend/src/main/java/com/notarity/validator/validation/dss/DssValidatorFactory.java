package com.notarity.validator.validation.dss;

import com.notarity.validator.api.ApiException;
import eu.europa.esig.dss.enumerations.MimeTypeEnum;
import eu.europa.esig.dss.enumerations.TokenExtractionStrategy;
import eu.europa.esig.dss.enumerations.ValidationLevel;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.pades.validation.PDFDocumentValidator;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import com.notarity.validator.trust.CertificateVerifierProvider;
import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.validation.mode", havingValue = "dss", matchIfMissing = true)
public class DssValidatorFactory {
    private final CertificateVerifierProvider certificateVerifiers;

    public DssValidatorFactory(CertificateVerifierProvider certificateVerifiers) {
        this.certificateVerifiers = certificateVerifiers;
    }

    public ValidatorSnapshot create(byte[] bytes, String filename) {
        var snapshot = certificateVerifiers.snapshot();
        return new ValidatorSnapshot(create(bytes, filename, snapshot.verifier()),
            snapshot.trustEnabled(), snapshot.trustedEntities());
    }

    public SignedDocumentValidator createCryptographicOnly(byte[] bytes, String filename) {
        return create(bytes, filename, certificateVerifiers.cryptographicOnly().verifier());
    }

    private SignedDocumentValidator create(byte[] bytes, String filename, CertificateVerifier certificateVerifier) {
        var document = new InMemoryDocument(bytes, filename, MimeTypeEnum.PDF);
        SignedDocumentValidator validator;
        try {
            validator = SignedDocumentValidator.fromDocument(document);
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_PDF",
                "DSS could not recognize the uploaded PDF as a supported signed document.");
        }
        if (!(validator instanceof PDFDocumentValidator)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE",
                "Only PDF documents with supported PAdES signatures are accepted.");
        }
        validator.setCertificateVerifier(certificateVerifier);
        validator.setValidationLevel(ValidationLevel.LONG_TERM_DATA);
        validator.setTokenExtractionStrategy(TokenExtractionStrategy.EXTRACT_CERTIFICATES_AND_TIMESTAMPS);
        validator.setEnableEtsiValidationReport(false);
        return validator;
    }

    public CertificateVerifier configuredCertificateVerifier() {
        return certificateVerifiers.snapshot().verifier();
    }

    public record ValidatorSnapshot(SignedDocumentValidator validator, boolean trustEnabled,
                                    int trustedEntities) {}
}

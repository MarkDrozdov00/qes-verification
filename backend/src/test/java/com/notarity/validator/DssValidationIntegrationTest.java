package com.notarity.validator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import com.notarity.validator.validation.dss.DssValidatorFactory;
import com.notarity.validator.trust.CertificateVerifierProvider;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.validation.mode=dss", "app.trust.enabled=false", "app.trust.refresh-on-startup=false"})
@AutoConfigureMockMvc
class DssValidationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired CertificateVerifierProvider certificateVerifiers;
    @Autowired DssValidatorFactory validatorFactory;

    @Test void offlineTestProfileHasNoOnlineSources() {
        var certificateVerifier = certificateVerifiers.snapshot().verifier();
        org.assertj.core.api.Assertions.assertThat(certificateVerifier.getAIASource()).isNull();
        org.assertj.core.api.Assertions.assertThat(certificateVerifier.getCrlSource()).isNull();
        org.assertj.core.api.Assertions.assertThat(certificateVerifier.getOcspSource()).isNull();
    }

    @Test void unavailableTrustUsesStableCryptographicOnlyVerifier() {
        var certificateVerifier = certificateVerifiers.snapshot().verifier();
        org.assertj.core.api.Assertions.assertThat(validatorFactory.configuredCertificateVerifier())
            .isSameAs(certificateVerifier);
        org.assertj.core.api.Assertions.assertThat(certificateVerifiers.snapshot().trustEnabled()).isFalse();
        org.assertj.core.api.Assertions.assertThat(certificateVerifier.getTrustedCertSources().getSources()).isEmpty();
    }

    @Test void unsignedPdfReturnsUnsigned() throws Exception {
        mvc.perform(multipart("/api/v1/validations").file(pdf("unsigned.pdf", SignedPdfFixtures.unsignedPdf())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.overallStatus").value("UNSIGNED"))
            .andExpect(jsonPath("$.signatures").isEmpty()).andExpect(jsonPath("$.document.signed").value(false));
    }
    @Test void realSignatureAndCertificateAreMapped() throws Exception {
        mvc.perform(multipart("/api/v1/validations").file(pdf("signed.pdf", SignedPdfFixtures.signedPdf())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.overallStatus").value("INDETERMINATE"))
            .andExpect(jsonPath("$.signatures.length()").value(1))
            .andExpect(jsonPath("$.signatures[0].signer.displayName").value("Maria Muster"))
            .andExpect(jsonPath("$.signatures[0].certificate.sha256Fingerprint").value(SignedPdfFixtures.certificateSha256()))
            .andExpect(jsonPath("$.signatures[0].signature.signatureValue").value("VALID"))
            .andExpect(jsonPath("$.signatures[0].signature.integrity").value("VALID"))
            .andExpect(jsonPath("$.trustSummary.documentIntegrity.status").value("VALID"))
            .andExpect(jsonPath("$.trustSummary.documentIntegrity.value").value("Cryptographically intact"))
            .andExpect(jsonPath("$.trustSummary.embeddedSignatures.status").value("VALID"))
            .andExpect(jsonPath("$.trustSummary.signatureQualification.status").value("INDETERMINATE"))
            .andExpect(jsonPath("$.trustSummary.certificateTrust.status").value("INDETERMINATE"))
            .andExpect(jsonPath("$.signatures[0].certificate.chainStatus",
                anyOf(is("UNKNOWN"), is("INCOMPLETE"))))
            .andExpect(jsonPath("$.summary.warnings[*].code", hasItem("TRUST_INFRASTRUCTURE_UNAVAILABLE")))
            .andExpect(jsonPath("$.notaryCheck.status").value("NO_MATCH"))
            .andExpect(jsonPath("$.notarityCheck.status").value("NO_RECORD"));
    }
    @Test void multipleSignaturesAreDetected() throws Exception {
        mvc.perform(multipart("/api/v1/validations").file(pdf("multi-signed.pdf", SignedPdfFixtures.multiSignedPdf())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.signatures.length()").value(2))
            .andExpect(jsonPath("$.technicalEvidence.detectedSignatureCount").value(2));
    }
    @Test void claimedTimeIsNotPromotedToTrustedTime() throws Exception {
        mvc.perform(multipart("/api/v1/validations").file(pdf("claimed-time.pdf", SignedPdfFixtures.signedPdf())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.signatures[0].signingTime.claimedTime").exists())
            .andExpect(jsonPath("$.signatures[0].signingTime.trustedTime").doesNotExist())
            .andExpect(jsonPath("$.signatures[0].signingTime.source").value("CLAIMED_SIGNING_TIME"));
    }
    @Test void modifiedSignedDocumentIsNotReportedAsVerified() throws Exception {
        mvc.perform(multipart("/api/v1/validations").file(pdf("modified.pdf", SignedPdfFixtures.modifiedSignedPdf())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.overallStatus", anyOf(is("INVALID"), is("INDETERMINATE"))));
    }
    @Test void corruptedSignatureIsInvalid() throws Exception {
        mvc.perform(multipart("/api/v1/validations").file(pdf("corrupted.pdf", SignedPdfFixtures.corruptedSignaturePdf())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.overallStatus").value("INVALID"));
    }
    @Test void malformedPdfStillUsesStructuredError() throws Exception {
        mvc.perform(multipart("/api/v1/validations").file(pdf("broken.pdf", "%PDF-not-valid".getBytes())))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_PDF"));
    }

    private MockMultipartFile pdf(String name, byte[] bytes) {
        return new MockMultipartFile("document", name, "application/pdf", bytes);
    }
}

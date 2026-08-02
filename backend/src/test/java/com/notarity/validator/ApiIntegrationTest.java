package com.notarity.validator;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.validation.mode=fixture", "app.trust.enabled=false", "app.trust.refresh-on-startup=false"})
@AutoConfigureMockMvc
class ApiIntegrationTest {
    @Autowired MockMvc mvc;

    @Test void health() throws Exception {
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components.dssValidationEngine").value("NOT_CONFIGURED"))
            .andExpect(jsonPath("$.components.prototypeNotarityRegistry").value("UP"))
            .andExpect(jsonPath("$.components.prototypeRegistryKeys").value("UP"));
    }
    @Test void capabilities() throws Exception {
        mvc.perform(get("/api/v1/capabilities")).andExpect(status().isOk())
            .andExpect(jsonPath("$.maximumFileSizeBytes").value(26214400))
            .andExpect(jsonPath("$.prototypeRegistryVerification").value(true))
            .andExpect(jsonPath("$.prototypeRegistryAlgorithm").value("Ed25519"))
            .andExpect(jsonPath("$.documentRetention").value("NONE"));
    }
    @Test void validPdfPreservesPagesAndReturnsVerifiedFixture() throws Exception {
        mvc.perform(multipart("/api/v1/validations").file(file("contract.pdf", pdf(2, false))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.overallStatus").value("VERIFIED"))
            .andExpect(jsonPath("$.document.pageCount").value(2)).andExpect(jsonPath("$.document.sha256").isString())
            .andExpect(jsonPath("$.signatures.length()").value(2))
            .andExpect(jsonPath("$.technicalEvidence.validationEngine").value("Fixture validation engine"));
    }
    @Test void missingFileIsStructured() throws Exception {
        mvc.perform(multipart("/api/v1/validations")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MISSING_DOCUMENT")).andExpect(jsonPath("$.path").value("/api/v1/validations"));
    }
    @Test void unsupportedFileIsRejected() throws Exception {
        var file = new MockMultipartFile("document", "note.txt", "text/plain", "hello".getBytes());
        mvc.perform(multipart("/api/v1/validations").file(file)).andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
    }
    @Test void oversizedFileIsRejected() throws Exception {
        var file = new MockMultipartFile("document", "large.pdf", "application/pdf", new byte[25 * 1024 * 1024 + 1]);
        mvc.perform(multipart("/api/v1/validations").file(file)).andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }
    @Test void fakePdfExtensionIsRejected() throws Exception {
        var file = new MockMultipartFile("document", "fake.pdf", "application/pdf", "not a pdf".getBytes());
        mvc.perform(multipart("/api/v1/validations").file(file)).andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_PDF"));
    }
    @Test void encryptedPdfIsRejected() throws Exception {
        mvc.perform(multipart("/api/v1/validations").file(file("secret.pdf", pdf(1, true))))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("ENCRYPTED_PDF"));
    }
    @Test void unsignedFixture() throws Exception {
        mvc.perform(multipart("/api/v1/validations").file(file("unsigned-agreement.pdf", pdf(1, false))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.overallStatus").value("UNSIGNED"))
            .andExpect(jsonPath("$.document.signed").value(false)).andExpect(jsonPath("$.signatures").isEmpty());
    }
    @Test void invalidFixture() throws Exception {
        mvc.perform(multipart("/api/v1/validations").file(file("tampered-contract.pdf", pdf(1, false))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.overallStatus").value("INVALID"))
            .andExpect(jsonPath("$.summary.warnings[0].code").value("DOCUMENT_INTEGRITY_INVALID"));
    }
    @Test void filenameIsSanitized() throws Exception {
        mvc.perform(multipart("/api/v1/validations").file(file("../../contract.pdf", pdf(1, false))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.document.filename").value("contract.pdf"));
    }
    @Test void pdfReportRevalidatesUploadAndDownloadsVectorPdf() throws Exception {
        mvc.perform(multipart("/api/v1/reports/pdf").file(file("contract.pdf", pdf(2, false))))
            .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("contract-verification-report.pdf")))
            .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsByteArray()).startsWith("%PDF".getBytes()));
    }
    @Test void reportRejectsInvalidPdfWithExistingStructuredError() throws Exception {
        mvc.perform(multipart("/api/v1/reports/pdf").file(new MockMultipartFile("document", "bad.pdf", "application/pdf", "bad".getBytes())))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVALID_PDF"));
    }

    private MockMultipartFile file(String name, byte[] bytes) { return new MockMultipartFile("document", name, "application/pdf", bytes); }
    private byte[] pdf(int pages, boolean encrypted) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 0; i < pages; i++) document.addPage(new PDPage());
            if (encrypted) document.protect(new StandardProtectionPolicy("owner", "secret", new AccessPermission()));
            document.save(out);
            return out.toByteArray();
        }
    }
}

package com.notarity.validator.report;

import com.notarity.validator.api.ApiModels.*;
import com.notarity.validator.document.DocumentMetadata;
import com.notarity.validator.validation.FixtureSignatureValidationService;
import com.notarity.validator.validation.ValidationOptions;
import com.notarity.validator.notary.JsonPrototypeNotaryRegistry;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PdfReportServiceTest {
    private static final String HASH = "8a1fe8ca8dbf5a35505d04bf7fa05b2b0f4b801c27b0b8d3f7a12735f3a677b7";
    private final PdfReportService reports = new PdfReportService(new JsonPrototypeNotaryRegistry(
        JsonMapper.builder().findAndAddModules().build(), "../data/notaries.json"));
    private final FixtureSignatureValidationService validation = new FixtureSignatureValidationService();

    @Test void mirrorsWebHierarchyWithoutTruncationOrMethodologyPage() throws Exception {
        byte[] pdf = reports.generate(new ReportTemplateModel(sampleResponse(), Instant.parse("2026-08-02T01:42:00Z")));
        Files.createDirectories(Path.of("build/reports"));
        Files.write(Path.of("build/reports/verification-report-sample.pdf"), pdf);

        try (var document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(document.getNumberOfPages()).isEqualTo(2);
            assertThat(text).containsSubsequence("Document information", "Trust summary", "Embedded signatures",
                "Prototype Notarity record verified", "Notary directory");
            assertThat(text).doesNotContain("Overall result", "Methodology", "Implementation Overview",
                "Technical verification details", "...", "Prototype notice");
            assertThat(text).contains(HASH);
            assertThat(text).contains("Advanced electronic seal with qualified certificate");
            assertThat(text).contains("29 Jul 2026, 14:03 UTC to 29 Jul 2027, 14:03 UTC");
            assertThat(text).contains("Sebastian Wodniansky-Wildenfeld", "Notarity GmbH", "Organization — Advanced electronic seal with qualified certificate");
            assertThat(text).contains("Prototype Notarity record verified", "3 of 3", "Audit receipt", "Signed prototype registry evidence available");
            assertThat(text).contains("No match in current snapshot", "Static snapshot — Retrieved 02 Aug 2026");
            assertThat(text).doesNotContain("Not available");
            assertThat(occurrences(text.replaceAll("\\s+", " "), "do not query Notarity production systems")).isEqualTo(1);
            assertThat(text.replaceAll("\\s+", " ")).contains("do not query Notarity production systems");
            assertThat(text).doesNotContain("\uFFFD");
            String pageOne = pageText(document, 1), pageTwo = pageText(document, 2);
            assertThat(pageOne).contains("Document integrity", "Embedded signatures", "Signature level", "Certificate trust", "Notary", "Notarity record", "Sebastian Wodniansky-Wildenfeld");
            assertThat(pageTwo).doesNotContain("SESSION_CREATED", "PARTICIPANT_SIGNED", "DOCUMENT_REGISTERED");
            assertThat(pageTwo).contains("Prototype Notarity record verified", "Notary directory");
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                String pageText = pageText(document, page);
                assertThat(pageText).contains("Page " + page + " of " + document.getNumberOfPages());
            }
        }
    }

    @Test void layoutMaintainsSafeMarginsAndSeparatedSummaryColumns() throws Exception {
        byte[] pdf = reports.generate(new ReportTemplateModel(sampleResponse(), Instant.parse("2026-08-02T01:42:00Z")));
        try (var document = Loader.loadPDF(pdf)) {
            PositionStripper stripper = new PositionStripper();
            stripper.setStartPage(1); stripper.setEndPage(1); stripper.getText(document);
            assertThat(stripper.lines).allSatisfy(line -> {
                assertThat(line.x()).isGreaterThanOrEqualTo(42f);
                assertThat(line.x() + line.width()).isLessThanOrEqualTo(553f);
            });
            PositionedText badge = stripper.firstExact("Verified");
            PositionedText heading = stripper.firstContaining("The document and its signatures are trusted");
            assertThat(heading.y() - badge.y()).isGreaterThan(24f);
            PositionedText integrityStatus = stripper.firstExact("Verified", 1);
            PositionedText integrityLabel = stripper.firstExact("Document integrity");
            PositionedText integrityValue = stripper.firstExact("Cryptographically intact");
            assertThat(integrityStatus.x()).isEqualTo(integrityLabel.x());
            assertThat(integrityLabel.x()).isEqualTo(integrityValue.x());
            PositionedText eyebrow = stripper.firstExact("SIGNATURE VALIDATOR");
            PositionedText reportTitle = stripper.firstExact("Verification Report");
            assertThat(reportTitle.y() - eyebrow.y()).isGreaterThan(20f);
            PositionedText context = stripper.firstExact("Independent document evidence");
            assertThat(context.x() + context.width()).isLessThanOrEqualTo(553f);
        }
    }

    @Test void rendersReportPreviewToPngUsingPdfBox() throws Exception {
        byte[] pdf = reports.generate(new ReportTemplateModel(sampleResponse(), Instant.parse("2026-08-02T01:42:00Z")));
        Path previewDirectory = Path.of("build/report-preview"); Files.createDirectories(previewDirectory);
        try (var document = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);
            assertThat(document.getNumberOfPages()).isEqualTo(2);
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                var image = renderer.renderImageWithDPI(page, 144);
                Path output = previewDirectory.resolve("verification-report-page-" + (page + 1) + ".png");
                ImageIO.write(image, "PNG", output.toFile());
                assertThat(image.getWidth()).isGreaterThan(1100);
                assertThat(image.getHeight()).isGreaterThan(1600);
                System.out.printf("Report preview page %d: %dx%d %s%n", page + 1, image.getWidth(), image.getHeight(), output);
            }
        }
    }

    @Test void embeddedFontPreservesEuropeanNamesAndLongValues() throws Exception {
        byte[] pdf = reports.generate(new ReportTemplateModel(sampleResponse(), Instant.parse("2026-08-02T01:42:00Z")));
        try (var document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Éva Grün-Wald", "Swisscom IT Services Finance S.E.");
            assertThat(document.getPage(0).getResources().getFontNames()).isNotEmpty();
        }
    }

    @Test void generatesUnsignedReportWithoutInventingSignatures() throws Exception {
        var response = validation.validate(new byte[0], metadata("unsigned.pdf"), new ValidationOptions(true, "en"));
        byte[] pdf = reports.generate(new ReportTemplateModel(response, Instant.now()));
        try (var document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Unsigned", "No supported embedded signatures were found");
            assertThat(text).doesNotContain("Signature 1", "Methodology");
        }
    }

    private DocumentValidationResponse sampleResponse() {
        Instant validated = Instant.parse("2026-08-02T01:41:50Z");
        List<SignatureResult> signatures = List.of(
            signature(1, "Sebastian Wodniansky-Wildenfeld", SignerEntityType.PERSON, Qualification.QES,
                "Qualified electronic signature", "Swisscom IT Services Finance S.E.", "Swisscom Diamant EU CA 4.1", Instant.parse("2026-07-29T14:05:00Z")),
            signature(2, "Éva Grün-Wald", SignerEntityType.PERSON, Qualification.QES,
                "Qualified electronic signature", "A-Trust GmbH", "A-Trust European Qualified CA", Instant.parse("2026-07-29T14:07:00Z")),
            signature(3, "Notarity GmbH", SignerEntityType.ORGANIZATION, Qualification.ADESEAL_WITH_QC,
                "Advanced electronic seal with qualified certificate", "PrimeSign GmbH", "PrimeSign Qualified Seal CA 2021", Instant.parse("2026-07-29T14:09:00Z")));

        List<ParticipantMatch> participants = List.of(
            participant("Sebastian Wodniansky-Wildenfeld", "sig-1", signatures.get(0)),
            participant("Éva Grün-Wald", "sig-2", signatures.get(1)),
            new ParticipantMatch(ParticipantRole.NOTARY, "Notarity GmbH", "sig-3", signatures.get(2).certificate().sha256Fingerprint(), true,
                signatures.get(2).signingTime().bestAvailableTime(), ParticipantStatus.MATCHED));
        NotarityCheckResult notarity = new NotarityCheckResult(NotarityStatus.EXACT_MATCH, Environment.PROTOTYPE, true,
            new RegistryReceipt(TernaryStatus.VALID, "Ed25519", "prototype-registry-2026-01", validated),
            new Transaction("NOT-DEMO-POA-001", TransactionStatus.COMPLETED, Instant.parse("2026-07-29T13:46:00Z"), Instant.parse("2026-07-29T14:09:00Z")),
            participants, new NotarityNotary("Notarity GmbH", null, "AT", "sig-3", true),
            new NotarizationAuthority("Notarity GmbH", SignerEntityType.ORGANIZATION, "sig-3", true,
                "Advanced electronic seal with qualified certificate", "PrimeSign GmbH", true), List.of(),
            "This result uses a cryptographically signed prototype registry record and does not query Notarity production systems.", List.of());
        NotaryCheckResult notary = new NotaryCheckResult(NotaryStatus.NO_MATCH, Confidence.NONE, null, null, null, List.of(),
            new RegistryInfo("Austrian notarial-signature directory snapshot", DatasetType.STATIC_PUBLIC_SNAPSHOT,
                Instant.parse("2026-08-02T00:00:00Z"), false, 5, "https://example.gv.at", false),
            "No named person signer matched the current Austrian notarial-signature directory snapshot.",
            "This result uses a static, incomplete snapshot of publicly accessible directory records and may not reflect later changes.", List.of());
        TrustSummary trust = new TrustSummary(card(CheckStatus.VALID, "Document integrity", "Cryptographically intact"),
            card(CheckStatus.VALID, "Embedded signatures", "3 signatures"), card(CheckStatus.WARNING, "Signature level", "Mixed signature levels"),
            card(CheckStatus.VALID, "Certificate trust", "Trusted EU certificate chains"), card(CheckStatus.NOT_FOUND, "Notary", "No match in current snapshot"),
            card(CheckStatus.VALID, "Notarity record", "Prototype record verified"));
        return new DocumentValidationResponse("VAL-POA-2026-001", "0.2.0", validated, OverallValidationStatus.VERIFIED,
            new ValidationSummary("The document has not been modified since signing",
                "Three embedded signatures were successfully verified. Certificate trust and eIDAS qualification were evaluated using authenticated EU Trusted Lists.", List.of()),
            new DocumentInfo("PoA_signed_sample_with_a_descriptive_long_filename.pdf", "application/pdf", 1_153_434, HASH, 3, false, true),
            trust, signatures, notary, notarity,
            new TechnicalEvidence("EU DSS 6.4", "EU Trusted List and revocation validation", "SHA-256", List.of(), "dss", "6.4",
                "TOTAL_PASSED", List.of(), 3, 9_800, true, true), new Capabilities(true, false));
    }

    private SignatureResult signature(int index, String name, SignerEntityType entity, Qualification qualification,
                                      String qualificationLabel, String provider, String authority, Instant signed) {
        String fingerprint = Integer.toHexString(index).repeat(64).substring(0, 64);
        return new SignatureResult("sig-" + index, index, SignatureStatus.VALID,
            new ValidationSummary("Signature valid", "Cryptographic and trust checks passed.", List.of()),
            new SignerIdentity(name, name, null, null, entity == SignerEntityType.ORGANIZATION ? name : null, null, "AT", null,
                "CN=" + name + ",O=" + provider + ",C=AT", name.toLowerCase(), entity),
            new SigningTime(signed.minusSeconds(30), signed, signed, TimeSource.TRUSTED_TIMESTAMP),
            new SignatureEvidence("PAdES", "PAdES Baseline", index == 3 ? "B_LTA" : "B_LT", qualification, qualificationLabel,
                TernaryStatus.VALID, TernaryStatus.VALID),
            new CertificateResult("CN=" + name + ",C=AT", "CN=" + authority + ",O=" + provider + ",C=AT", "1234567890" + index,
                fingerprint, Instant.parse("2026-07-29T14:03:00Z"), Instant.parse("2027-07-29T14:03:00Z"), CertificateStatus.VALID,
                CertificateStatus.VALID, ChainStatus.TRUSTED, RevocationStatus.GOOD, true, List.of(), List.of(), "RSA", 3072, List.of()),
            new TrustServiceResult(provider, qualificationLabel, "AT", "CA/QC", "granted", true, "EU Trusted List"),
            List.of(new TimestampResult("ts-" + index, TimestampType.SIGNATURE_TIMESTAMP, signed, TernaryStatus.VALID, provider, fingerprint, List.of())),
            null, List.of());
    }

    private ParticipantMatch participant(String name, String id, SignatureResult signature) {
        return new ParticipantMatch(ParticipantRole.PARTY, name, id, signature.certificate().sha256Fingerprint(), true,
            signature.signingTime().bestAvailableTime(), ParticipantStatus.MATCHED);
    }
    private StatusSummary card(CheckStatus status, String label, String value) { return new StatusSummary(status, label, value, value); }
    private String pageText(org.apache.pdfbox.pdmodel.PDDocument document, int page) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper(); stripper.setStartPage(page); stripper.setEndPage(page); return stripper.getText(document);
    }
    private long occurrences(String value, String needle) { return value.split(java.util.regex.Pattern.quote(needle), -1).length - 1L; }
    private DocumentMetadata metadata(String name) { return new DocumentMetadata(name, "application/pdf", 12345, "a".repeat(64), 3, false); }

    private record PositionedText(String text, float x, float y, float width) {}
    private static final class PositionStripper extends PDFTextStripper {
        private final List<PositionedText> lines = new ArrayList<>();
        PositionStripper() throws java.io.IOException {}
        @Override protected void writeString(String text, List<TextPosition> positions) {
            if (positions.isEmpty() || text.isBlank()) return;
            TextPosition first = positions.getFirst(), last = positions.getLast();
            lines.add(new PositionedText(text.trim(), first.getXDirAdj(), first.getYDirAdj(),
                last.getXDirAdj() + last.getWidthDirAdj() - first.getXDirAdj()));
        }
        PositionedText firstExact(String text) { return firstExact(text, 0); }
        PositionedText firstExact(String text, int skip) { return lines.stream().filter(line -> line.text().equals(text)).skip(skip).findFirst().orElseThrow(); }
        PositionedText firstContaining(String text) { return lines.stream().filter(line -> line.text().contains(text)).findFirst().orElseThrow(); }
    }
}

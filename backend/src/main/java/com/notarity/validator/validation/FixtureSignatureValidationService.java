package com.notarity.validator.validation;

import com.notarity.validator.api.ApiModels.*;
import com.notarity.validator.document.DocumentMetadata;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Service
@ConditionalOnProperty(name = "app.validation.mode", havingValue = "fixture")
public class FixtureSignatureValidationService implements SignatureValidationService {
    static final String NOTICE = "Prototype result: signature, certificate, notary, and Notarity trust checks use fixture data and do not query production systems.";
    private static final String FP1 = "4584e506b58f62173c3cbfbd16f49d459f72f6da3c2a56f4b344114a651a57e1";
    private static final String FP2 = "b28fc0ef034f04112a70f007b26741fca76bc2e70ae6f06096e638da2518b4c9";

    @Override
    public DocumentValidationResponse validate(byte[] bytes, DocumentMetadata metadata, ValidationOptions options) {
        String name = metadata.filename().toLowerCase(Locale.ROOT);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        if (name.contains("unsigned")) return unsigned(metadata, now, options);
        if (name.contains("invalid") || name.contains("tampered")) return invalid(metadata, now, options);
        return verified(metadata, now, options);
    }

    private DocumentValidationResponse base(DocumentMetadata m, Instant now, OverallValidationStatus status,
        ValidationSummary summary, TrustSummary trust, List<SignatureResult> signatures,
        NotaryCheckResult notary, NotarityCheckResult notarity, ValidationOptions options) {
        return new DocumentValidationResponse("val_" + m.sha256().substring(0, 14), "0.1.0", now, status, summary,
            new DocumentInfo(m.filename(), m.mediaType(), m.sizeBytes(), m.sha256(), m.pageCount(), m.encrypted(), !signatures.isEmpty()),
            trust, signatures, notary, notarity,
            new TechnicalEvidence("Fixture validation engine", "phase-2-fixture-policy", "SHA-256",
                options.includeTechnicalDetails() ? List.of(info("PROTOTYPE_VALIDATION", "Prototype validation", NOTICE, null)) : List.of()),
            new Capabilities(true, false));
    }

    private DocumentValidationResponse verified(DocumentMetadata m, Instant now, ValidationOptions o) {
        List<SignatureResult> signatures = List.of(signature(1, "Dr. Anna Leitner", Qualification.QES, "Qualified electronic signature", FP1, now.minus(2, ChronoUnit.DAYS)),
            signature(2, "Martin Berger", Qualification.ADES, "Advanced electronic signature", FP2, now.minus(2, ChronoUnit.DAYS).plus(8, ChronoUnit.MINUTES)));
        TrustSummary trust = trust(CheckStatus.VALID, "Valid", CheckStatus.VALID, "2 signatures",
            CheckStatus.VALID, "Qualified electronic signature", CheckStatus.VALID, "Trusted at signing time",
            CheckStatus.VALID, "Confirmed directory match", CheckStatus.VALID, "Exact document match");
        NotaryCheckResult notary = new NotaryCheckResult(NotaryStatus.CONFIRMED_MATCH, Confidence.HIGH, "sig-1",
            new SearchedIdentity("Dr. Anna Leitner", "anna leitner", "AT"),
            new MatchedNotary("at-notary-001", "Dr. Anna Leitner", "AT", "Austria", "Vienna", "Vienna",
                "Austrian prototype notary directory", RegistryStatus.ACTIVE, null),
            List.of("normalizedName", "countryCode"), new RegistryInfo("Prototype registry", DatasetType.PROTOTYPE_SNAPSHOT, now.minus(7, ChronoUnit.DAYS)),
            "The signer identity matches an active prototype notary-directory entry.",
            "A directory match indicates that the signer identity matches a registered notary. It does not by itself prove that the signer acted in an official notarial capacity for this transaction.",
            List.of(info("PROTOTYPE_NOTARY_DATA", "Prototype directory", NOTICE, "sig-1")));
        List<AuditTrailEvent> audit = List.of(
            new AuditTrailEvent("evt-1", AuditType.PARTICIPANT_SIGNED, now.minus(2, ChronoUnit.DAYS), "Document signature embedded", "A supported signature is represented in the document fixture.", new Actor("Martin Berger", "Party"), EvidenceSource.EMBEDDED_DOCUMENT, AuditStatus.VERIFIED),
            new AuditTrailEvent("evt-2", AuditType.NOTARY_COMPLETED, now.minus(2, ChronoUnit.DAYS).plus(10, ChronoUnit.MINUTES), "Notary completed notarization", "Completion is recorded in the prototype Notarity fixture.", new Actor("Dr. Anna Leitner", "Notary"), EvidenceSource.PROTOTYPE_NOTARITY_REGISTRY, AuditStatus.VERIFIED),
            new AuditTrailEvent("evt-3", AuditType.DOCUMENT_REGISTERED, now.minus(2, ChronoUnit.DAYS).plus(11, ChronoUnit.MINUTES), "Final document registered", "The uploaded document fingerprint exactly matches the prototype record.", null, EvidenceSource.PROTOTYPE_NOTARITY_REGISTRY, AuditStatus.VERIFIED));
        NotarityCheckResult notarity = new NotarityCheckResult(NotarityStatus.EXACT_MATCH, Environment.PROTOTYPE, true,
            new RegistryReceipt(TernaryStatus.VALID, "Ed25519", "prototype-key-2026-01", now),
            new Transaction("txn_prototype_84H2K", TransactionStatus.COMPLETED, now.minus(2, ChronoUnit.DAYS).minus(20, ChronoUnit.MINUTES), now.minus(2, ChronoUnit.DAYS).plus(10, ChronoUnit.MINUTES)),
            List.of(new ParticipantMatch(ParticipantRole.PARTY, "Martin Berger", "sig-2", FP2, true, now.minus(2, ChronoUnit.DAYS).plus(8, ChronoUnit.MINUTES), ParticipantStatus.MATCHED),
                new ParticipantMatch(ParticipantRole.NOTARY, "Dr. Anna Leitner", "sig-1", FP1, true, now.minus(2, ChronoUnit.DAYS), ParticipantStatus.MATCHED)),
            new NotarityNotary("Dr. Anna Leitner", "at-notary-001", "AT", "sig-1", true), audit,
            "This result uses a signed prototype registry record and does not query Notarity production systems.",
            List.of(info("PROTOTYPE_NOTARITY_DATA", "Prototype registry", NOTICE, null)));
        return base(m, now, OverallValidationStatus.VERIFIED,
            new ValidationSummary("Document verified", "The uploaded PDF was parsed successfully. Fixture evidence represents two valid signatures, including one qualified electronic signature.", List.of()),
            trust, signatures, notary, notarity, o);
    }

    private DocumentValidationResponse unsigned(DocumentMetadata m, Instant now, ValidationOptions o) {
        TrustSummary trust = trust(CheckStatus.NOT_APPLICABLE, "Not signed", CheckStatus.NOT_FOUND, "No signatures",
            CheckStatus.NOT_APPLICABLE, "Not applicable", CheckStatus.NOT_CHECKED, "Not checked",
            CheckStatus.NOT_CHECKED, "Not checked", CheckStatus.NOT_FOUND, "No record found");
        NotaryCheckResult notary = new NotaryCheckResult(NotaryStatus.NOT_CHECKED, Confidence.NONE, null, null, null, List.of(),
            new RegistryInfo("Prototype registry", DatasetType.PROTOTYPE_SNAPSHOT, null), "No signer identity was available for a notary-directory check.",
            "A directory match requires an identifiable embedded signer.", List.of());
        NotarityCheckResult notarity = new NotarityCheckResult(NotarityStatus.NO_RECORD, Environment.PROTOTYPE, false,
            new RegistryReceipt(TernaryStatus.INDETERMINATE, null, null, null), null, List.of(), null, List.of(),
            "This result uses a prototype registry and does not query production systems.", List.of());
        return base(m, now, OverallValidationStatus.UNSIGNED,
            new ValidationSummary("No cryptographic signatures found", "The document does not contain a supported embedded cryptographic signature. This does not necessarily mean that the document is fraudulent.",
                List.of(info("NO_SIGNATURES_FOUND", "No signatures found", "No supported embedded signatures were represented by the fixture behavior.", null))),
            trust, List.of(), notary, notarity, o);
    }

    private DocumentValidationResponse invalid(DocumentMetadata m, Instant now, ValidationOptions o) {
        ValidationMessage failure = new ValidationMessage("DOCUMENT_INTEGRITY_INVALID", MessageSeverity.ERROR, "Document integrity failed",
            "The fixture represents a document that changed after signing.", "sig-1", "Fixture behavior selected from the filename.");
        SignatureResult sig = signature(1, "Dr. Anna Leitner", Qualification.QES, "Qualified electronic signature", FP1, now.minus(2, ChronoUnit.DAYS));
        sig = new SignatureResult(sig.id(), sig.index(), SignatureStatus.INVALID,
            new ValidationSummary("Invalid signature", "The signed content does not match the embedded signature.", List.of(failure)),
            sig.signer(), sig.signingTime(), new SignatureEvidence("PAdES", "PAdES Baseline", "B-T", Qualification.QES,
            "Qualified electronic signature", TernaryStatus.INVALID, TernaryStatus.INVALID), sig.certificate(),
            sig.trustService(), sig.timestamps(), sig.providerAttribution(), List.of(failure));
        TrustSummary trust = trust(CheckStatus.INVALID, "Invalid", CheckStatus.INVALID, "1 invalid signature",
            CheckStatus.VALID, "Qualified electronic signature", CheckStatus.VALID, "Trusted certificate",
            CheckStatus.NOT_CHECKED, "Not checked", CheckStatus.NOT_CHECKED, "Not checked");
        return base(m, now, OverallValidationStatus.INVALID,
            new ValidationSummary("Document integrity could not be verified", "A material fixture integrity check failed. Do not rely on this document without obtaining a trusted original.", List.of(failure)),
            trust, List.of(sig), null, null, o);
    }

    private SignatureResult signature(int index, String name, Qualification q, String label, String fp, Instant time) {
        String id = "sig-" + index;
        CertificateResult cert = new CertificateResult("CN=" + name + ",C=AT", "CN=Prototype Trust CA,O=Fixture Trust,C=AT",
            "0" + index + "A84F", fp, time.minus(3000, ChronoUnit.DAYS), time.plus(365, ChronoUnit.DAYS),
            CertificateStatus.VALID, CertificateStatus.VALID, ChainStatus.TRUSTED, RevocationStatus.GOOD,
            q == Qualification.QES, List.of("0.4.0.194112.1.2"), q == Qualification.QES ? List.of("QcCompliance") : List.of(),
            "RSA", 3072, List.of());
        return new SignatureResult(id, index, SignatureStatus.VALID,
            new ValidationSummary("Signature valid", "Fixture signature evidence passed all represented checks.", List.of()),
            new SignerIdentity(name, name, null, null, index == 1 ? "Leitner Notariat" : null, null, "AT", null,
                "CN=" + name + ",C=AT", name.toLowerCase(Locale.ROOT), SignerEntityType.PERSON),
            new SigningTime(time.minus(2, ChronoUnit.MINUTES), time, time, TimeSource.TRUSTED_TIMESTAMP),
            new SignatureEvidence("PAdES", "PAdES Baseline", index == 1 ? "B-LT" : "B-T", q, label, TernaryStatus.VALID, TernaryStatus.VALID),
            cert, new TrustServiceResult("Fixture Trust Services GmbH", "Qualified certificate service", "AT",
                "CA/QC", "granted", q == Qualification.QES, "Prototype trusted list"),
            List.of(new TimestampResult("ts-" + index, TimestampType.SIGNATURE_TIMESTAMP, time, TernaryStatus.VALID,
                "Fixture Timestamp Authority", fp, List.of())),
            new ProviderAttribution("Fixture Trust Services GmbH", index == 1 ? "Prototype signing channel" : null,
                Confidence.HIGH, List.of("Fixture provider rule matched"), NOTICE), List.of());
    }

    private TrustSummary trust(CheckStatus a, String av, CheckStatus b, String bv, CheckStatus c, String cv,
                               CheckStatus d, String dv, CheckStatus e, String ev, CheckStatus f, String fv) {
        return new TrustSummary(card(a, "Document integrity", av, "Whether the signed bytes still match the signature."),
            card(b, "Embedded signatures", bv, "Supported cryptographic signatures represented in the result."),
            card(c, "Signature level", cv, "The strongest represented signature qualification."),
            card(d, "Certificate trust", dv, "Trust status at the represented signing time."),
            card(e, "Notary", ev, "Prototype notary-directory identity check."),
            card(f, "Notarity record", fv, "Prototype exact-document registry check."));
    }
    private StatusSummary card(CheckStatus s, String l, String v, String d) { return new StatusSummary(s, l, v, d); }
    private ValidationMessage info(String code, String title, String message, String sig) {
        return new ValidationMessage(code, MessageSeverity.INFO, title, message, sig, null);
    }
}

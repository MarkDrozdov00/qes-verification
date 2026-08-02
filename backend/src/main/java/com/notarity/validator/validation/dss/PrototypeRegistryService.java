package com.notarity.validator.validation.dss;

import com.notarity.validator.api.ApiModels.*;
import com.notarity.validator.registry.*;
import com.notarity.validator.registry.PrototypeRegistryModels.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class PrototypeRegistryService {
    private static final String VERIFIED_NOTICE =
        "This result uses a cryptographically signed prototype registry record and does not query Notarity production systems.";
    private final PrototypeNotarityRegistry registry;
    private final RegistryReceiptVerifier receipts;
    private final CertificateFingerprintParticipantMatcher participants;

    public PrototypeRegistryService(PrototypeNotarityRegistry registry, RegistryReceiptVerifier receipts,
                                    CertificateFingerprintParticipantMatcher participants) {
        this.registry = registry;
        this.receipts = receipts;
        this.participants = participants;
    }

    public PrototypeResults lookup(String sha256, List<SignatureResult> signatures, Instant now) {
        final SignedPrototypeRegistryRecord record;
        try {
            record = registry.findByDocumentHash(sha256).orElse(null);
        } catch (RuntimeException exception) {
            return unavailable("REGISTRY_READ_FAILED", "The prototype registry could not be read safely.");
        }
        if (record == null) return notFound();
        if (record.payload() == null || !normalize(sha256).equals(normalize(record.payload().documentSha256()))) {
            return failed(record, signatures, now, NotarityStatus.HASH_MISMATCH,
                TernaryStatus.INVALID, "REGISTRY_HASH_MISMATCH", "The registry index and signed payload contain different document fingerprints.");
        }

        RegistryReceiptVerificationResult receipt = receipts.verify(record);
        if (receipt.status() == ReceiptVerificationStatus.INVALID) {
            return failed(record, signatures, now, NotarityStatus.RECEIPT_INVALID,
                TernaryStatus.INVALID, receipt.reason().name(), "The prototype registry receipt could not be verified.");
        }
        if (receipt.status() == ReceiptVerificationStatus.INDETERMINATE) {
            return failed(record, signatures, now, NotarityStatus.INDETERMINATE,
                TernaryStatus.INDETERMINATE, receipt.reason().name(), "The prototype registry receipt could not be conclusively verified.");
        }

        List<ParticipantMatch> matches = participants.match(record, signatures);
        SignatureResult notarySignature = participants.findNotary(record, signatures);
        boolean participantMismatch = matches.stream().anyMatch(match -> match.status() != ParticipantStatus.MATCHED);
        boolean notaryRequired = record.payload().notary() != null
            && record.payload().notary().certificateSha256Fingerprint() != null;
        boolean notaryMismatch = notaryRequired && notarySignature == null;
        boolean completed = "COMPLETED".equalsIgnoreCase(record.payload().status());
        NotarityStatus status = participantMismatch || notaryMismatch ? NotarityStatus.SIGNER_MISMATCH
            : completed ? NotarityStatus.EXACT_MATCH : NotarityStatus.INDETERMINATE;
        List<ValidationMessage> warnings = new ArrayList<>();
        if (participantMismatch || notaryMismatch) warnings.add(warning("REGISTRY_SIGNER_MISMATCH",
            "Registered signer mismatch", "At least one registered certificate fingerprint was not found among the embedded PDF signatures."));
        if (!completed) warnings.add(warning("REGISTRY_TRANSACTION_NOT_COMPLETED",
            "Transaction not completed", "The signed prototype record does not identify the transaction as completed."));

        NotarityCheckResult notarity = result(record, signatures, matches, notarySignature, now, status,
            TernaryStatus.VALID, audit(record, signatures, AuditStatus.VERIFIED), warnings,
            status == NotarityStatus.EXACT_MATCH ? VERIFIED_NOTICE
                : "The document hash matched a signed prototype record, but its participant or transaction evidence was incomplete.");
        return new PrototypeResults(notarity, status == NotarityStatus.EXACT_MATCH ? CheckStatus.VALID : CheckStatus.WARNING,
            status == NotarityStatus.EXACT_MATCH ? "Exact prototype match"
                : status == NotarityStatus.SIGNER_MISMATCH ? "Signer mismatch" : "Unable to verify");
    }

    private PrototypeResults failed(SignedPrototypeRegistryRecord record, List<SignatureResult> signatures, Instant now,
                                    NotarityStatus status, TernaryStatus receiptStatus, String code, String message) {
        AuditStatus auditStatus = receiptStatus == TernaryStatus.INVALID ? AuditStatus.INVALID : AuditStatus.INFORMATIONAL;
        var notarity = result(record, signatures, List.of(), null, now, status, receiptStatus,
            audit(record, signatures, auditStatus), List.of(warning(code, "Prototype receipt verification failed", message)),
            "A prototype record was found, but its registry receipt was not verified. No production Notarity system was queried.");
        return new PrototypeResults(notarity, status == NotarityStatus.RECEIPT_INVALID || status == NotarityStatus.HASH_MISMATCH
                ? CheckStatus.INVALID : CheckStatus.INDETERMINATE,
            status == NotarityStatus.RECEIPT_INVALID ? "Registry receipt invalid"
                : status == NotarityStatus.HASH_MISMATCH ? "Registry hash mismatch" : "Unable to verify");
    }

    private NotarityCheckResult result(SignedPrototypeRegistryRecord record, List<SignatureResult> signatures,
                                       List<ParticipantMatch> matches, SignatureResult notarySignature, Instant now,
                                       NotarityStatus status, TernaryStatus receiptStatus,
                                       List<AuditTrailEvent> audit, List<ValidationMessage> warnings, String notice) {
        var payload = record.payload();
        var notary = payload == null ? null : payload.notary();
        boolean organizationAuthority = notarySignature != null && notarySignature.signer() != null
            && notarySignature.signer().entityType() == SignerEntityType.ORGANIZATION;
        NotarityNotary namedNotary = notary == null || organizationAuthority ? null
            : new NotarityNotary(notary.displayName(), notary.registryId(), notary.countryCode(),
                notarySignature == null ? null : notarySignature.id(),
                notary.certificateSha256Fingerprint() == null ? null : notarySignature != null);
        NotarizationAuthority authority = !organizationAuthority ? null : new NotarizationAuthority(
            notarySignature.signer().displayName() == null ? notary.displayName() : notarySignature.signer().displayName(),
            SignerEntityType.ORGANIZATION, notarySignature.id(),
            notary.certificateSha256Fingerprint() == null ? null : true,
            notarySignature.signature() == null ? null : notarySignature.signature().qualificationLabel(),
            notarySignature.trustService() == null ? null : notarySignature.trustService().providerName(),
            sealTrustedAtSigningTime(notarySignature));
        return new NotarityCheckResult(status, Environment.PROTOTYPE,
            status == NotarityStatus.HASH_MISMATCH ? false : true,
            new RegistryReceipt(receiptStatus, record.algorithm(), record.keyId(),
                receiptStatus == TernaryStatus.VALID ? now : null),
            payload == null ? null : new Transaction(payload.transactionId(), transactionStatus(payload.status()),
                payload.createdAt(), payload.completedAt()),
            matches, namedNotary, authority, audit, notice, warnings);
    }

    private List<AuditTrailEvent> audit(SignedPrototypeRegistryRecord record, List<SignatureResult> signatures,
                                        AuditStatus registryStatus) {
        List<AuditTrailEvent> events = new ArrayList<>();
        for (SignatureResult signature : signatures) {
            Instant time = signature.signingTime().bestAvailableTime();
            if (time != null) events.add(new AuditTrailEvent("embedded-" + signature.id(), AuditType.OTHER, time,
                signature.signer().entityType() == SignerEntityType.ORGANIZATION
                    ? "Electronic seal embedded" : "Electronic signature embedded",
                "Derived from the PDF signature evidence extracted by EU DSS.",
                signature.signer().displayName() == null ? null : new Actor(signature.signer().displayName(),
                    signature.signer().entityType() == SignerEntityType.ORGANIZATION ? "Notarization authority" : "Signer"),
                EvidenceSource.EMBEDDED_DOCUMENT, AuditStatus.VERIFIED));
        }
        if (record.payload() != null && record.payload().auditTrail() != null) {
            for (PrototypeAuditEvent event : record.payload().auditTrail()) {
                events.add(new AuditTrailEvent(event.id(), auditType(event.type()), event.occurredAt(), event.title(),
                    event.description(), event.actor() == null ? null
                        : new Actor(event.actor().displayName(), event.actor().role()),
                    EvidenceSource.PROTOTYPE_NOTARITY_REGISTRY, registryStatus));
            }
        }
        return events.stream().sorted(java.util.Comparator.comparing(AuditTrailEvent::occurredAt,
            java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))).toList();
    }

    private PrototypeResults notFound() {
        NotarityCheckResult notarity = new NotarityCheckResult(NotarityStatus.NO_RECORD, Environment.PROTOTYPE, false,
            new RegistryReceipt(TernaryStatus.INDETERMINATE, null, null, null), null, List.of(), null, List.of(),
            "No matching document hash was found in the prototype registry.", List.of());
        return new PrototypeResults(notarity, CheckStatus.NOT_FOUND, "No prototype record");
    }
    private PrototypeResults unavailable(String code, String message) {
        NotarityCheckResult notarity = new NotarityCheckResult(NotarityStatus.INDETERMINATE, Environment.PROTOTYPE, null,
            new RegistryReceipt(TernaryStatus.INDETERMINATE, null, null, null), null, List.of(), null, List.of(),
            "The prototype registry could not be checked. No production Notarity system was queried.",
            List.of(warning(code, "Prototype registry unavailable", message)));
        return new PrototypeResults(notarity, CheckStatus.INDETERMINATE, "Unable to verify");
    }
    private ValidationMessage warning(String code, String title, String message) {
        return new ValidationMessage(code, MessageSeverity.WARNING, title, message, null, null);
    }
    private TransactionStatus transactionStatus(String value) {
        try { return TransactionStatus.valueOf(value == null ? "UNKNOWN" : value.toUpperCase()); }
        catch (IllegalArgumentException exception) { return TransactionStatus.UNKNOWN; }
    }
    private AuditType auditType(PrototypeRegistryModels.AuditEventType value) {
        try { return AuditType.valueOf(value == null ? "OTHER" : value.name()); }
        catch (IllegalArgumentException exception) { return AuditType.OTHER; }
    }
    private String normalize(String value) { return CertificateFingerprintParticipantMatcher.normalize(value); }
    private Boolean sealTrustedAtSigningTime(SignatureResult signature) {
        if (signature.certificate() == null) return null;
        if (signature.certificate().chainStatus() == ChainStatus.TRUSTED
            && signature.certificate().statusAtSigningTime() == CertificateStatus.VALID) return true;
        if (signature.certificate().chainStatus() == ChainStatus.UNTRUSTED
            || signature.certificate().statusAtSigningTime() == CertificateStatus.REVOKED) return false;
        return null;
    }

    public record PrototypeResults(NotarityCheckResult notarity,
                                   CheckStatus notarityStatus, String notarityValue) {}
}

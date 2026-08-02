package com.notarity.validator.registry;

import java.time.Instant;
import java.util.List;

public final class PrototypeRegistryModels {
    private PrototypeRegistryModels() {}

    public enum ParticipantRole { PARTY, NOTARY, WITNESS, OTHER }
    public enum AuditEventType {
        SESSION_CREATED, PARTICIPANT_INVITED, IDENTITY_VERIFIED, PARTICIPANT_SIGNED,
        NOTARY_JOINED, NOTARY_COMPLETED, DOCUMENT_REGISTERED, OTHER
    }
    public record PrototypeAuditActor(String displayName, String role) {}
    public record PrototypeParticipant(String displayName, ParticipantRole role,
                                       String certificateSha256Fingerprint, Instant signedAt) {}
    public record PrototypeNotary(String displayName, String certificateSha256Fingerprint,
                                  String countryCode, String registryId) {}
    public record PrototypeAuditEvent(String id, AuditEventType type, Instant occurredAt,
                                      String title, String description, PrototypeAuditActor actor) {}
    public record PrototypeNotarizationRecord(
        int schemaVersion, String transactionId, String status, String documentSha256,
        Instant createdAt, Instant completedAt, List<PrototypeParticipant> participants,
        PrototypeNotary notary, List<PrototypeAuditEvent> auditTrail) {}
    public record SignedPrototypeRegistryRecord(PrototypeNotarizationRecord payload,
                                                String algorithm, String keyId, String signature) {}
    public enum ReceiptVerificationStatus { VALID, INVALID, INDETERMINATE }
    public enum ReceiptVerificationReason {
        NONE, KEY_NOT_FOUND, UNSUPPORTED_ALGORITHM, MALFORMED_SIGNATURE,
        CANONICALIZATION_FAILED, SIGNATURE_MISMATCH, PUBLIC_KEY_INVALID
    }
    public record RegistryReceiptVerificationResult(ReceiptVerificationStatus status,
                                                    ReceiptVerificationReason reason) {}
}

package com.notarity.validator.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ApiModels {
    private ApiModels() {}

    public enum OverallValidationStatus { VERIFIED, VERIFIED_WITH_WARNINGS, INVALID, INDETERMINATE, UNSIGNED }
    public enum CheckStatus { VALID, WARNING, INVALID, INDETERMINATE, NOT_FOUND, NOT_CHECKED, NOT_APPLICABLE }
    public enum MessageSeverity { INFO, WARNING, ERROR }

    public record DocumentValidationResponse(
        String validationId, String validatorVersion, Instant validatedAt,
        OverallValidationStatus overallStatus, ValidationSummary summary,
        DocumentInfo document, TrustSummary trustSummary, List<SignatureResult> signatures,
        NotaryCheckResult notaryCheck, NotarityCheckResult notarityCheck,
        TechnicalEvidence technicalEvidence, Capabilities capabilities) {}

    public record ValidationSummary(String title, String description, List<ValidationMessage> warnings) {}
    public record DocumentInfo(String filename, String mediaType, long sizeBytes, String sha256,
                               int pageCount, boolean encrypted, boolean signed) {}
    public record StatusSummary(CheckStatus status, String label, String value, String description) {}
    public record TrustSummary(StatusSummary documentIntegrity, StatusSummary embeddedSignatures,
                               StatusSummary signatureQualification, StatusSummary certificateTrust,
                               StatusSummary notaryRegistry, StatusSummary notarityRecord) {}
    public record SignatureResult(String id, int index, SignatureStatus status, ValidationSummary summary,
                                  SignerIdentity signer, SigningTime signingTime, SignatureEvidence signature,
                                  CertificateResult certificate, TrustServiceResult trustService,
                                  List<TimestampResult> timestamps, ProviderAttribution providerAttribution,
                                  List<ValidationMessage> warnings) {}
    public enum SignatureStatus { VALID, VALID_WITH_WARNINGS, INVALID, INDETERMINATE }
    public enum SignerEntityType { PERSON, ORGANIZATION, UNKNOWN }
    public record SignerIdentity(String displayName, String commonName, String givenName, String surname,
                                 String organization, String organizationalUnit, String countryCode,
                                 String email, String certificateSubject, String normalizedName,
                                 SignerEntityType entityType) {}
    public enum TimeSource { TRUSTED_TIMESTAMP, CLAIMED_SIGNING_TIME, VALIDATION_TIME, UNKNOWN }
    public record SigningTime(Instant claimedTime, Instant trustedTime, Instant bestAvailableTime, TimeSource source) {}
    public enum BaselineLevel { B_B, B_T, B_LT, B_LTA, UNKNOWN }
    public enum Qualification { QES, QESEAL, ADES_WITH_QC, ADESEAL_WITH_QC, ADES, ADESEAL, ELECTRONIC_SIGNATURE, ELECTRONIC_SEAL, UNKNOWN }
    public enum TernaryStatus { VALID, INVALID, INDETERMINATE }
    public record SignatureEvidence(String format, String profile, String baselineLevel,
                                    Qualification qualification, String qualificationLabel,
                                    TernaryStatus integrity, TernaryStatus signatureValue) {}
    public enum CertificateStatus { VALID, EXPIRED, NOT_YET_VALID, REVOKED, UNKNOWN }
    public enum ChainStatus { TRUSTED, UNTRUSTED, INCOMPLETE, UNKNOWN }
    public enum RevocationStatus { GOOD, REVOKED, UNKNOWN, NOT_CHECKED }
    public record CertificateResult(String subject, String issuer, String serialNumber, String sha256Fingerprint,
                                    Instant validFrom, Instant validUntil, CertificateStatus currentStatus,
                                    CertificateStatus statusAtSigningTime, ChainStatus chainStatus,
                                    RevocationStatus revocationStatus, Boolean qualifiedCertificate,
                                    List<String> certificatePolicies, List<String> qcStatements,
                                    String publicKeyAlgorithm, Integer publicKeySize, List<ValidationMessage> warnings) {}
    public record TrustServiceResult(String providerName, String serviceName, String countryCode,
                                     String serviceType, String serviceStatus, Boolean qualifiedAtSigningTime,
                                     String trustedListSource) {}
    public enum TimestampType { SIGNATURE_TIMESTAMP, CONTENT_TIMESTAMP, ARCHIVE_TIMESTAMP, DOCUMENT_TIMESTAMP, OTHER }
    public record TimestampResult(String id, TimestampType type, Instant time, TernaryStatus status,
                                  String authority, String certificateFingerprint, List<ValidationMessage> warnings) {}
    public enum Confidence { HIGH, MEDIUM, LOW, NONE }
    public record ProviderAttribution(String providerName, String signingChannel, Confidence confidence,
                                      List<String> evidence, String disclaimer) {}
    public enum NotaryStatus { CONFIRMED_MATCH, POSSIBLE_MATCH, NO_MATCH, AMBIGUOUS, NOT_CHECKED, REGISTRY_UNAVAILABLE }
    public record SearchedIdentity(String displayName, String normalizedName, String countryCode) {}
    public enum RegistryStatus { ACTIVE, INACTIVE, UNKNOWN }
    public record MatchedNotary(String id, String fullName, String countryCode, String countryName,
                                String city, String jurisdiction, String registryName,
                                RegistryStatus registryStatus, String sourceUrl) {}
    public enum DatasetType { PROTOTYPE_SNAPSHOT, STATIC_PUBLIC_SNAPSHOT, LIVE_DIRECTORY, MOCK }
    public record RegistryInfo(String provider, DatasetType datasetType, Instant updatedAt,
                               Boolean complete, Integer recordCount, String sourceUrl, Boolean stale) {
        public RegistryInfo(String provider, DatasetType datasetType, Instant updatedAt) {
            this(provider, datasetType, updatedAt, null, null, null, null);
        }
    }
    public record NotaryCheckResult(NotaryStatus status, Confidence confidence, String signatureId,
                                    SearchedIdentity searchedIdentity, MatchedNotary matchedNotary,
                                    List<String> matchedFields, RegistryInfo registry, String explanation,
                                    String disclaimer, List<ValidationMessage> warnings) {}
    public enum NotarityStatus { EXACT_MATCH, NO_RECORD, HASH_MISMATCH, SIGNER_MISMATCH, RECEIPT_INVALID, INDETERMINATE, NOT_CHECKED }
    public enum Environment { PROTOTYPE, PRODUCTION }
    public record RegistryReceipt(TernaryStatus status, String algorithm, String keyId, Instant verifiedAt) {}
    public enum TransactionStatus { COMPLETED, CANCELLED, UNKNOWN }
    public record Transaction(String id, TransactionStatus status, Instant createdAt, Instant completedAt) {}
    public enum ParticipantRole { PARTY, NOTARY, WITNESS, OTHER }
    public enum ParticipantStatus { MATCHED, MISMATCHED, NOT_FOUND, INDETERMINATE }
    public record ParticipantMatch(ParticipantRole role, String registryDisplayName, String signatureId,
                                   String certificateFingerprint, Boolean certificateFingerprintMatch,
                                   Instant signingTime, ParticipantStatus status) {}
    public record NotarityNotary(String name, String registryId, String countryCode,
                                 String signatureId, Boolean certificateFingerprintMatch) {}
    public record NotarizationAuthority(String name, SignerEntityType entityType, String signatureId,
                                        Boolean certificateFingerprintMatch, String qualification,
                                        String trustProvider, Boolean sealTrustedAtSigningTime) {}
    public enum AuditType { SESSION_CREATED, PARTICIPANT_INVITED, IDENTITY_VERIFIED, PARTICIPANT_SIGNED, NOTARY_JOINED, NOTARY_COMPLETED, DOCUMENT_REGISTERED, OTHER }
    public enum EvidenceSource { EMBEDDED_DOCUMENT, PROTOTYPE_NOTARITY_REGISTRY }
    public enum AuditStatus { VERIFIED, INFORMATIONAL, INVALID }
    public record Actor(String displayName, String role) {}
    public record AuditTrailEvent(String id, AuditType type, Instant occurredAt, String title,
                                  String description, Actor actor, EvidenceSource evidenceSource, AuditStatus status) {}
    public record NotarityCheckResult(NotarityStatus status, Environment environment, Boolean documentHashMatch,
                                      RegistryReceipt registryReceipt, Transaction transaction,
                                      List<ParticipantMatch> participantMatches, NotarityNotary notary,
                                      NotarizationAuthority notarizationAuthority,
                                      List<AuditTrailEvent> auditTrail, String prototypeNotice,
                                      List<ValidationMessage> warnings) {
        public NotarityCheckResult(NotarityStatus status, Environment environment, Boolean documentHashMatch,
                                   RegistryReceipt registryReceipt, Transaction transaction,
                                   List<ParticipantMatch> participantMatches, NotarityNotary notary,
                                   List<AuditTrailEvent> auditTrail, String prototypeNotice,
                                   List<ValidationMessage> warnings) {
            this(status, environment, documentHashMatch, registryReceipt, transaction, participantMatches,
                notary, null, auditTrail, prototypeNotice, warnings);
        }
    }
    public record ValidationMessage(String code, MessageSeverity severity, String title, String message,
                                    String signatureId, String technicalDetails) {}
    public record TechnicalEvidence(String validationEngine, String validationPolicy,
                                    String documentHashAlgorithm, List<ValidationMessage> warnings,
                                    String validationMode, String dssVersion, String dssOverallIndication,
                                    List<String> dssSubIndications, int detectedSignatureCount,
                                    long executionTimeMillis, boolean trustedListsConfigured,
                                    boolean revocationCheckingConfigured) {
        public TechnicalEvidence(String validationEngine, String validationPolicy,
                                 String documentHashAlgorithm, List<ValidationMessage> warnings) {
            this(validationEngine, validationPolicy, documentHashAlgorithm, warnings,
                null, null, null, List.of(), 0, 0, false, false);
        }
    }
    public record Capabilities(boolean reportPdfAvailable, boolean technicalJsonAvailable) {}
    public record ApiError(Instant timestamp, int status, String error, String code, String message,
                           String path, String validationId, Map<String, Object> details) {}
}

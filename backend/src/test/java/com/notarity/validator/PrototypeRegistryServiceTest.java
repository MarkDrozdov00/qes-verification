package com.notarity.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.notarity.validator.api.ApiModels.*;
import com.notarity.validator.registry.*;
import com.notarity.validator.registry.PrototypeRegistryModels.*;
import com.notarity.validator.validation.dss.PrototypeRegistryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PrototypeRegistryServiceTest {
    @TempDir Path directory;
    ObjectMapper mapper;
    CanonicalJsonService canonical;
    KeyPair keyPair;
    PrototypeRegistryKeyStore keys;
    Ed25519RegistryReceiptVerifier verifier;
    CertificateFingerprintParticipantMatcher matcher = new CertificateFingerprintParticipantMatcher();

    @BeforeEach void setup() throws Exception {
        mapper = JsonMapper.builder().findAndAddModules().build();
        canonical = new CanonicalJsonService(mapper);
        keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String pem = "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
        byte[] json = mapper.writeValueAsBytes(List.of(new PrototypeRegistryKeyStore.PublicKeyDefinition(
            "test-key", "Ed25519", pem, "ACTIVE", Instant.parse("2026-01-01T00:00:00Z"))));
        keys = new PrototypeRegistryKeyStore(mapper, new ByteArrayResource(json));
        verifier = new Ed25519RegistryReceiptVerifier(canonical, keys);
    }

    @Test void canonicalPayloadIsDeterministic() {
        var payload = payload("a".repeat(64), "COMPLETED", "b".repeat(64));
        assertThat(canonical.canonicalize(payload)).isEqualTo(canonical.canonicalize(payload));
    }
    @Test void validReceiptAndFingerprintBindingsProduceExactMatch() throws Exception {
        String hash = "a".repeat(64), fingerprint = "b".repeat(64);
        writeRecord(hash, signed(payload(hash, "COMPLETED", fingerprint), "test-key", "Ed25519"));
        var result = service().lookup(hash, List.of(signature("sig-1", fingerprint)), Instant.now());
        assertThat(result.notarity().status()).isEqualTo(NotarityStatus.EXACT_MATCH);
        assertThat(result.notarity().registryReceipt().status()).isEqualTo(TernaryStatus.VALID);
        assertThat(result.notarity().participantMatches()).allMatch(match -> match.status() == ParticipantStatus.MATCHED);
        assertThat(result.notarity().notary().signatureId()).isEqualTo("sig-1");
        assertThat(result.notarity().auditTrail()).anyMatch(event ->
            event.evidenceSource() == EvidenceSource.PROTOTYPE_NOTARITY_REGISTRY
                && event.status() == AuditStatus.VERIFIED);
    }
    @Test void unknownHashHasNoRecord() {
        writeEmptyIndex();
        assertThat(service().lookup("c".repeat(64), List.of(), Instant.now()).notarity().status())
            .isEqualTo(NotarityStatus.NO_RECORD);
    }
    @Test void modifiedAuditEventInvalidatesReceipt() throws Exception {
        String hash = "a".repeat(64), fingerprint = "b".repeat(64);
        var envelope = signed(payload(hash, "COMPLETED", fingerprint), "test-key", "Ed25519");
        var altered = new PrototypeNotarizationRecord(envelope.payload().schemaVersion(), envelope.payload().transactionId(),
            envelope.payload().status(), hash, envelope.payload().createdAt(), envelope.payload().completedAt(),
            envelope.payload().participants(), envelope.payload().notary(),
            List.of(new PrototypeAuditEvent("evt-1", AuditEventType.OTHER, Instant.parse("2026-07-29T14:00:00Z"),
                "Altered event", "tampered", null)));
        writeRecord(hash, new SignedPrototypeRegistryRecord(altered, "Ed25519", "test-key", envelope.signature()));
        var result = service().lookup(hash, List.of(signature("sig-1", fingerprint)), Instant.now()).notarity();
        assertThat(result.status()).isEqualTo(NotarityStatus.RECEIPT_INVALID);
        assertThat(result.auditTrail()).filteredOn(event ->
            event.evidenceSource() == EvidenceSource.PROTOTYPE_NOTARITY_REGISTRY)
            .allMatch(event -> event.status() == AuditStatus.INVALID);
    }
    @Test void participantMismatchDoesNotMatchByName() throws Exception {
        String hash = "a".repeat(64);
        writeRecord(hash, signed(payload(hash, "COMPLETED", "b".repeat(64)), "test-key", "Ed25519"));
        var result = service().lookup(hash, List.of(signature("sig-1", "c".repeat(64))), Instant.now()).notarity();
        assertThat(result.status()).isEqualTo(NotarityStatus.SIGNER_MISMATCH);
        assertThat(result.participantMatches().getFirst().status()).isEqualTo(ParticipantStatus.NOT_FOUND);
        assertThat(result.notary().certificateFingerprintMatch()).isFalse();
    }
    @Test void exactMatchRequiresCompletedTransaction() throws Exception {
        String hash = "a".repeat(64), fingerprint = "b".repeat(64);
        writeRecord(hash, signed(payload(hash, "CANCELLED", fingerprint), "test-key", "Ed25519"));
        assertThat(service().lookup(hash, List.of(signature("sig-1", fingerprint)), Instant.now()).notarity().status())
            .isEqualTo(NotarityStatus.INDETERMINATE);
    }
    @Test void unknownKeyAndUnsupportedAlgorithmAreIndeterminate() throws Exception {
        var payload = payload("a".repeat(64), "COMPLETED", "b".repeat(64));
        assertThat(verifier.verify(signed(payload, "unknown", "Ed25519")).status())
            .isEqualTo(ReceiptVerificationStatus.INDETERMINATE);
        assertThat(verifier.verify(signed(payload, "test-key", "RSA")).status())
            .isEqualTo(ReceiptVerificationStatus.INDETERMINATE);
    }
    @Test void malformedBase64FailsSafely() {
        var record = new SignedPrototypeRegistryRecord(payload("a".repeat(64), "COMPLETED", "b".repeat(64)),
            "Ed25519", "test-key", "not-base64!");
        assertThat(verifier.verify(record).reason()).isEqualTo(ReceiptVerificationReason.MALFORMED_SIGNATURE);
    }

    private PrototypeRegistryService service() {
        return new PrototypeRegistryService(new JsonPrototypeNotarityRegistry(mapper, directory.toString()), verifier, matcher);
    }
    private PrototypeNotarizationRecord payload(String hash, String status, String fingerprint) {
        var participant = new PrototypeParticipant("Same readable name", PrototypeRegistryModels.ParticipantRole.PARTY,
            fingerprint, Instant.parse("2026-07-29T14:05:00Z"));
        var notary = new PrototypeNotary("Prototype notary", fingerprint, "AT", "NOT-1");
        var event = new PrototypeAuditEvent("evt-1", AuditEventType.DOCUMENT_REGISTERED,
            Instant.parse("2026-07-29T14:09:00Z"), "Document registered", "Prototype event", null);
        return new PrototypeNotarizationRecord(1, "TX-1", status, hash,
            Instant.parse("2026-07-29T13:46:00Z"), Instant.parse("2026-07-29T14:09:00Z"),
            List.of(participant), notary, List.of(event));
    }
    private SignedPrototypeRegistryRecord signed(PrototypeNotarizationRecord payload, String keyId, String algorithm) throws Exception {
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(keyPair.getPrivate());
        signature.update(canonical.canonicalize(payload));
        return new SignedPrototypeRegistryRecord(payload, algorithm, keyId,
            Base64.getEncoder().encodeToString(signature.sign()));
    }
    private void writeRecord(String hash, SignedPrototypeRegistryRecord envelope) throws Exception {
        Path records = directory.resolve("records");
        Files.createDirectories(records);
        mapper.writeValue(records.resolve(hash + ".json").toFile(), envelope);
        var index = mapper.createObjectNode();
        index.put("schemaVersion", 1);
        index.putArray("records").addObject().put("documentSha256", hash).put("recordPath", "records/" + hash + ".json");
        mapper.writeValue(directory.resolve("index.json").toFile(), index);
    }
    private void writeEmptyIndex() {
        try {
            Files.writeString(directory.resolve("index.json"), "{\"schemaVersion\":1,\"records\":[]}");
        } catch (Exception exception) { throw new RuntimeException(exception); }
    }
    private SignatureResult signature(String id, String fingerprint) {
        SignatureResult signature = mock(SignatureResult.class);
        CertificateResult certificate = mock(CertificateResult.class);
        SignerIdentity signer = mock(SignerIdentity.class);
        SigningTime time = mock(SigningTime.class);
        when(signature.id()).thenReturn(id);
        when(signature.certificate()).thenReturn(certificate);
        when(signature.signer()).thenReturn(signer);
        when(signature.signingTime()).thenReturn(time);
        when(certificate.sha256Fingerprint()).thenReturn(fingerprint);
        when(signer.entityType()).thenReturn(SignerEntityType.PERSON);
        return signature;
    }
}

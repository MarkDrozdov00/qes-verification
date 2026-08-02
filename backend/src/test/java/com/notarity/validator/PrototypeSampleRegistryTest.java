package com.notarity.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.notarity.validator.api.ApiModels.*;
import com.notarity.validator.registry.*;
import com.notarity.validator.registry.PrototypeRegistryModels.*;
import com.notarity.validator.validation.dss.PrototypeRegistryService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrototypeSampleRegistryTest {
    private static final String HASH = "8a1fe8ca8dbf5a35505d04bf7fa05b2b0f4b801c27b0b8d3f7a12735f3a677b7";
    private static final String SWISSCOM = "ac7ff1f8b7e2245d9ccaa7160b683ea4e8462d137aa70701db2898d12d54b2c5";
    private static final String A_TRUST = "a74bed9e0bec8c1e3856404379dca12de10ef45e5be4c68c8518be04aeda1945";
    private static final String PRIMESIGN_SEAL = "6766efd511e59a57f1bc0eeb2532ea9702772fb478136ddfc9bea08e9b6e4e58";

    @Test
    void committedSampleRecordHasValidReceiptAndMatchesAllRealCertificateFingerprints() {
        var result = service().lookup(HASH, List.of(
            signature("sample-signature-1", SWISSCOM, SignerEntityType.PERSON),
            signature("sample-signature-2", A_TRUST, SignerEntityType.PERSON),
            signature("sample-signature-3", PRIMESIGN_SEAL, SignerEntityType.ORGANIZATION)
        ), Instant.parse("2026-08-01T12:00:00Z")).notarity();

        assertThat(result.status()).isEqualTo(NotarityStatus.EXACT_MATCH);
        assertThat(result.documentHashMatch()).isTrue();
        assertThat(result.registryReceipt().status()).isEqualTo(TernaryStatus.VALID);
        assertThat(result.registryReceipt().algorithm()).isEqualTo("Ed25519");
        assertThat(result.registryReceipt().keyId()).isEqualTo("prototype-registry-2026-01");
        assertThat(result.transaction().id()).isEqualTo("NOT-DEMO-POA-001");
        assertThat(result.transaction().status()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(result.participantMatches()).extracting(ParticipantMatch::signatureId)
            .containsExactly("sample-signature-1", "sample-signature-2", "sample-signature-3");
        assertThat(result.participantMatches()).allMatch(match -> match.status() == ParticipantStatus.MATCHED);
        assertThat(result.notary()).isNull();
        assertThat(result.notarizationAuthority().name()).isEqualTo("Notarity GmbH");
        assertThat(result.notarizationAuthority().entityType()).isEqualTo(SignerEntityType.ORGANIZATION);
        assertThat(result.notarizationAuthority().signatureId()).isEqualTo("sample-signature-3");
        assertThat(result.notarizationAuthority().certificateFingerprintMatch()).isTrue();
        assertThat(result.notarizationAuthority().qualification()).isEqualTo("Qualified electronic seal");
        assertThat(result.notarizationAuthority().trustProvider()).isEqualTo("PrimeSign");
        assertThat(result.notarizationAuthority().sealTrustedAtSigningTime()).isTrue();
        assertThat(result.auditTrail()).filteredOn(event -> event.evidenceSource() == EvidenceSource.EMBEDDED_DOCUMENT
                && "Electronic seal embedded".equals(event.title()))
            .singleElement().satisfies(event -> assertThat(event.actor().displayName()).isEqualTo("Notarity GmbH"));
        assertThat(result.auditTrail()).filteredOn(event ->
            event.evidenceSource() == EvidenceSource.PROTOTYPE_NOTARITY_REGISTRY)
            .allMatch(event -> event.status() == AuditStatus.VERIFIED);
    }

    @Test
    void runtimeVerificationDoesNotRequirePrototypePrivateKey() {
        assertThat(service().lookup(HASH, List.of(), Instant.now()).notarity().registryReceipt().status())
            .isEqualTo(TernaryStatus.VALID);
    }

    @Test
    void productionRegistryLoadsOneRecordAndOnePublicVerificationKey() {
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        var registry = new JsonPrototypeNotarityRegistry(mapper, "../data/notarization-records");
        var keys = new PrototypeRegistryKeyStore(mapper,
            new FileSystemResource(Path.of("../data/prototype-registry-public-keys.json")));
        assertThat(registry.recordCount()).isEqualTo(1);
        assertThat(keys.keyCount()).isEqualTo(1);
    }

    @Test
    void unrelatedDocumentHashDoesNotReceiveTheSampleRecord() {
        assertThat(service().lookup("f".repeat(64), List.of(
            signature("unrelated", PRIMESIGN_SEAL, SignerEntityType.ORGANIZATION)), Instant.now())
            .notarity().status()).isEqualTo(NotarityStatus.NO_RECORD);
    }

    private PrototypeRegistryService service() {
        ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
        var canonical = new CanonicalJsonService(mapper);
        var keys = new PrototypeRegistryKeyStore(mapper,
            new FileSystemResource(Path.of("../data/prototype-registry-public-keys.json")));
        return new PrototypeRegistryService(
            new JsonPrototypeNotarityRegistry(mapper, "../data/notarization-records"),
            new Ed25519RegistryReceiptVerifier(canonical, keys),
            new CertificateFingerprintParticipantMatcher());
    }

    private SignatureResult signature(String id, String fingerprint, SignerEntityType entityType) {
        SignatureResult signature = mock(SignatureResult.class);
        CertificateResult certificate = mock(CertificateResult.class);
        SignerIdentity signer = mock(SignerIdentity.class);
        SigningTime time = mock(SigningTime.class);
        SignatureEvidence evidence = mock(SignatureEvidence.class);
        TrustServiceResult trust = mock(TrustServiceResult.class);
        when(signature.id()).thenReturn(id);
        when(signature.certificate()).thenReturn(certificate);
        when(signature.signer()).thenReturn(signer);
        when(signature.signingTime()).thenReturn(time);
        when(time.bestAvailableTime()).thenReturn(Instant.parse("2026-07-29T14:09:00Z"));
        when(signature.signature()).thenReturn(evidence);
        when(signature.trustService()).thenReturn(trust);
        when(certificate.sha256Fingerprint()).thenReturn(fingerprint);
        when(certificate.chainStatus()).thenReturn(ChainStatus.TRUSTED);
        when(certificate.statusAtSigningTime()).thenReturn(CertificateStatus.VALID);
        when(signer.entityType()).thenReturn(entityType);
        when(signer.displayName()).thenReturn(entityType == SignerEntityType.ORGANIZATION
            ? "Notarity GmbH" : "Sebastian Wodniansky-Wildenfeld");
        when(evidence.qualificationLabel()).thenReturn(entityType == SignerEntityType.ORGANIZATION
            ? "Qualified electronic seal" : "Qualified electronic signature");
        when(trust.providerName()).thenReturn(entityType == SignerEntityType.ORGANIZATION ? "PrimeSign" : null);
        return signature;
    }
}

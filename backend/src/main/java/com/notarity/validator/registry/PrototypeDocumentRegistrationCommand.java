package com.notarity.validator.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notarity.validator.document.DocumentInspectionService;
import com.notarity.validator.document.DocumentMetadata;
import com.notarity.validator.registry.PrototypeRegistryModels.*;
import com.notarity.validator.validation.SignatureValidationService;
import com.notarity.validator.validation.ValidationOptions;
import org.apache.pdfbox.Loader;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.registry-registration.enabled", havingValue = "true")
public class PrototypeDocumentRegistrationCommand implements ApplicationRunner {
    private final SignatureValidationService validation;
    private final CanonicalJsonService canonicalJson;
    private final ObjectMapper mapper;
    private final Path document;
    private final Path template;
    private final Path outputDirectory;
    private final Path privateKey;
    private final String keyId;

    public PrototypeDocumentRegistrationCommand(SignatureValidationService validation, CanonicalJsonService canonicalJson,
        ObjectMapper mapper,
        @Value("${app.registry-registration.document}") Path document,
        @Value("${app.registry-registration.template}") Path template,
        @Value("${app.registry-registration.output-directory:../data/notarization-records}") Path outputDirectory,
        @Value("${app.registry-registration.private-key:../local/keys/prototype-registry-private.pem}") Path privateKey,
        @Value("${app.registry-registration.key-id:prototype-registry-2026-01}") String keyId) {
        this.validation = validation;
        this.canonicalJson = canonicalJson;
        this.mapper = mapper;
        this.document = document;
        this.template = template;
        this.outputDirectory = outputDirectory;
        this.privateKey = privateKey;
        this.keyId = keyId;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!Files.isRegularFile(privateKey)) throw new IllegalStateException(
            "Prototype registry private key is missing; generate it explicitly before registration.");
        byte[] bytes = Files.readAllBytes(document);
        String hash = DocumentInspectionService.sha256(bytes);
        DocumentMetadata metadata;
        try (var pdf = Loader.loadPDF(bytes)) {
            metadata = new DocumentMetadata(DocumentInspectionService.sanitize(document.getFileName().toString()),
                "application/pdf", bytes.length, hash, pdf.getNumberOfPages(), false);
        }
        var validationResult = validation.validate(bytes, metadata, new ValidationOptions(true, "en"));
        List<String> documentFingerprints = validationResult.signatures().stream()
            .map(signature -> CertificateFingerprintParticipantMatcher.normalize(
                signature.certificate().sha256Fingerprint())).toList();
        PrototypeNotarizationRecord source = mapper.readValue(template.toFile(), PrototypeNotarizationRecord.class);
        PrototypeNotarizationRecord payload = new PrototypeNotarizationRecord(source.schemaVersion(),
            source.transactionId(), source.status(), hash, source.createdAt(), source.completedAt(),
            source.participants(), source.notary(), source.auditTrail());
        for (PrototypeParticipant participant : payload.participants()) {
            requireFingerprint(participant.certificateSha256Fingerprint(), documentFingerprints, "participant");
        }
        if (payload.notary() != null) {
            requireFingerprint(payload.notary().certificateSha256Fingerprint(), documentFingerprints, "notary");
        }

        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(readPrivateKey());
        signer.update(canonicalJson.canonicalize(payload));
        var envelope = new SignedPrototypeRegistryRecord(payload, "Ed25519", keyId,
            Base64.getEncoder().encodeToString(signer.sign()));
        Path records = outputDirectory.resolve("records");
        Files.createDirectories(records);
        Path output = records.resolve(hash + ".json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), envelope);
        updateIndex(hash, outputDirectory.relativize(output).toString().replace('\\', '/'));
        System.out.printf("documentHash=%s%ntransactionId=%s%nparticipants=%d%nkeyId=%s%noutput=%s%n",
            hash, payload.transactionId(), payload.participants().size(), keyId, output.toAbsolutePath());
        System.exit(0);
    }

    private java.security.PrivateKey readPrivateKey() throws Exception {
        String pem = Files.readString(privateKey, StandardCharsets.UTF_8);
        byte[] encoded = Base64.getDecoder().decode(
            pem.replaceAll("-----[^-]+-----", "").replaceAll("\\s+", ""));
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }
    private void requireFingerprint(String value, List<String> actual, String role) {
        String normalized = CertificateFingerprintParticipantMatcher.normalize(value);
        if (normalized.isEmpty() || !actual.contains(normalized)) {
            throw new IllegalArgumentException("The " + role + " fingerprint is not present in the PDF signatures.");
        }
    }
    private void updateIndex(String hash, String relativePath) throws Exception {
        Path index = outputDirectory.resolve("index.json");
        var root = Files.isRegularFile(index) ? mapper.readTree(index.toFile()) : mapper.createObjectNode();
        ((com.fasterxml.jackson.databind.node.ObjectNode) root).put("schemaVersion", 1);
        var records = ((com.fasterxml.jackson.databind.node.ObjectNode) root).withArray("records");
        for (var record : records) {
            if (hash.equals(record.path("documentSha256").asText())) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(index.toFile(), root);
                return;
            }
        }
        var entry = records.addObject();
        entry.put("documentSha256", hash);
        entry.put("recordPath", relativePath);
        mapper.writerWithDefaultPrettyPrinter().writeValue(index.toFile(), root);
    }
}

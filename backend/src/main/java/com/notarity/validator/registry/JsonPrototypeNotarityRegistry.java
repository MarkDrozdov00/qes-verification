package com.notarity.validator.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notarity.validator.registry.PrototypeRegistryModels.SignedPrototypeRegistryRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

@Component
public class JsonPrototypeNotarityRegistry implements PrototypeNotarityRegistry {
    private final ObjectMapper mapper;
    private final Path root;

    public JsonPrototypeNotarityRegistry(ObjectMapper mapper,
        @Value("${app.prototype-registry.directory:../data/notarization-records}") String root) {
        this.mapper = mapper;
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public Optional<SignedPrototypeRegistryRecord> findByDocumentHash(String sha256) {
        String normalized = normalizeHash(sha256);
        if (!normalized.matches("[0-9a-f]{64}")) return Optional.empty();
        Path index = root.resolve("index.json").normalize();
        if (!index.startsWith(root) || !Files.isRegularFile(index)) return Optional.empty();
        try {
            JsonNode json = mapper.readTree(index.toFile());
            for (JsonNode entry : json.path("records")) {
                if (!normalized.equals(normalizeHash(entry.path("documentSha256").asText()))) continue;
                String relative = entry.path("recordPath").asText("records/" + normalized + ".json");
                Path recordPath = root.resolve(relative).normalize();
                if (!recordPath.startsWith(root) || !Files.isRegularFile(recordPath)) return Optional.empty();
                return Optional.of(mapper.readValue(recordPath.toFile(), SignedPrototypeRegistryRecord.class));
            }
            return Optional.empty();
        } catch (Exception exception) {
            throw new RegistryReadException("The prototype registry could not be read", exception);
        }
    }

    static String normalizeHash(String value) {
        return value == null ? "" : value.replace(":", "").replace(" ", "").toLowerCase(Locale.ROOT);
    }
    public boolean available() { return Files.isRegularFile(root.resolve("index.json")); }

    public int recordCount() {
        Path index = root.resolve("index.json");
        if (!Files.isRegularFile(index)) return 0;
        try {
            return mapper.readTree(index.toFile()).path("records").size();
        } catch (Exception exception) {
            return 0;
        }
    }

    public static final class RegistryReadException extends RuntimeException {
        RegistryReadException(String message, Throwable cause) { super(message, cause); }
    }
}

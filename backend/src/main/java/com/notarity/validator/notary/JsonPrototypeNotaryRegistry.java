package com.notarity.validator.notary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notarity.validator.notary.PrototypeNotaryRegistryModels.Dataset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.time.Duration;
import java.time.Instant;

@Component
public class JsonPrototypeNotaryRegistry {
    private final Dataset dataset;
    private final boolean available;
    private final int staleAfterDays;

    public JsonPrototypeNotaryRegistry(ObjectMapper mapper, String location) {
        this(mapper, location, 30);
    }

    @Autowired
    public JsonPrototypeNotaryRegistry(ObjectMapper mapper,
        @Value("${app.notary-registry.file:../data/notaries.json}") String location,
        @Value("${app.notary-registry.stale-after-days:30}") int staleAfterDays) {
        Dataset loaded = null;
        boolean valid = false;
        try {
            Path path = Path.of(location).toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                loaded = mapper.readValue(path.toFile(), Dataset.class);
                valid = loaded.schemaVersion() == 2 && loaded.dataset() != null && loaded.notaries() != null
                    && loaded.dataset().recordCount() == loaded.notaries().size();
            }
        } catch (Exception ignored) {
            // Registry availability is surfaced as a safe result by the matcher.
        }
        this.dataset = loaded;
        this.available = valid;
        this.staleAfterDays = staleAfterDays;
    }

    public boolean available() { return available; }
    public List<PrototypeNotaryRegistryModels.NotaryEntry> entries() {
        return available ? List.copyOf(dataset.notaries()) : List.of();
    }
    public Dataset dataset() { return dataset; }
    public boolean stale() {
        return available && (dataset.dataset().retrievedAt() == null
            || dataset.dataset().retrievedAt().isBefore(Instant.now().minus(Duration.ofDays(staleAfterDays))));
    }
    public boolean degraded() { return available && (dataset.notaries().isEmpty() || stale()); }
}

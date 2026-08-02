package com.notarity.validator.notary;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PrototypeNotaryRegistryModels {
    private PrototypeNotaryRegistryModels() {}

    public record Dataset(int schemaVersion, DatasetMetadata dataset, List<NotaryEntry> notaries) {}
    public record DatasetMetadata(String name, String datasetType, String countryCode, String sourceName,
                                  String sourceUrl, Instant retrievedAt, int recordCount, boolean complete,
                                  String collectionMethod) {}
    public record Authorization(String type, LocalDate validFrom, LocalDate validUntil) {}
    public record SourceReference(String sourceRecordUrl, Instant retrievedAt) {}
    public record NotaryEntry(String id, String fullName, String givenName, String surname,
                              String professionalTitle, String normalizedName, String countryCode,
                              String nCode, String registryStatus, List<Authorization> authorizations,
                              List<String> certificateReferences, SourceReference sourceReference) {}
}

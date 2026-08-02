package com.notarity.validator.notary;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.notarity.validator.api.ApiModels.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrototypeNotaryMatchingServiceTest {
    @TempDir Path directory;

    @Test void exactPersonNameAndCountryAreConfirmed() throws Exception {
        var result = service(entries(entry("AT-1", "Dr. Anna Example", "AT")))
            .match(List.of(person("sig-1", "Anna Example", "AT")));
        assertThat(result.status()).isEqualTo(NotaryStatus.CONFIRMED_MATCH);
        assertThat(result.confidence()).isEqualTo(Confidence.HIGH);
        assertThat(result.signatureId()).isEqualTo("sig-1");
        assertThat(result.matchedFields()).containsExactly("normalizedName", "countryCode", "authorizationPeriod");
    }

    @Test void matchingIsCaseInsensitive() throws Exception {
        assertThat(service(entries(entry("AT-1", "Dr. Anna Example", "AT")))
            .match(List.of(person("sig-1", "ANNA EXAMPLE", "at"))).status())
            .isEqualTo(NotaryStatus.CONFIRMED_MATCH);
    }

    @Test void diacriticsAreNormalized() throws Exception {
        assertThat(service(entries(entry("AT-2", "Mag. Éva Grün", "AT")))
            .match(List.of(person("sig-1", "Eva Grun", "AT"))).status())
            .isEqualTo(NotaryStatus.CONFIRMED_MATCH);
    }

    @Test void hyphenatedSurnamesAreNormalized() throws Exception {
        assertThat(service(entries(entry("AT-3", "Eva Grün-Wald", "AT")))
            .match(List.of(person("sig-1", "Eva Grun Wald", "AT"))).status())
            .isEqualTo(NotaryStatus.CONFIRMED_MATCH);
    }

    @Test void organizationSignerIsSkipped() throws Exception {
        var result = service(entries(entry("AT-1", "Notarity GmbH", "AT")))
            .match(List.of(signature("seal-1", "Notarity GmbH", "AT", SignerEntityType.ORGANIZATION)));
        assertThat(result.status()).isEqualTo(NotaryStatus.NOT_CHECKED);
    }

    @Test void unknownPersonHasNoMatch() throws Exception {
        assertThat(service(entries(entry("AT-1", "Anna Example", "AT")))
            .match(List.of(person("sig-1", "Sebastian Example", "AT"))).status())
            .isEqualTo(NotaryStatus.NO_MATCH);
    }

    @Test void duplicateBestNamesAreAmbiguous() throws Exception {
        var result = service(entries(
            entry("AT-1", "Anna Example", "AT"), entry("AT-2", "Dr. Anna Example", "AT")))
            .match(List.of(person("sig-1", "Anna Example", "AT")));
        assertThat(result.status()).isEqualTo(NotaryStatus.AMBIGUOUS);
        assertThat(result.confidence()).isEqualTo(Confidence.HIGH);
    }

    @Test void countryMismatchDoesNotMatch() throws Exception {
        assertThat(service(entries(entry("AT-1", "Anna Example", "AT")))
            .match(List.of(person("sig-1", "Anna Example", "DE"))).status())
            .isEqualTo(NotaryStatus.NO_MATCH);
    }

    @Test void notaryMatchDoesNotRequireANotarityDocumentRecord() throws Exception {
        // The matcher accepts signer evidence only: no hash, transaction, or Notarity receipt participates.
        assertThat(service(entries(entry("AT-1", "Anna Example", "AT")))
            .match(List.of(person("sig-1", "Anna Example", "AT"))).status())
            .isEqualTo(NotaryStatus.CONFIRMED_MATCH);
    }

    @Test void notarityDesignationCannotCreateANotaryRegistryMatch() throws Exception {
        // A designated Notarity signer that is absent from this dataset remains a truthful no-match.
        assertThat(service(entries(entry("AT-1", "Anna Example", "AT")))
            .match(List.of(person("notarity-designated", "Different Person", "AT"))).status())
            .isEqualTo(NotaryStatus.NO_MATCH);
    }

    @Test void authorizationExpiredBeforeSigningDoesNotMatch() throws Exception {
        assertThat(service(entries(entryWithAuthorization("AT-1", "Anna Example", "AT", "2020-01-01", "2025-12-31")))
            .match(List.of(person("sig-1", "Anna Example", "AT"))).status()).isEqualTo(NotaryStatus.NO_MATCH);
    }

    @Test void authorizationStartingAfterSigningDoesNotMatch() throws Exception {
        assertThat(service(entries(entryWithAuthorization("AT-1", "Anna Example", "AT", "2027-01-01", null)))
            .match(List.of(person("sig-1", "Anna Example", "AT"))).status()).isEqualTo(NotaryStatus.NO_MATCH);
    }

    @Test void missingAuthorizationDatesProducesPossibleMatch() throws Exception {
        assertThat(service(entries(entryWithAuthorization("AT-1", "Anna Example", "AT", null, null)))
            .match(List.of(person("sig-1", "Anna Example", "AT"))).status()).isEqualTo(NotaryStatus.POSSIBLE_MATCH);
    }

    @Test void staleSnapshotAddsWarning() throws Exception {
        Path file = directory.resolve("stale-notaries.json");
        Files.writeString(file, entries(entry("AT-1", "Anna Example", "AT")));
        var registry = new JsonPrototypeNotaryRegistry(JsonMapper.builder().findAndAddModules().build(), file.toString(), 0);
        var result = new PrototypeNotaryMatchingService(registry, new NotaryIdentityNormalizer())
            .match(List.of(person("sig-1", "Anna Example", "AT")));
        assertThat(result.warnings()).extracting(ValidationMessage::code).contains("NOTARY_SNAPSHOT_STALE");
        assertThat(result.registry().stale()).isTrue();
    }

    private PrototypeNotaryMatchingService service(String json) throws Exception {
        Path file = directory.resolve("notaries-" + System.nanoTime() + ".json");
        Files.writeString(file, json);
        return new PrototypeNotaryMatchingService(
            new JsonPrototypeNotaryRegistry(JsonMapper.builder().findAndAddModules().build(), file.toString()),
            new NotaryIdentityNormalizer());
    }

    private String entries(String... entries) {
        return "{\"schemaVersion\":2,\"dataset\":{\"name\":\"Snapshot\",\"datasetType\":\"STATIC_PUBLIC_SNAPSHOT\","
            + "\"countryCode\":\"AT\",\"sourceName\":\"Public directory\",\"sourceUrl\":\"https://example.test\","
            + "\"retrievedAt\":\"2026-08-01T00:00:00Z\",\"recordCount\":" + entries.length + ",\"complete\":false,"
            + "\"collectionMethod\":\"TEST_FIXTURE\"},\"notaries\":[" + String.join(",", entries) + "]}";
    }

    private String entry(String id, String name, String country) {
        return entryWithAuthorization(id, name, country, "2024-01-01", null);
    }

    private String entryWithAuthorization(String id, String name, String country, String validFrom, String validUntil) {
        String from = validFrom == null ? "null" : "\"" + validFrom + "\"";
        String until = validUntil == null ? "null" : "\"" + validUntil + "\"";
        return "{\"id\":\"" + id + "\",\"fullName\":\"" + name + "\","
            + "\"givenName\":null,\"surname\":null,\"professionalTitle\":null,\"normalizedName\":null,"
            + "\"countryCode\":\"" + country + "\",\"nCode\":null,\"registryStatus\":\"ACTIVE\","
            + "\"authorizations\":[{\"type\":\"NOTARIAL_SIGNATURE\",\"validFrom\":" + from + ",\"validUntil\":" + until + "}],"
            + "\"certificateReferences\":[],\"sourceReference\":{\"sourceRecordUrl\":\"https://example.test\",\"retrievedAt\":\"2026-08-01T00:00:00Z\"}}";
    }

    private SignatureResult person(String id, String name, String country) {
        return signature(id, name, country, SignerEntityType.PERSON);
    }

    private SignatureResult signature(String id, String name, String country, SignerEntityType type) {
        SignatureResult signature = mock(SignatureResult.class);
        SignerIdentity signer = mock(SignerIdentity.class);
        when(signature.id()).thenReturn(id);
        when(signature.signer()).thenReturn(signer);
        when(signer.displayName()).thenReturn(name);
        when(signer.countryCode()).thenReturn(country);
        when(signer.entityType()).thenReturn(type);
        SigningTime signingTime = mock(SigningTime.class);
        when(signingTime.bestAvailableTime()).thenReturn(Instant.parse("2026-07-29T14:00:00Z"));
        when(signature.signingTime()).thenReturn(signingTime);
        return signature;
    }
}

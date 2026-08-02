package com.notarity.validator.notary;

import com.notarity.validator.api.ApiModels.*;
import com.notarity.validator.notary.PrototypeNotaryRegistryModels.NotaryEntry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
public class PrototypeNotaryMatchingService {
    private static final String PROVIDER = "Austrian notarial-signature directory snapshot";
    private static final String DISCLAIMER = "This result uses a static, incomplete snapshot of publicly accessible directory records and may not reflect later changes.";

    private final JsonPrototypeNotaryRegistry registry;
    private final NotaryIdentityNormalizer names;

    public PrototypeNotaryMatchingService(JsonPrototypeNotaryRegistry registry, NotaryIdentityNormalizer names) {
        this.registry = registry;
        this.names = names;
    }

    public NotaryCheckResult match(List<SignatureResult> signatures) {
        RegistryInfo info = registryInfo();
        if (!registry.available()) return new NotaryCheckResult(NotaryStatus.REGISTRY_UNAVAILABLE, Confidence.NONE,
            null, null, null, List.of(), info, "The prototype notary registry is unavailable.", DISCLAIMER,
            List.of(new ValidationMessage("NOTARY_REGISTRY_UNAVAILABLE", MessageSeverity.WARNING,
                "Notary registry unavailable", "The local prototype notary dataset could not be loaded.", null, null)));

        List<SignerCandidate> people = signatures == null ? List.of() : signatures.stream()
            .filter(signature -> signature != null && signature.signer() != null
                && signature.signer().entityType() == SignerEntityType.PERSON)
            .map(this::candidate).toList();
        if (people.isEmpty()) return notChecked(info);

        List<Match> matches = new ArrayList<>();
        for (SignerCandidate person : people) {
            for (NotaryEntry entry : registry.entries()) {
                Match match = compare(person, entry);
                if (match.confidence() != Confidence.NONE) matches.add(match);
            }
        }
        if (matches.isEmpty()) return noMatch(people.getFirst(), info);

        Confidence best = matches.stream().map(Match::confidence)
            .min(Comparator.comparingInt(this::rank)).orElse(Confidence.NONE);
        List<Match> bestMatches = matches.stream().filter(match -> match.confidence() == best).toList();
        if (bestMatches.size() > 1) return ambiguous(bestMatches.getFirst(), info);
        Match selected = bestMatches.getFirst();
        NotaryStatus status = selected.confidence() == Confidence.HIGH
            ? NotaryStatus.CONFIRMED_MATCH : NotaryStatus.POSSIBLE_MATCH;
        List<ValidationMessage> warnings = registry.stale() ? staleWarning() : List.of();
        return result(status, selected.confidence(), selected, info,
            status == NotaryStatus.CONFIRMED_MATCH
                ? "The signer identity matched a public directory record and the recorded authorization covered the signing date."
                : "The signer identity matched the snapshot, but the available public record was insufficient for a definitive date-specific conclusion.", warnings);
    }

    private Match compare(SignerCandidate signer, NotaryEntry entry) {
        String registryName = names.normalize(entry.normalizedName() == null ? entry.fullName() : entry.normalizedName());
        boolean exactName = !signer.normalizedName().isEmpty() && signer.normalizedName().equals(registryName);
        boolean closeName = !exactName && close(signer.normalizedName(), registryName);
        if (!exactName && !closeName) return Match.none(signer, entry);
        if (signer.countryCode() != null && entry.countryCode() != null
            && !signer.countryCode().equalsIgnoreCase(entry.countryCode())) return Match.none(signer, entry);

        List<String> fields = new ArrayList<>();
        fields.add("normalizedName");
        boolean countryMatch = signer.countryCode() != null && entry.countryCode() != null
            && signer.countryCode().equalsIgnoreCase(entry.countryCode());
        if (countryMatch) fields.add("countryCode");
        AuthorizationState authorization = authorizationAt(entry, signer.signingDate());
        if (authorization == AuthorizationState.OUTSIDE_PERIOD) return Match.none(signer, entry);
        if (entry.certificateReferences() != null && !entry.certificateReferences().isEmpty()
            && signer.certificateFingerprint() != null) {
            boolean certificateMatch = entry.certificateReferences().stream()
                .map(this::normalizeFingerprint).anyMatch(normalizeFingerprint(signer.certificateFingerprint())::equals);
            if (!certificateMatch) return Match.none(signer, entry);
            fields.add("certificateReference");
        }
        if (authorization == AuthorizationState.ACTIVE) fields.add("authorizationPeriod");
        Confidence confidence = exactName && countryMatch && authorization == AuthorizationState.ACTIVE
            ? Confidence.HIGH : exactName && countryMatch ? Confidence.MEDIUM : Confidence.LOW;
        return new Match(signer, entry, confidence, List.copyOf(fields));
    }

    private boolean close(String left, String right) {
        if (left.isBlank() || right.isBlank()) return false;
        String a = left.replace(" ", ""), b = right.replace(" ", "");
        return levenshtein(a, b) <= Math.max(1, Math.min(2, Math.max(a.length(), b.length()) / 8));
    }

    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1];
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) current[j] = Math.min(
                Math.min(current[j - 1] + 1, previous[j] + 1),
                previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1));
            previous = current;
        }
        return previous[right.length()];
    }

    private SignerCandidate candidate(SignatureResult signature) {
        SignerIdentity signer = signature.signer();
        String display = signer.displayName();
        String sourceName = signer.givenName() != null && signer.surname() != null
            ? signer.givenName() + " " + signer.surname() : display;
        Instant time = signature.signingTime() == null ? null : signature.signingTime().bestAvailableTime();
        LocalDate signingDate = time == null ? null : time.atZone(ZoneOffset.UTC).toLocalDate();
        String fingerprint = signature.certificate() == null ? null : signature.certificate().sha256Fingerprint();
        return new SignerCandidate(signature.id(), display, names.normalize(sourceName), upper(signer.countryCode()),
            signer.organization(), signingDate, fingerprint);
    }

    private NotaryCheckResult result(NotaryStatus status, Confidence confidence, Match match,
                                     RegistryInfo info, String explanation, List<ValidationMessage> warnings) {
        NotaryEntry entry = match.entry();
        return new NotaryCheckResult(status, confidence, match.signer().signatureId(),
            new SearchedIdentity(match.signer().displayName(), match.signer().normalizedName(), match.signer().countryCode()),
            new MatchedNotary(entry.id(), entry.fullName(), entry.countryCode(), countryName(entry), null,
                null, PROVIDER, registryStatus(entry.registryStatus()),
                entry.sourceReference() == null ? null : entry.sourceReference().sourceRecordUrl()),
            match.fields(), info, explanation, DISCLAIMER, warnings);
    }

    private NotaryCheckResult ambiguous(Match match, RegistryInfo info) {
        return result(NotaryStatus.AMBIGUOUS, match.confidence(), match, info,
            "More than one static snapshot entry matched the signer identity with the same confidence.", registry.stale() ? staleWarning() : List.of());
    }

    private NotaryCheckResult noMatch(SignerCandidate signer, RegistryInfo info) {
        return new NotaryCheckResult(NotaryStatus.NO_MATCH, Confidence.NONE, signer.signatureId(),
            new SearchedIdentity(signer.displayName(), signer.normalizedName(), signer.countryCode()), null,
            List.of(), info, "No named person signer matched the Austrian notarial-signature directory snapshot.", DISCLAIMER,
            registry.stale() ? staleWarning() : List.of());
    }

    private NotaryCheckResult notChecked(RegistryInfo info) {
        return new NotaryCheckResult(NotaryStatus.NOT_CHECKED, Confidence.NONE, null, null, null, List.of(), info,
            "No person signer identity was available for static snapshot matching.", DISCLAIMER,
            registry.stale() ? staleWarning() : List.of());
    }

    private RegistryInfo registryInfo() {
        var dataset = registry.dataset();
        var metadata = dataset == null ? null : dataset.dataset();
        return new RegistryInfo(PROVIDER, DatasetType.STATIC_PUBLIC_SNAPSHOT,
            metadata == null ? null : metadata.retrievedAt(), metadata == null ? false : metadata.complete(),
            metadata == null ? 0 : metadata.recordCount(), metadata == null ? null : metadata.sourceUrl(), registry.stale());
    }
    private String countryName(NotaryEntry entry) {
        return "AT".equalsIgnoreCase(entry.countryCode()) ? "Austria" : entry.countryCode();
    }
    private RegistryStatus registryStatus(String value) {
        try { return RegistryStatus.valueOf(value == null ? "UNKNOWN" : value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { return RegistryStatus.UNKNOWN; }
    }
    private int rank(Confidence value) {
        return switch (value) { case HIGH -> 0; case MEDIUM -> 1; case LOW -> 2; case NONE -> 3; };
    }
    private String upper(String value) { return value == null ? null : value.toUpperCase(Locale.ROOT); }
    private String normalizeFingerprint(String value) { return value == null ? "" : value.replaceAll("[:\\s]", "").toLowerCase(Locale.ROOT); }
    private List<ValidationMessage> staleWarning() { return List.of(new ValidationMessage("NOTARY_SNAPSHOT_STALE", MessageSeverity.WARNING,
        "Notary snapshot may be outdated", "The static public-directory snapshot is older than the configured freshness threshold.", null, null)); }
    private AuthorizationState authorizationAt(NotaryEntry entry, LocalDate signingDate) {
        if (entry.authorizations() == null || entry.authorizations().isEmpty() || signingDate == null) return AuthorizationState.UNKNOWN;
        boolean hasUsableDate = false;
        for (var authorization : entry.authorizations()) {
            if (authorization.validFrom() == null && authorization.validUntil() == null) continue;
            hasUsableDate = true;
            boolean afterStart = authorization.validFrom() == null || !signingDate.isBefore(authorization.validFrom());
            boolean beforeEnd = authorization.validUntil() == null || !signingDate.isAfter(authorization.validUntil());
            if (afterStart && beforeEnd) return AuthorizationState.ACTIVE;
        }
        return hasUsableDate ? AuthorizationState.OUTSIDE_PERIOD : AuthorizationState.UNKNOWN;
    }

    private record SignerCandidate(String signatureId, String displayName, String normalizedName,
                                   String countryCode, String organization, LocalDate signingDate,
                                   String certificateFingerprint) {}
    private enum AuthorizationState { ACTIVE, OUTSIDE_PERIOD, UNKNOWN }
    private record Match(SignerCandidate signer, NotaryEntry entry, Confidence confidence, List<String> fields) {
        static Match none(SignerCandidate signer, NotaryEntry entry) {
            return new Match(signer, entry, Confidence.NONE, List.of());
        }
    }
}

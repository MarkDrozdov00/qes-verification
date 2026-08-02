package com.notarity.validator.report;

import com.notarity.validator.api.ApiModels.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ReportPresentation {
    private static final Set<Qualification> QUALIFIED = Set.of(Qualification.QES, Qualification.QESEAL);
    private ReportPresentation() {}

    static List<StatusSummary> trustSummary(DocumentValidationResponse result) {
        return List.of(result.trustSummary().documentIntegrity(), result.trustSummary().embeddedSignatures(),
            qualification(result.signatures()), certificateTrust(result.signatures()), notary(result), notarity(result));
    }
    static StatusSummary qualification(List<SignatureResult> signatures) {
        if (signatures.isEmpty()) return card(CheckStatus.NOT_FOUND, "Signature level", "No signatures", "No supported embedded signatures were found.");
        if (signatures.stream().anyMatch(s -> s.signature().qualification() == Qualification.UNKNOWN)) return card(CheckStatus.INDETERMINATE, "Signature level", "Unable to determine", "eIDAS qualification could not be established for every embedded signature.");
        if (signatures.stream().allMatch(s -> QUALIFIED.contains(s.signature().qualification()))) {
            long seals = signatures.stream().filter(s -> s.signature().qualification() == Qualification.QESEAL).count(), people = signatures.size() - seals;
            List<String> parts = new ArrayList<>();
            if (people > 0) parts.add(people + " qualified signature" + (people == 1 ? "" : "s"));
            if (seals > 0) parts.add(seals + " qualified seal" + (seals == 1 ? "" : "s"));
            return card(CheckStatus.VALID, "Signature level", String.join(" and ", parts), "All embedded signatures meet a qualified eIDAS signature or seal level.");
        }
        if (signatures.stream().map(s -> s.signature().qualification()).distinct().count() == 1) return card(CheckStatus.VALID, "Signature level", qualificationLabel(signatures.getFirst()), "All embedded signatures share this eIDAS qualification level.");
        List<String> levels = signatures.stream().map(s -> s.signature().qualification()).distinct().map(level -> {
            long count = signatures.stream().filter(s -> s.signature().qualification() == level).count();
            String label = qualificationLabel(signatures.stream().filter(s -> s.signature().qualification() == level).findFirst().orElseThrow());
            return count + " " + label.toLowerCase(Locale.ROOT) + (count == 1 || label.endsWith("s") ? "" : "s");
        }).toList();
        return card(CheckStatus.WARNING, "Signature level", String.join(" and ", levels), "The document contains signatures with different eIDAS qualification levels.");
    }
    static StatusSummary certificateTrust(List<SignatureResult> signatures) {
        if (signatures.isEmpty()) return card(CheckStatus.NOT_CHECKED, "Certificate trust", "Not checked", "There are no signer certificates to evaluate.");
        long trusted = signatures.stream().filter(s -> s.certificate().chainStatus() == ChainStatus.TRUSTED).count();
        if (trusted == signatures.size()) return card(CheckStatus.VALID, "Certificate trust", "Trusted EU certificate chains", "All signer certificates were trusted through authenticated EU Trusted List services at the relevant signing time.");
        if (trusted > 0) return card(CheckStatus.WARNING, "Certificate trust", "Partially established", "Trust was established for " + trusted + " of " + signatures.size() + " signer certificate chains.");
        return card(CheckStatus.INDETERMINATE, "Certificate trust", "Unable to determine", "Certificate-chain trust could not be established for the embedded signatures.");
    }
    static StatusSummary notary(DocumentValidationResponse result) {
        NotaryCheckResult check = result.notaryCheck(); if (check == null) return result.trustSummary().notaryRegistry();
        return switch (check.status()) {
            case CONFIRMED_MATCH -> card(CheckStatus.VALID, "Notary", "Austrian notary snapshot match", "The signer identity matched a public directory record and the recorded authorization covered the signing date.");
            case POSSIBLE_MATCH -> card(CheckStatus.WARNING, "Notary", "Possible Austrian notary match", "Available public evidence was insufficient for a date-specific conclusion.");
            case NO_MATCH -> card(CheckStatus.NOT_FOUND, "Notary", "No match in current snapshot", "No named person signer matched the Austrian notarial-signature directory snapshot.");
            default -> result.trustSummary().notaryRegistry();
        };
    }
    static StatusSummary notarity(DocumentValidationResponse result) {
        NotarityCheckResult check = result.notarityCheck(); if (check == null) return result.trustSummary().notarityRecord();
        if (check.status() == NotarityStatus.EXACT_MATCH) return card(CheckStatus.VALID, "Notarity record", "Prototype record verified", "The exact document hash, registered signer certificates, and signed prototype registry receipt all match.");
        if (check.status() == NotarityStatus.NO_RECORD) return card(CheckStatus.NOT_FOUND, "Notarity record", "No Notarity record found", "The document fingerprint was not found in the prototype registry.");
        return result.trustSummary().notarityRecord();
    }
    static String qualificationLabel(SignatureResult signature) {
        if (signature.signature().qualification() == Qualification.UNKNOWN) return "Unable to determine";
        String label = signature.signature().qualificationLabel();
        return label == null || label.isBlank() ? title(signature.signature().qualification().name()) : label;
    }
    static String entityLabel(SignatureResult signature) { return signature.signer().entityType() == SignerEntityType.ORGANIZATION ? "Organization" : signature.signer().entityType() == SignerEntityType.PERSON ? "Person" : "Signer"; }
    static String status(CheckStatus status) { return title(status.name()); }
    static String statusForChain(ChainStatus status) { return switch (status) { case TRUSTED -> "Trusted at signing time"; case INCOMPLETE -> "Certificate chain incomplete"; case UNTRUSTED -> "Untrusted"; case UNKNOWN -> "Unable to determine"; }; }
    private static StatusSummary card(CheckStatus status, String label, String value, String description) { return new StatusSummary(status, label, value, description); }
    private static String title(String value) { String lower = value.replace('_', ' ').toLowerCase(Locale.ROOT); return Character.toUpperCase(lower.charAt(0)) + lower.substring(1); }
}

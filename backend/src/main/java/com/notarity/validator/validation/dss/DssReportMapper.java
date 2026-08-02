package com.notarity.validator.validation.dss;

import com.notarity.validator.api.ApiModels.*;
import com.notarity.validator.document.DocumentMetadata;
import eu.europa.esig.dss.diagnostic.DiagnosticData;
import eu.europa.esig.dss.simplereport.SimpleReport;
import eu.europa.esig.dss.validation.reports.Reports;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.notarity.validator.trust.TrustProperties;
import com.notarity.validator.trust.TrustedListStatusService;
import com.notarity.validator.notary.PrototypeNotaryMatchingService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class DssReportMapper {
    private static final String TRUST_NOTICE = "Cryptographic integrity was checked, but EU Trusted Lists and revocation services are not configured in this phase.";
    private final DssSignatureMapper signatures;
    private final PrototypeRegistryService prototypeRegistry;
    private final TrustedListStatusService trustedLists;
    private final TrustProperties trustProperties;
    private final ValidationStatusAggregator statuses;
    private final PrototypeNotaryMatchingService notaryMatching;

    public DssReportMapper(DssSignatureMapper signatures, PrototypeRegistryService prototypeRegistry,
                           TrustedListStatusService trustedLists, TrustProperties trustProperties,
                           PrototypeNotaryMatchingService notaryMatching) {
        this(signatures, prototypeRegistry, trustedLists, trustProperties, new ValidationStatusAggregator(), notaryMatching);
    }

    @Autowired
    public DssReportMapper(DssSignatureMapper signatures, PrototypeRegistryService prototypeRegistry,
                           TrustedListStatusService trustedLists, TrustProperties trustProperties,
                           ValidationStatusAggregator statuses, PrototypeNotaryMatchingService notaryMatching) {
        this.signatures = signatures;
        this.prototypeRegistry = prototypeRegistry;
        this.trustedLists = trustedLists;
        this.trustProperties = trustProperties;
        this.statuses = statuses;
        this.notaryMatching = notaryMatching;
    }

    public ExtractedReports extract(Reports reports) {
        return extract(reports, false);
    }

    public ExtractedReports extract(Reports reports, boolean trustFallback) {
        return new ExtractedReports(reports.getDiagnosticData(), reports.getSimpleReport(), trustFallback);
    }

    public DocumentValidationResponse map(ExtractedReports extracted, String validationId,
                                          DocumentMetadata metadata, Instant validatedAt,
                                          long durationMillis, boolean includeTechnicalDetails) {
        DiagnosticData diagnostic = extracted.diagnostic();
        SimpleReport simple = extracted.simple();
        List<DssSignatureMapper.MappedSignature> mapped = new ArrayList<>();
        int index = 1;
        var diagnosticSignatures = diagnostic == null || diagnostic.getSignatures() == null
            ? List.<eu.europa.esig.dss.diagnostic.SignatureWrapper>of() : diagnostic.getSignatures();
        for (var source : diagnosticSignatures) {
            if (source != null) mapped.add(signatures.map(source, simple, index++, validatedAt));
        }
        List<SignatureResult> results = mapped.stream().map(DssSignatureMapper.MappedSignature::result).toList();
        boolean trustAvailableForRequest = trustedLists.available() && !extracted.trustFallback();
        OverallValidationStatus overall = statuses.overall(results, trustAvailableForRequest);
        var prototype = prototypeRegistry.lookup(metadata.sha256(), results, validatedAt);
        var notary = notaryMatching.match(results);
        if (overall == OverallValidationStatus.VERIFIED
            && prototype.notarity().status() != NotarityStatus.EXACT_MATCH
            && prototype.notarity().status() != NotarityStatus.NO_RECORD) {
            overall = OverallValidationStatus.VERIFIED_WITH_WARNINGS;
        }
        ValidationMessage trustWarning = trustWarning(extracted.trustFallback());

        List<ValidationMessage> summaryWarnings = overall == OverallValidationStatus.UNSIGNED
            ? List.of(new ValidationMessage("NO_SIGNATURES_FOUND", MessageSeverity.INFO, "No signatures found",
                "No supported embedded PAdES signatures were detected.", null, null))
            : combineWarnings(trustWarning, prototype.notarity().warnings());
        TrustSummary trust = trustSummary(overall, results, notary, prototype);
        List<String> indications = mapped.stream().map(DssSignatureMapper.MappedSignature::indication)
            .filter(Objects::nonNull).distinct().toList();
        List<String> subIndications = mapped.stream().map(DssSignatureMapper.MappedSignature::subIndication)
            .filter(Objects::nonNull).distinct().toList();
        String overallIndication = indications.isEmpty() ? (results.isEmpty() ? "NO_SIGNATURES" : "UNKNOWN")
            : String.join(", ", indications);
        List<ValidationMessage> technicalWarnings = includeTechnicalDetails && !results.isEmpty() && trustWarning != null
            ? List.of(trustWarning) : List.of();

        return new DocumentValidationResponse(validationId, "0.2.0", validatedAt,
            overall, summary(overall, results, summaryWarnings),
            new DocumentInfo(metadata.filename(), metadata.mediaType(), metadata.sizeBytes(), metadata.sha256(),
                metadata.pageCount(), metadata.encrypted(), !results.isEmpty()),
            trust, results, notary, prototype.notarity(),
            new TechnicalEvidence("EU DSS 6.4", "EU Trusted List and revocation validation", "SHA-256", technicalWarnings,
                "dss", "6.4", overallIndication, subIndications, results.size(), durationMillis,
                trustAvailableForRequest, trustProperties.getRevocation().isEnabled() && !extracted.trustFallback()),
            new Capabilities(true, false));
    }

    private ValidationSummary summary(OverallValidationStatus status, List<SignatureResult> signatures,
                                      List<ValidationMessage> warnings) {
        int count = signatures.size();
        CheckStatus cryptographic = statuses.cryptographicIntegrity(signatures);
        return switch (status) {
            case UNSIGNED -> new ValidationSummary("No cryptographic signatures found",
                "The PDF does not contain a supported embedded PAdES signature. This does not mean that the document is fraudulent.", warnings);
            case INVALID -> new ValidationSummary("Document signature integrity failed",
                "DSS detected a material failure in at least one signature value, signed PDF byte range, or signed-content integrity check.", warnings);
            case INDETERMINATE -> cryptographic == CheckStatus.VALID
                ? new ValidationSummary("The document has not been modified since signing",
                    count + " embedded signature" + (count == 1 ? " passed" : "s passed")
                        + " cryptographic integrity checks. Certificate-chain trust and eIDAS qualification could not be established"
                        + (trustedLists.available() ? "." : " because authenticated EU Trusted List data was unavailable."), warnings)
                : new ValidationSummary("Unable to complete cryptographic validation",
                    "Embedded signatures were found, but their cryptographic integrity could not be conclusively evaluated.", warnings);
            case VERIFIED -> new ValidationSummary("The document and its signatures are trusted",
                count + " embedded signature" + (count == 1 ? " was" : "s were")
                    + " cryptographically verified with certificate-chain and revocation evidence.", warnings);
            default -> new ValidationSummary("The document has not been modified since signing",
                count + " embedded PAdES signature" + (count == 1 ? " was" : "s were")
                    + " verified. Trust was established, with the limitations shown below.", warnings);
        };
    }
    private TrustSummary trustSummary(OverallValidationStatus overall, List<SignatureResult> results,
                                      NotaryCheckResult notary,
                                      PrototypeRegistryService.PrototypeResults prototype) {
        int count = results.size();
        CheckStatus integrity = statuses.cryptographicIntegrity(results);
        String integrityValue = integrity == CheckStatus.VALID ? "Cryptographically intact"
            : integrity == CheckStatus.INVALID ? "Invalid"
            : integrity == CheckStatus.NOT_APPLICABLE ? "Not signed" : "Unable to determine";
        return new TrustSummary(
            card(integrity, "Document integrity", integrityValue, "DSS checked signature values, signed content, and PDF byte ranges."),
            card(count == 0 ? CheckStatus.NOT_FOUND : CheckStatus.VALID,
                "Embedded signatures", count == 0 ? "No signatures" : count + (count == 1 ? " signature" : " signatures"),
                "Supported embedded PAdES signatures detected by DSS."),
            qualificationCard(results),
            certificateTrustCard(results),
            notaryCard(notary),
            card(prototype.notarityStatus(), "Notarity record", prototype.notarityValue(), "Versioned prototype registry lookup by exact SHA-256."));
    }
    private StatusSummary notaryCard(NotaryCheckResult result) {
        return switch (result.status()) {
            case CONFIRMED_MATCH -> card(CheckStatus.VALID, "Notary", "Austrian notary snapshot match",
                "The signer identity matched a public directory record and its authorization covered the signing date.");
            case POSSIBLE_MATCH -> card(CheckStatus.WARNING, "Notary", "Possible Austrian notary match",
                "The identity matched the snapshot, but public evidence was insufficient for a date-specific conclusion.");
            case AMBIGUOUS -> card(CheckStatus.INDETERMINATE, "Notary", "Ambiguous match",
                "More than one prototype directory entry matched with the same confidence.");
            case NO_MATCH -> card(CheckStatus.NOT_FOUND, "Notary", "No match in current snapshot",
                "No named person signer matched the Austrian notarial-signature directory snapshot.");
            case REGISTRY_UNAVAILABLE -> card(CheckStatus.INDETERMINATE, "Notary", "Registry unavailable",
                "The local prototype notary dataset could not be loaded.");
            case NOT_CHECKED -> card(CheckStatus.NOT_CHECKED, "Notary", "Not checked",
                "No embedded person signature was available for notary identity matching.");
        };
    }
    private StatusSummary qualificationCard(List<SignatureResult> results) {
        if (results.isEmpty()) return card(CheckStatus.NOT_APPLICABLE, "Signature level", "Not applicable", "No signatures were detected.");
        var values = results.stream().map(result -> result.signature().qualification()).toList();
        if (values.stream().anyMatch(value -> value == Qualification.UNKNOWN)) {
            return card(CheckStatus.INDETERMINATE, "Signature level", "Unable to determine",
                "DSS could not establish every signature's eIDAS qualification.");
        }
        String value = values.stream().distinct().count() == 1
            ? results.getFirst().signature().qualificationLabel()
            : values.size() + " qualification results";
        return card(CheckStatus.VALID, "Signature level", value, "Derived from DSS qualification processing and Trusted List evidence.");
    }
    private StatusSummary certificateTrustCard(List<SignatureResult> results) {
        if (results.isEmpty()) return card(CheckStatus.NOT_APPLICABLE, "Certificate trust", "Not applicable", "No signatures were detected.");
        long trusted = results.stream().filter(result -> result.certificate().chainStatus() == ChainStatus.TRUSTED).count();
        long indeterminate = results.size() - trusted;
        if (trusted == results.size()) return card(CheckStatus.VALID, "Certificate trust", "Trusted at signing time",
            "All signer chains reached an authenticated Trusted List trust service.");
        String value = trusted > 0 ? trusted + " trusted, " + indeterminate + " indeterminate"
            : trustedLists.available() ? "Certificate chain incomplete" : "Unable to determine";
        return card(CheckStatus.INDETERMINATE, "Certificate trust", value,
            trustedLists.available() ? "One or more signer chains could not be completed." : "Authenticated Trusted List data is unavailable.");
    }
    private ValidationMessage trustWarning(boolean trustFallback) {
        if (trustedLists.available() && !trustFallback) return null;
        return new ValidationMessage("TRUST_INFRASTRUCTURE_UNAVAILABLE", MessageSeverity.WARNING,
            "Trust infrastructure unavailable",
            "Cryptographic checks completed, but authenticated EU Trusted List data is currently unavailable.",
            null, "TrustedListState=" + trustedLists.snapshot().state()
                + (trustFallback ? ";CryptographicFallback=true" : ""));
    }
    private List<ValidationMessage> combineWarnings(ValidationMessage trustWarning,
                                                    List<ValidationMessage> registryWarnings) {
        List<ValidationMessage> warnings = new ArrayList<>();
        if (trustWarning != null) warnings.add(trustWarning);
        if (registryWarnings != null) warnings.addAll(registryWarnings);
        return List.copyOf(warnings);
    }
    private StatusSummary card(CheckStatus status, String label, String value, String description) {
        return new StatusSummary(status, label, value, description);
    }

    public record ExtractedReports(DiagnosticData diagnostic, SimpleReport simple, boolean trustFallback) {}
}

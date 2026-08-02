package com.notarity.validator.validation.dss;

import com.notarity.validator.api.ApiModels.DocumentValidationResponse;
import com.notarity.validator.document.DocumentMetadata;
import com.notarity.validator.validation.SignatureValidationService;
import com.notarity.validator.validation.ValidationOptions;
import com.notarity.validator.trust.TrustedListStatusService;
import com.notarity.validator.trust.TrustProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@ConditionalOnProperty(name = "app.validation.mode", havingValue = "dss", matchIfMissing = true)
public class DssSignatureValidationService implements SignatureValidationService {
    private static final Logger log = LoggerFactory.getLogger(DssSignatureValidationService.class);
    private final DssValidatorFactory validators;
    private final DssReportMapper mapper;
    private final ValidationDiagnosticContext diagnostics;
    private final TrustedListStatusService trustedLists;
    private final TrustProperties trustProperties;

    public DssSignatureValidationService(DssValidatorFactory validators, DssReportMapper mapper,
                                         ValidationDiagnosticContext diagnostics,
                                         TrustedListStatusService trustedLists,
                                         TrustProperties trustProperties) {
        this.validators = validators;
        this.mapper = mapper;
        this.diagnostics = diagnostics;
        this.trustedLists = trustedLists;
        this.trustProperties = trustProperties;
    }

    @Override
    public DocumentValidationResponse validate(byte[] documentBytes, DocumentMetadata metadata, ValidationOptions options) {
        long started = System.nanoTime();
        Instant validatedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        String validationId = diagnostics.current() == null
            ? diagnostics.begin(metadata.filename()) : diagnostics.current().validationId();
        if (trustProperties.isEnabled() && trustProperties.isRefreshOnStartup()
            && !trustedLists.available()) {
            var before = trustedLists.snapshot();
            log.info("validationId={} filename={} waitingForInitialTrustedLists=true trustState={}",
                validationId, metadata.filename(), before.state());
            var after = trustedLists.awaitInitialResolution(trustProperties.getStartupTimeout());
            log.info("validationId={} filename={} initialTrustedListWaitComplete=true trustState={} loadedLists={}",
                validationId, metadata.filename(), after.state(), after.nationalTrustedLists());
        }
        var validatorSnapshot = at(DssProcessingStage.DSS_VALIDATOR_CREATION,
            () -> validators.create(documentBytes, metadata.filename()));
        log.info("validationId={} filename={} trustState={} verifierTrustEnabled={} trustedEntities={}",
            validationId, metadata.filename(), trustedLists.snapshot().state(),
            validatorSnapshot.trustEnabled(), validatorSnapshot.trustedEntities());
        var execution = validateWithFallback(validatorSnapshot, documentBytes, metadata.filename(), validationId);
        var extracted = at(DssProcessingStage.DSS_REPORT_EXTRACTION,
            () -> mapper.extract(execution.reports(), execution.trustFallback()));
        logStructure(extracted);
        long durationMillis = (System.nanoTime() - started) / 1_000_000;
        var response = at(DssProcessingStage.DSS_MAPPING,
            () -> mapper.map(extracted, validationId, metadata, validatedAt, durationMillis, options.includeTechnicalDetails()));
        log.info("validationId={} filename={} sizeBytes={} signatures={} overallStatus={} durationMs={}",
            response.validationId(), metadata.filename(), metadata.sizeBytes(), response.signatures().size(),
            response.overallStatus(), durationMillis);
        return response;
    }

    private ValidationExecution validateWithFallback(
            DssValidatorFactory.ValidatorSnapshot snapshot, byte[] documentBytes,
            String filename, String validationId) {
        diagnostics.stage(DssProcessingStage.DSS_VALIDATION);
        try {
            return new ValidationExecution(snapshot.validator().validateDocument(), false);
        } catch (RuntimeException exception) {
            if (!snapshot.trustEnabled() || !TrustInfrastructureFailure.isInfrastructureFailure(exception)) {
                Throwable deepest = TrustInfrastructureFailure.deepest(exception);
                log.error("validationId={} filename={} stage=DSS_VALIDATION trustState={} "
                        + "verifierTrustEnabled={} trustedEntities={} failureArea={} exceptionClass={} "
                        + "deepestExceptionClass={} deepestExceptionMessage={} causeChain={}",
                    validationId, filename, trustedLists.snapshot().state(), snapshot.trustEnabled(),
                    snapshot.trustedEntities(), TrustInfrastructureFailure.failureArea(exception),
                    exception.getClass().getName(), deepest.getClass().getName(),
                    TrustInfrastructureFailure.safeMessage(deepest),
                    TrustInfrastructureFailure.safeCauseChain(exception), exception);
                throw new DssValidationException("SIGNATURE_VALIDATION_FAILED",
                    DssProcessingStage.DSS_VALIDATION, exception);
            }
            log.warn("validationId={} filename={} stage=DSS_VALIDATION trustFallback=true "
                    + "exceptionClass={} deepestExceptionClass={} causeChain={}",
                validationId, filename, exception.getClass().getName(),
                TrustInfrastructureFailure.deepest(exception).getClass().getName(),
                TrustInfrastructureFailure.safeCauseChain(exception));
            var fallback = at(DssProcessingStage.DSS_VALIDATOR_CREATION,
                () -> validators.createCryptographicOnly(documentBytes, filename));
            return new ValidationExecution(
                at(DssProcessingStage.DSS_VALIDATION, fallback::validateDocument), true);
        }
    }

    private record ValidationExecution(eu.europa.esig.dss.validation.reports.Reports reports,
                                       boolean trustFallback) {}

    private void logStructure(DssReportMapper.ExtractedReports extracted) {
        var signatureList = extracted.diagnostic() == null || extracted.diagnostic().getSignatures() == null
            ? java.util.List.<eu.europa.esig.dss.diagnostic.SignatureWrapper>of()
            : extracted.diagnostic().getSignatures();
        log.info("DSS structure detectedSignatureCount={}", signatureList.size());
        for (var signature : signatureList) {
            var revision = signature.getPDFRevision();
            String indication = safe(() -> extracted.simple() == null ? null
                : extracted.simple().getIndication(signature.getId()));
            String subIndication = safe(() -> extracted.simple() == null ? null
                : extracted.simple().getSubIndication(signature.getId()));
            log.info("DSS signature id={} subFilter={} signerCertificatePresent={} timestampCount={} format={} "
                    + "indication={} subIndication={} byteRangeValid={} incrementalRevisionDetected={}",
                signature.getId(), revision == null ? null : revision.getSubFilter(),
                signature.getSigningCertificate() != null, safeSize(signature.getTimestampList()),
                signature.getSignatureFormat(), indication, subIndication,
                signature.isSignatureByteRangeValid(), signature.arePdfModificationsDetected());
        }
    }

    private int safeSize(java.util.Collection<?> values) {
        return values == null ? 0 : values.size();
    }

    private String safe(java.util.function.Supplier<?> supplier) {
        try {
            Object value = supplier.get();
            return value == null ? null : value.toString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private <T> T at(DssProcessingStage stage, java.util.function.Supplier<T> operation) {
        diagnostics.stage(stage);
        try {
            return operation.get();
        } catch (DssValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DssValidationException("SIGNATURE_VALIDATION_FAILED", stage, exception);
        } catch (LinkageError error) {
            // DSS loads some provider implementations lazily. Preserve the stage when
            // a required implementation is absent or cannot be initialized.
            throw new DssValidationException("SIGNATURE_VALIDATION_FAILED", stage, error);
        }
    }
}

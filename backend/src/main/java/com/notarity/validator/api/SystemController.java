package com.notarity.validator.api;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import com.notarity.validator.trust.TrustProperties;
import com.notarity.validator.trust.TrustedListStatusService;
import com.notarity.validator.registry.JsonPrototypeNotarityRegistry;
import com.notarity.validator.registry.PrototypeRegistryKeyStore;
import com.notarity.validator.notary.JsonPrototypeNotaryRegistry;
import org.springframework.core.env.Environment;

@RestController
@RequestMapping("/api/v1")
public class SystemController {
    @Value("${app.version:0.2.0}") String version;
    @Value("${app.validation.mode:dss}") String validationMode;
    private final TrustedListStatusService trustedLists;
    private final TrustProperties trust;
    private final JsonPrototypeNotarityRegistry prototypeRegistry;
    private final PrototypeRegistryKeyStore prototypeKeys;
    private final Environment springEnvironment;
    private final JsonPrototypeNotaryRegistry notaryRegistry;

    public SystemController(TrustedListStatusService trustedLists, TrustProperties trust,
                            JsonPrototypeNotarityRegistry prototypeRegistry,
                            PrototypeRegistryKeyStore prototypeKeys, Environment springEnvironment,
                            JsonPrototypeNotaryRegistry notaryRegistry) {
        this.trustedLists = trustedLists;
        this.trust = trust;
        this.prototypeRegistry = prototypeRegistry;
        this.prototypeKeys = prototypeKeys;
        this.springEnvironment = springEnvironment;
        this.notaryRegistry = notaryRegistry;
    }

    @GetMapping("/health")
    @Operation(summary = "Application health", description = "Returns sanitized component readiness without internal configuration.")
    public Map<String, Object> health() {
        String trustedListHealth = switch (trustedLists.snapshot().state()) {
            case AVAILABLE -> trustedLists.snapshot().failedTrustedLists() == 0 ? "UP" : "DEGRADED";
            case AVAILABLE_FROM_CACHE, REFRESHING -> "DEGRADED";
            case NOT_CONFIGURED -> "NOT_CONFIGURED";
            case UNAVAILABLE, INVALID -> "DOWN";
        };
        String revocation = !trust.getRevocation().isEnabled() ? "NOT_CONFIGURED"
            : trustedLists.available() ? "UP" : "DEGRADED";
        Map<String, Object> trustStatus = new java.util.LinkedHashMap<>();
        var snapshot = trustedLists.snapshot();
        trustStatus.put("enabled", trust.isEnabled());
        trustStatus.put("state", snapshot.state());
        trustStatus.put("lastAttempt", snapshot.lastAttempt());
        trustStatus.put("lastSuccess", snapshot.lastSuccess());
        trustStatus.put("loadedListCount", snapshot.nationalTrustedLists());
        trustStatus.put("failedListCount", snapshot.failedTrustedLists());
        trustStatus.put("cacheAvailable", snapshot.cacheAvailable());
        trustStatus.put("failureStage", snapshot.failureStage());
        trustStatus.put("failureClass", snapshot.failureClass());
        String[] profiles = springEnvironment.getActiveProfiles();
        trustStatus.put("activeProfiles", profiles.length == 0 ? List.of("default") : List.of(profiles));
        trustStatus.put("initializationComplete", trustedLists.initialResolutionComplete());
        return Map.of("status", "UP", "version", version, "components", Map.of(
            "application", "UP", "dssValidationEngine", validationMode.equalsIgnoreCase("dss") ? "UP" : "NOT_CONFIGURED",
            "trustedLists", trustedListHealth, "certificateTrust", trustedListHealth, "revocationSources", revocation,
            "notaryRegistry", !notaryRegistry.available() ? "DOWN" : notaryRegistry.degraded() ? "DEGRADED" : "UP",
            "prototypeNotarityRegistry", prototypeRegistry.available() ? "UP" : "DOWN",
            "prototypeRegistryKeys", prototypeKeys.available() ? "UP" : "DOWN"),
            "trustedListStatus", trustStatus);
    }

    @GetMapping("/capabilities")
    @Operation(summary = "Validator capabilities", description = "Returns supported upload, validation, and planned report capabilities for the Phase 3A application.")
    public Map<String, Object> capabilities() {
        var snapshot = trustedLists.snapshot();
        Map<String, Object> capabilities = new java.util.LinkedHashMap<>();
        capabilities.put("supportedMediaTypes", List.of("application/pdf"));
        capabilities.put("supportedSignatureFormats", List.of("PAdES"));
        capabilities.put("maximumFileSizeBytes", 26214400);
        capabilities.put("reportFormats", List.of("PDF", "JSON"));
        capabilities.put("notaryRegistryCoverage", List.of("AT_STATIC_PUBLIC_SNAPSHOT"));
        capabilities.put("notaryRegistryLive", false);
        capabilities.put("notaryRegistryState", !notaryRegistry.available() ? "UNAVAILABLE" : notaryRegistry.degraded() ? "DEGRADED" : "AVAILABLE");
        capabilities.put("notarityEnvironment", "PROTOTYPE");
        capabilities.put("prototypeRegistryVerification", prototypeRegistry.available() && prototypeKeys.available());
        capabilities.put("prototypeRegistryAlgorithm", "Ed25519");
        capabilities.put("documentRetention", "NONE");
        capabilities.put("eidasTrustValidation", trustedLists.available());
        capabilities.put("trustedListsEnabled", trust.isEnabled());
        capabilities.put("trustedListsState", snapshot.state());
        capabilities.put("trustedListInitializationComplete", trustedLists.initialResolutionComplete());
        capabilities.put("trustedListLastSuccessfulRefresh", snapshot.lastSuccess());
        capabilities.put("loadedNationalTrustedListCount", snapshot.nationalTrustedLists());
        capabilities.put("trustedListCacheAvailable", snapshot.cacheAvailable());
        capabilities.put("trustedListLastUpdatedAt", snapshot.lastSuccess());
        capabilities.put("revocationChecking", trust.getRevocation().isEnabled());
        return capabilities;
    }
}

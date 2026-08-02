package com.notarity.validator.trust;

import eu.europa.esig.dss.model.tsl.TLValidationJobSummary;
import eu.europa.esig.dss.model.tsl.TLInfo;
import eu.europa.esig.dss.service.http.commons.CommonsDataLoader;
import eu.europa.esig.dss.service.http.commons.FileCacheDataLoader;
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource;
import eu.europa.esig.dss.tsl.job.TLValidationJob;
import eu.europa.esig.dss.tsl.source.LOTLSource;
import eu.europa.esig.dss.tsl.sync.ExpirationAndSignatureCheckStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class TrustedListLoadingService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TrustedListLoadingService.class);
    enum Stage { INITIALIZATION, LOTL_DOWNLOAD, LOTL_AUTHENTICATION, NATIONAL_TL_LOADING, CACHE_RECOVERY }

    private final TrustProperties properties;
    private final TrustedListStatusService status;
    private final CertificateVerifierProvider verifierProvider;
    private final FileCacheDataLoader cache;
    private final ExecutorService tlWorkers = Executors.newFixedThreadPool(6);
    private final Object refreshLock = new Object();
    private volatile Stage stage = Stage.INITIALIZATION;
    private int refreshCount;

    public TrustedListLoadingService(TrustProperties properties, TrustedListStatusService status,
                                     CertificateVerifierProvider verifierProvider,
                                     CommonsDataLoader network, FileCacheDataLoader cache) {
        this.properties = properties;
        this.status = status;
        this.verifierProvider = verifierProvider;
        this.cache = cache;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!properties.isEnabled()) {
            status.notConfigured();
            log.info("TRUSTED_LIST_INITIALIZATION_SKIPPED reason=NOT_CONFIGURED");
            return;
        }
        if (properties.isRefreshOnStartup()) {
            // Loading every national Trusted List can take minutes on a cold cache. Publish the
            // pending state immediately and keep Spring/HTTP startup non-blocking; validators use
            // the stable cryptographic-only verifier until an authenticated snapshot is promoted.
            status.refreshing(Instant.now());
            Thread.ofVirtual().name("trusted-list-initialization").start(this::initializeWithTimeout);
        }
        else status.failed(Instant.now(), Instant.now().plus(properties.getRefreshInterval()), false,
            cacheFilesExist(), Stage.INITIALIZATION.name(), "REFRESH_ON_STARTUP_DISABLED");
    }

    private void initializeWithTimeout() {
        ExecutorService startup = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().name("trusted-list-startup").factory());
        var task = startup.submit(this::refresh);
        try {
            task.get(properties.getStartupTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            task.cancel(true);
            boolean cached = cacheFilesExist();
            status.failed(Instant.now(), Instant.now().plus(properties.getRefreshInterval()), false,
                cached, stage.name(), exception.getClass().getName());
            log.error("TRUSTED_LIST_INITIALIZATION_FAILED stage={} exceptionClass={} safeRootCause=STARTUP_TIMEOUT cacheFilesExist={} resultingState={}",
                stage, exception.getClass().getName(), cached, status.snapshot().state());
        } catch (Exception exception) {
            logFailure(exception, cacheFilesExist());
        } finally {
            startup.shutdownNow();
        }
    }

    @Scheduled(initialDelayString = "${app.trust.refresh-interval:24h}",
               fixedDelayString = "${app.trust.refresh-interval:24h}")
    public void scheduledRefresh() {
        if (properties.isEnabled()) refresh();
    }

    public void refresh() {
        synchronized (refreshLock) {
            refreshCount++;
            Instant now = Instant.now();
            Instant next = now.plus(properties.getRefreshInterval());
            status.refreshing(now);
            stage = Stage.INITIALIZATION;
            log.info("TRUSTED_LIST_INITIALIZATION_STARTED refreshNumber={} cacheFilesExist={}", refreshCount, cacheFilesExist());
            try {
                stage = Stage.LOTL_DOWNLOAD;
                log.info("LOTL_DOWNLOAD_STARTED");
                var ojCertificates = new OfficialJournalCertificateLoader()
                    .load(properties.getOfficialJournalUrl(), cache);
                TrustedListsCertificateSource candidate = new TrustedListsCertificateSource();
                TLValidationJob job = job(ojCertificates, candidate);
                stage = Stage.NATIONAL_TL_LOADING;
                log.info("NATIONAL_TL_LOADING_STARTED");
                job.onlineRefresh();
                accept(job.getSummary(), candidate, false, now, next);
            } catch (Exception onlineFailure) {
                logFailure(onlineFailure, cacheFilesExist());
                if (!recoverAuthenticatedCache(now, next)) {
                    boolean cached = cacheFilesExist();
                    status.failed(now, next, isAuthenticityFailure(onlineFailure), cached,
                        stage.name(), root(onlineFailure).getClass().getName());
                    log.error("TRUSTED_LIST_INITIALIZATION_FAILED stage={} exceptionClass={} safeRootCause={} cacheFilesExist={} resultingState={}",
                        stage, onlineFailure.getClass().getName(), safeCause(onlineFailure), cached, status.snapshot().state());
                }
            }
        }
    }

    private boolean recoverAuthenticatedCache(Instant now, Instant next) {
        stage = Stage.CACHE_RECOVERY;
        try {
            var cachedNotice = cache.getDocumentFromCache(properties.getOfficialJournalUrl());
            if (cachedNotice == null) return false;
            var ojCertificates = new OfficialJournalCertificateLoader().load(cachedNotice);
            TrustedListsCertificateSource candidate = new TrustedListsCertificateSource();
            TLValidationJob cachedJob = job(ojCertificates, candidate);
            cachedJob.offlineRefresh();
            accept(cachedJob.getSummary(), candidate, true, now, next);
            return true;
        } catch (Exception exception) {
            log.warn("TRUSTED_LIST_CACHE_RECOVERY_FAILED exceptionClass={} safeRootCause={}",
                exception.getClass().getName(), safeCause(exception));
            return false;
        }
    }

    private TLValidationJob job(eu.europa.esig.dss.spi.x509.CertificateSource ojCertificates,
                                TrustedListsCertificateSource candidate) {
        LOTLSource lotl = new LOTLSource();
        lotl.setUrl(properties.getLotlUrl());
        lotl.setCertificateSource(ojCertificates);
        lotl.setPivotSupport(true);
        lotl.setTLVersions(List.of(5, 6));

        ExpirationAndSignatureCheckStrategy strict = new ExpirationAndSignatureCheckStrategy();
        strict.setAcceptExpiredListOfTrustedLists(false);
        strict.setAcceptInvalidListOfTrustedLists(false);
        strict.setAcceptExpiredTrustedList(false);
        strict.setAcceptInvalidTrustedList(false);

        TLValidationJob job = new TLValidationJob();
        job.setListOfTrustedListSources(lotl);
        job.setTrustedListCertificateSource(candidate);
        job.setOfflineDataLoader(cache);
        job.setOnlineDataLoader(cache);
        job.setSynchronizationStrategy(strict);
        job.setExecutorService(tlWorkers);
        return job;
    }

    private void accept(TLValidationJobSummary summary, TrustedListsCertificateSource candidate,
                        boolean fromCache, Instant now, Instant next) {
        if (summary == null || summary.getLOTLInfos().isEmpty()
            || summary.getLOTLInfos().stream().noneMatch(info -> info.getValidationCacheInfo() != null
                && info.getValidationCacheInfo().isValid())) {
            throw new IllegalStateException("The EU LOTL did not pass signature validation");
        }
        stage = Stage.LOTL_AUTHENTICATION;
        log.info("LOTL_AUTHENTICATION_SUCCEEDED source={}", fromCache ? "CACHE" : "LIVE");
        var nationalLists = nationalTrustedLists(summary);
        int processed = nationalLists.size();
        int valid = (int) nationalLists.stream().filter(info ->
            info.getValidationCacheInfo() != null && info.getValidationCacheInfo().isValid()).count();
        int failures = processed - valid;
        int certificates = candidate.getNumberOfTrustedEntityKeys();
        var indications = nationalLists.stream()
            .filter(info -> info.getValidationCacheInfo() != null)
            .collect(java.util.stream.Collectors.groupingBy(
                info -> String.valueOf(info.getValidationCacheInfo().getIndication()),
                java.util.stream.Collectors.counting()));
        log.info("TRUSTED_LIST_AUTHENTICATION_SUMMARY processed={} valid={} failed={} "
                + "trustedEntities={} indications={}",
            processed, valid, failures, certificates, indications);
        if (valid == 0 || certificates == 0) {
            throw new IllegalStateException("Authenticated national Trusted Lists did not produce trust anchors: "
                + "processed=" + processed + ", valid=" + valid + ", trustedEntities=" + certificates);
        }
        verifierProvider.promote(candidate);
        status.available(fromCache, now, next, valid, certificates, failures);
        log.info("TRUSTED_LIST_INITIALIZATION_COMPLETED source={} authenticatedNationalLists={} failedNationalLists={} trustedEntities={} resultingState={}",
            fromCache ? "CACHE" : "LIVE", valid, failures, certificates, status.snapshot().state());
    }

    private void logFailure(Exception exception, boolean cached) {
        Throwable deepest = root(exception);
        log.warn("TRUSTED_LIST_STAGE_FAILED stage={} exceptionClass={} deepestExceptionClass={} "
                + "deepestExceptionMessage={} causeChain={} safeRootCause={} cacheFilesExist={}",
            stage, exception.getClass().getName(), deepest.getClass().getName(), safeMessage(deepest),
            causeChain(exception), safeCause(exception), cached, exception);
    }
    private Throwable root(Throwable value) {
        Throwable current = value;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }
    private String safeCause(Exception exception) {
        Throwable root = root(exception);
        String name = root.getClass().getSimpleName().toUpperCase();
        if (name.contains("TIMEOUT")) return "NETWORK_TIMEOUT";
        if (name.contains("UNKNOWNHOST")) return "DNS_UNAVAILABLE";
        if (name.contains("SIGNATURE") || name.contains("CERTIFICATE")) return "AUTHENTICATION_FAILED";
        return "DOWNLOAD_OR_VALIDATION_FAILED";
    }
    private boolean isAuthenticityFailure(Exception exception) {
        String name = root(exception).getClass().getSimpleName().toLowerCase();
        return name.contains("signature") || name.contains("certificate");
    }
    private String safeMessage(Throwable failure) {
        if (failure == null || failure.getMessage() == null) return "none";
        String value = failure.getMessage().replace('\n', ' ').replace('\r', ' ');
        return value.length() > 300 ? value.substring(0, 300) : value;
    }
    private String causeChain(Throwable failure) {
        java.util.List<String> classes = new java.util.ArrayList<>();
        Throwable current = failure;
        while (current != null && classes.size() < 12) {
            classes.add(current.getClass().getName());
            current = current.getCause();
        }
        return String.join(" -> ", classes);
    }
    private boolean cacheFilesExist() {
        try (var files = Files.walk(properties.getCacheDirectory())) {
            return files.anyMatch(Files::isRegularFile);
        } catch (Exception ignored) {
            return false;
        }
    }

    public int refreshCount() { return refreshCount; }

    /** DSS nests LOTL-referenced national lists under each LOTL summary entry. */
    static List<TLInfo> nationalTrustedLists(TLValidationJobSummary summary) {
        return java.util.stream.Stream.concat(
                summary.getLOTLInfos().stream().flatMap(info -> info.getTLInfos().stream()),
                summary.getOtherTLInfos().stream())
            .toList();
    }
}

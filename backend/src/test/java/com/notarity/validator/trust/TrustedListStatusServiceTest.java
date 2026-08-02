package com.notarity.validator.trust;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedListStatusServiceTest {

    @Test
    void validationWaitsForInitialAtomicTrustSourcePublication() throws Exception {
        TrustedListStatusService status = new TrustedListStatusService();
        status.refreshing(Instant.now());
        assertThat(status.initialResolutionComplete()).isFalse();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var waiting = executor.submit(() -> status.awaitInitialResolution(Duration.ofSeconds(2)));
            Thread.sleep(50);
            assertThat(waiting.isDone()).isFalse();

            status.available(false, Instant.now(), Instant.now().plusSeconds(60), 30, 4_624, 1);

            var resolved = waiting.get(1, TimeUnit.SECONDS);
            assertThat(resolved.state()).isEqualTo(TrustedListStatusService.State.AVAILABLE);
            assertThat(resolved.nationalTrustedLists()).isEqualTo(30);
            assertThat(resolved.trustedCertificates()).isEqualTo(4_624);
            assertThat(status.initialResolutionComplete()).isTrue();
        }
    }

    @Test
    void failedInitialLoadReleasesWaitingValidationWithoutClaimingTrust() throws Exception {
        TrustedListStatusService status = new TrustedListStatusService();
        status.refreshing(Instant.now());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var waiting = executor.submit(() -> status.awaitInitialResolution(Duration.ofSeconds(2)));
            status.failed(Instant.now(), Instant.now().plusSeconds(60), false, false,
                "LOTL_DOWNLOAD", "java.net.ConnectException");

            assertThat(waiting.get(1, TimeUnit.SECONDS).state())
                .isEqualTo(TrustedListStatusService.State.UNAVAILABLE);
            assertThat(status.initialResolutionComplete()).isTrue();
        }
    }
}

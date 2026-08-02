package com.notarity.validator.trust;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TrustedListStatusService {
    public enum State { AVAILABLE, AVAILABLE_FROM_CACHE, REFRESHING, UNAVAILABLE, INVALID, NOT_CONFIGURED }
    public record Snapshot(State state, Instant lastAttempt, Instant lastSuccess, Instant nextRefresh,
                           int nationalTrustedLists, int trustedCertificates, boolean cacheAvailable,
                           int failedTrustedLists, String failureStage, String failureClass) {}

    private final AtomicReference<Snapshot> current =
        new AtomicReference<>(new Snapshot(State.NOT_CONFIGURED, null, null, null, 0, 0, false, 0, null, null));
    private final Object stateChanged = new Object();
    private final AtomicBoolean initialResolutionComplete = new AtomicBoolean(false);

    public Snapshot snapshot() { return current.get(); }
    public boolean available() {
        Snapshot snapshot = current.get();
        return snapshot.state() == State.AVAILABLE
            || snapshot.state() == State.AVAILABLE_FROM_CACHE
            || (snapshot.state() == State.REFRESHING && snapshot.lastSuccess() != null);
    }
    void refreshing(Instant now) {
        Snapshot old = current.get();
        current.set(new Snapshot(State.REFRESHING, now, old.lastSuccess(), old.nextRefresh(),
            old.nationalTrustedLists(), old.trustedCertificates(), old.cacheAvailable(),
            old.failedTrustedLists(), null, null));
        signalStateChanged();
    }
    void available(boolean cache, Instant now, Instant next, int lists, int certificates, int failures) {
        current.set(new Snapshot(cache ? State.AVAILABLE_FROM_CACHE : State.AVAILABLE,
            now, now, next, lists, certificates, true, failures, null, null));
        initialResolutionComplete.set(true);
        signalStateChanged();
    }
    void failed(Instant now, Instant next, boolean invalid, boolean cacheAvailable,
                String stage, String failureClass) {
        Snapshot old = current.get();
        State state = old.lastSuccess() == null ? (invalid ? State.INVALID : State.UNAVAILABLE) : State.AVAILABLE_FROM_CACHE;
        current.set(new Snapshot(state, now, old.lastSuccess(), next, old.nationalTrustedLists(),
            old.trustedCertificates(), cacheAvailable, old.failedTrustedLists(), stage, failureClass));
        initialResolutionComplete.set(true);
        signalStateChanged();
    }

    void notConfigured() {
        initialResolutionComplete.set(true);
        signalStateChanged();
    }

    public boolean initialResolutionComplete() { return initialResolutionComplete.get(); }

    /** Waits only for the initial source publication; later refreshes retain the last valid verifier. */
    public Snapshot awaitInitialResolution(Duration timeout) {
        long remainingNanos = timeout == null ? 0 : Math.max(0, timeout.toNanos());
        long deadline = System.nanoTime() + remainingNanos;
        synchronized (stateChanged) {
            while (isInitialLoading(current.get()) && remainingNanos > 0) {
                try {
                    long millis = Math.max(1, Math.min(Duration.ofNanos(remainingNanos).toMillis(), 1_000));
                    stateChanged.wait(millis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    break;
                }
                remainingNanos = deadline - System.nanoTime();
            }
            return current.get();
        }
    }

    private boolean isInitialLoading(Snapshot snapshot) {
        return snapshot.lastSuccess() == null
            && (snapshot.state() == State.REFRESHING || snapshot.state() == State.NOT_CONFIGURED);
    }

    private void signalStateChanged() {
        synchronized (stateChanged) { stateChanged.notifyAll(); }
    }
}

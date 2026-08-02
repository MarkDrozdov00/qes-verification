package com.notarity.validator.trust;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties("app.trust")
public class TrustProperties {
    private boolean enabled = true;
    private String lotlUrl = "https://ec.europa.eu/tools/lotl/eu-lotl.xml";
    private String officialJournalUrl = "https://eur-lex.europa.eu/legal-content/EN/TXT/HTML/?uri=OJ:C_202601944";
    private boolean refreshOnStartup = true;
    private Duration refreshInterval = Duration.ofHours(24);
    private Duration startupTimeout = Duration.ofMinutes(2);
    private Path cacheDirectory = Path.of("./cache/trusted-lists");
    private boolean onlineCertificateFetching = true;
    private final Revocation revocation = new Revocation();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getLotlUrl() { return lotlUrl; }
    public void setLotlUrl(String lotlUrl) { this.lotlUrl = lotlUrl; }
    public String getOfficialJournalUrl() { return officialJournalUrl; }
    public void setOfficialJournalUrl(String officialJournalUrl) { this.officialJournalUrl = officialJournalUrl; }
    public boolean isRefreshOnStartup() { return refreshOnStartup; }
    public void setRefreshOnStartup(boolean refreshOnStartup) { this.refreshOnStartup = refreshOnStartup; }
    public Duration getRefreshInterval() { return refreshInterval; }
    public void setRefreshInterval(Duration refreshInterval) { this.refreshInterval = refreshInterval; }
    public Duration getStartupTimeout() { return startupTimeout; }
    public void setStartupTimeout(Duration startupTimeout) { this.startupTimeout = startupTimeout; }
    public Path getCacheDirectory() { return cacheDirectory; }
    public void setCacheDirectory(Path cacheDirectory) { this.cacheDirectory = cacheDirectory; }
    public boolean isOnlineCertificateFetching() { return onlineCertificateFetching; }
    public void setOnlineCertificateFetching(boolean onlineCertificateFetching) { this.onlineCertificateFetching = onlineCertificateFetching; }
    public Revocation getRevocation() { return revocation; }

    public static class Revocation {
        private boolean enabled = true;
        private boolean ocspEnabled = true;
        private boolean crlEnabled = true;
        private int connectTimeoutMs = 3000;
        private int readTimeoutMs = 5000;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isOcspEnabled() { return ocspEnabled; }
        public void setOcspEnabled(boolean ocspEnabled) { this.ocspEnabled = ocspEnabled; }
        public boolean isCrlEnabled() { return crlEnabled; }
        public void setCrlEnabled(boolean crlEnabled) { this.crlEnabled = crlEnabled; }
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    }
}

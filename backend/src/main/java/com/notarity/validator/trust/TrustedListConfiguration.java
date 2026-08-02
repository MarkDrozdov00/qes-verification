package com.notarity.validator.trust;

import eu.europa.esig.dss.service.http.commons.CommonsDataLoader;
import eu.europa.esig.dss.service.http.commons.FileCacheDataLoader;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

@Configuration
@EnableConfigurationProperties(TrustProperties.class)
public class TrustedListConfiguration {
    @Bean
    CommonsDataLoader boundedNetworkLoader(TrustProperties properties) {
        CommonsDataLoader loader = new CommonsDataLoader();
        loader.setTimeoutConnection(properties.getRevocation().getConnectTimeoutMs());
        loader.setTimeoutConnectionRequest(properties.getRevocation().getConnectTimeoutMs());
        loader.setTimeoutResponse(properties.getRevocation().getReadTimeoutMs());
        loader.setTimeoutSocket(properties.getRevocation().getReadTimeoutMs());
        loader.setConnectionsMaxTotal(20);
        loader.setConnectionsMaxPerRoute(4);
        return loader;
    }

    @Bean
    FileCacheDataLoader trustCacheLoader(TrustProperties properties, CommonsDataLoader boundedNetworkLoader) {
        File directory = properties.getCacheDirectory().toFile();
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Unable to create the Trusted List cache");
        }
        FileCacheDataLoader cache = new FileCacheDataLoader(boundedNetworkLoader);
        cache.setFileCacheDirectory(directory);
        cache.setCacheExpirationTime(properties.getRefreshInterval().toMillis());
        return cache;
    }

}

package com.notarity.validator.registry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class PrototypeRegistryStartupLogger implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PrototypeRegistryStartupLogger.class);
    private final JsonPrototypeNotarityRegistry registry;
    private final PrototypeRegistryKeyStore keys;

    public PrototypeRegistryStartupLogger(JsonPrototypeNotarityRegistry registry,
                                          PrototypeRegistryKeyStore keys) {
        this.registry = registry;
        this.keys = keys;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Prototype registry loaded: records={} keys={}", registry.recordCount(), keys.keyCount());
    }
}

package com.notarity.validator.registry;

import com.notarity.validator.registry.PrototypeRegistryModels.SignedPrototypeRegistryRecord;
import java.util.Optional;

public interface PrototypeNotarityRegistry {
    Optional<SignedPrototypeRegistryRecord> findByDocumentHash(String sha256);
}

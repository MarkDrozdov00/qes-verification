package com.notarity.validator.registry;

import com.notarity.validator.registry.PrototypeRegistryModels.*;

public interface RegistryReceiptVerifier {
    RegistryReceiptVerificationResult verify(SignedPrototypeRegistryRecord record);
}

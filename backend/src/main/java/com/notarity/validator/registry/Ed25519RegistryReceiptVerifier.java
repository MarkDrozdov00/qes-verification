package com.notarity.validator.registry;

import com.notarity.validator.registry.PrototypeRegistryModels.*;
import org.springframework.stereotype.Component;

import java.security.Signature;
import java.util.Base64;

@Component
public class Ed25519RegistryReceiptVerifier implements RegistryReceiptVerifier {
    private final CanonicalJsonService canonicalJson;
    private final PrototypeRegistryKeyStore keys;

    public Ed25519RegistryReceiptVerifier(CanonicalJsonService canonicalJson, PrototypeRegistryKeyStore keys) {
        this.canonicalJson = canonicalJson;
        this.keys = keys;
    }

    @Override
    public RegistryReceiptVerificationResult verify(SignedPrototypeRegistryRecord record) {
        if (record == null || !"Ed25519".equals(record.algorithm())) {
            return result(ReceiptVerificationStatus.INDETERMINATE, ReceiptVerificationReason.UNSUPPORTED_ALGORITHM);
        }
        var key = keys.resolve(record.keyId(), record.algorithm());
        if (key.isEmpty()) return result(ReceiptVerificationStatus.INDETERMINATE, ReceiptVerificationReason.KEY_NOT_FOUND);
        final byte[] signatureBytes;
        try {
            signatureBytes = Base64.getDecoder().decode(record.signature());
        } catch (RuntimeException exception) {
            return result(ReceiptVerificationStatus.INVALID, ReceiptVerificationReason.MALFORMED_SIGNATURE);
        }
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key.get());
            verifier.update(canonicalJson.canonicalize(record.payload()));
            return verifier.verify(signatureBytes)
                ? result(ReceiptVerificationStatus.VALID, ReceiptVerificationReason.NONE)
                : result(ReceiptVerificationStatus.INVALID, ReceiptVerificationReason.SIGNATURE_MISMATCH);
        } catch (CanonicalJsonService.CanonicalizationException exception) {
            return result(ReceiptVerificationStatus.INDETERMINATE, ReceiptVerificationReason.CANONICALIZATION_FAILED);
        } catch (Exception exception) {
            return result(ReceiptVerificationStatus.INDETERMINATE, ReceiptVerificationReason.PUBLIC_KEY_INVALID);
        }
    }

    private RegistryReceiptVerificationResult result(ReceiptVerificationStatus status, ReceiptVerificationReason reason) {
        return new RegistryReceiptVerificationResult(status, reason);
    }
}

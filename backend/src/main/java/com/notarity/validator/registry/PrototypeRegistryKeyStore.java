package com.notarity.validator.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class PrototypeRegistryKeyStore {
    public record PublicKeyDefinition(String keyId, String algorithm, String publicKeyPem,
                                      String status, Instant validFrom) {}
    private final Map<String, PublicKeyDefinition> keys;

    public PrototypeRegistryKeyStore(ObjectMapper mapper,
        @Value("${app.prototype-registry.public-keys:classpath:prototype/prototype-registry-public-keys.json}") Resource resource) {
        try (var input = resource.getInputStream()) {
            keys = Arrays.stream(mapper.readValue(input, PublicKeyDefinition[].class))
                .collect(Collectors.toUnmodifiableMap(PublicKeyDefinition::keyId, key -> key));
        } catch (Exception exception) {
            throw new IllegalStateException("Prototype registry public keys could not be loaded", exception);
        }
    }

    public Optional<PublicKey> resolve(String keyId, String algorithm) {
        PublicKeyDefinition definition = keys.get(keyId);
        if (definition == null || !"ACTIVE".equals(definition.status())
            || !definition.algorithm().equalsIgnoreCase(algorithm)) return Optional.empty();
        try {
            String encoded = definition.publicKeyPem().replaceAll("-----[^-]+-----", "").replaceAll("\\s+", "");
            return Optional.of(KeyFactory.getInstance("Ed25519")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded))));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    public boolean available() { return !keys.isEmpty(); }
    public int keyCount() { return keys.size(); }
}

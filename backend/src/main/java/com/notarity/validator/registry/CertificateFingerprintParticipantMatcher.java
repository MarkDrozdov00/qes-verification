package com.notarity.validator.registry;

import com.notarity.validator.api.ApiModels.*;
import com.notarity.validator.registry.PrototypeRegistryModels.SignedPrototypeRegistryRecord;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class CertificateFingerprintParticipantMatcher implements ParticipantEvidenceMatcher {
    @Override
    public List<ParticipantMatch> match(SignedPrototypeRegistryRecord record, List<SignatureResult> signatures) {
        if (record == null || record.payload() == null || record.payload().participants() == null) return List.of();
        return record.payload().participants().stream().map(participant -> {
            String expected = normalize(participant.certificateSha256Fingerprint());
            SignatureResult matched = expected.isEmpty() ? null : signatures.stream()
                .filter(signature -> expected.equals(normalize(signature.certificate().sha256Fingerprint())))
                .findFirst().orElse(null);
            ParticipantStatus status = expected.isEmpty() ? ParticipantStatus.INDETERMINATE
                : matched == null ? ParticipantStatus.NOT_FOUND : ParticipantStatus.MATCHED;
            return new ParticipantMatch(role(participant.role()), participant.displayName(),
                matched == null ? null : matched.id(), participant.certificateSha256Fingerprint(),
                matched == null ? Boolean.FALSE : Boolean.TRUE, participant.signedAt(), status);
        }).toList();
    }

    public SignatureResult findNotary(SignedPrototypeRegistryRecord record, List<SignatureResult> signatures) {
        if (record == null || record.payload().notary() == null) return null;
        String expected = normalize(record.payload().notary().certificateSha256Fingerprint());
        return expected.isEmpty() ? null : signatures.stream()
            .filter(signature -> expected.equals(normalize(signature.certificate().sha256Fingerprint())))
            .findFirst().orElse(null);
    }

    public static String normalize(String value) {
        return value == null ? "" : value.replace(":", "").replace(" ", "").toLowerCase(Locale.ROOT);
    }
    private com.notarity.validator.api.ApiModels.ParticipantRole role(
        PrototypeRegistryModels.ParticipantRole role) {
        if (role == null) return com.notarity.validator.api.ApiModels.ParticipantRole.OTHER;
        return com.notarity.validator.api.ApiModels.ParticipantRole.valueOf(role.name());
    }
}

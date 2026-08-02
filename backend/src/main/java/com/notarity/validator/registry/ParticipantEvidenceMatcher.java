package com.notarity.validator.registry;

import com.notarity.validator.api.ApiModels.ParticipantMatch;
import com.notarity.validator.api.ApiModels.SignatureResult;
import com.notarity.validator.registry.PrototypeRegistryModels.SignedPrototypeRegistryRecord;
import java.util.List;

public interface ParticipantEvidenceMatcher {
    List<ParticipantMatch> match(SignedPrototypeRegistryRecord record, List<SignatureResult> documentSignatures);
}

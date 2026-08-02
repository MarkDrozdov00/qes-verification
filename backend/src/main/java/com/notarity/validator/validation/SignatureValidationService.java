package com.notarity.validator.validation;

import com.notarity.validator.api.ApiModels.DocumentValidationResponse;
import com.notarity.validator.document.DocumentMetadata;

public interface SignatureValidationService {
    DocumentValidationResponse validate(byte[] documentBytes, DocumentMetadata metadata, ValidationOptions options);
}

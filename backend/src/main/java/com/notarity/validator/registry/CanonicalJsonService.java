package com.notarity.validator.registry;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.notarity.validator.registry.PrototypeRegistryModels.PrototypeNotarizationRecord;
import org.springframework.stereotype.Component;

@Component
public class CanonicalJsonService {
    private final ObjectMapper mapper;

    public CanonicalJsonService(ObjectMapper applicationMapper) {
        this.mapper = applicationMapper.copy()
            .setSerializationInclusion(JsonInclude.Include.ALWAYS)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(SerializationFeature.INDENT_OUTPUT, false);
    }

    public byte[] canonicalize(PrototypeNotarizationRecord payload) {
        try {
            return mapper.writeValueAsBytes(payload);
        } catch (Exception exception) {
            throw new CanonicalizationException(exception);
        }
    }

    public static final class CanonicalizationException extends RuntimeException {
        CanonicalizationException(Throwable cause) { super(cause); }
    }
}

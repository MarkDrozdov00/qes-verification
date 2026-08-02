package com.notarity.validator.validation.dss;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Component
public class ValidationDiagnosticContext {
    private static final Logger log = LoggerFactory.getLogger(ValidationDiagnosticContext.class);
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    public String begin(String sanitizedFilename) {
        String validationId = "val_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        CURRENT.set(new State(validationId, sanitizedFilename, DssProcessingStage.DOCUMENT_INSPECTION));
        log.debug("validationId={} filename={} stage={}", validationId, sanitizedFilename,
            DssProcessingStage.DOCUMENT_INSPECTION);
        return validationId;
    }

    public void stage(DssProcessingStage stage) {
        State state = CURRENT.get();
        if (state != null) {
            CURRENT.set(new State(state.validationId(), state.sanitizedFilename(), stage));
            log.debug("validationId={} filename={} stage={}", state.validationId(), state.sanitizedFilename(), stage);
        }
    }

    public State current() {
        return CURRENT.get();
    }

    public void clear() {
        CURRENT.remove();
    }

    public record State(String validationId, String sanitizedFilename, DssProcessingStage stage) {}
}

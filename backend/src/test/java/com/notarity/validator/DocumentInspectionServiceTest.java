package com.notarity.validator;

import com.notarity.validator.document.DocumentInspectionService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentInspectionServiceTest {
    @Test void calculatesSha256InLowercaseHex() {
        assertThat(DocumentInspectionService.sha256("abc".getBytes()))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}

package com.notarity.validator.validation.dss;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SignerDisplayNameNormalizerTest {
    private final SignerDisplayNameNormalizer names = new SignerDisplayNameNormalizer();

    @Test
    void normalizesUppercasePersonalNamesWithoutLosingPunctuationOrDiacritics() {
        assertThat(names.person("SEBASTIAN WODNIANSKY-WILDENFELD"))
            .isEqualTo("Sebastian Wodniansky-Wildenfeld");
        assertThat(names.person("ÉLISE D'ARCY")).isEqualTo("Élise D'Arcy");
    }

    @Test
    void preservesMeaningfulMixedCasePersonalNames() {
        assertThat(names.person("Ludwig van Beethoven")).isEqualTo("Ludwig van Beethoven");
    }

    @Test
    void normalizesOrganizationsConservativelyAndPreservesLegalSuffixes() {
        assertThat(names.organization("notarity gmbh")).isEqualTo("Notarity GmbH");
        assertThat(names.organization("ACME ag")).isEqualTo("ACME AG");
        assertThat(names.organization("Example e.u.")).isEqualTo("Example e.U.");
    }
}

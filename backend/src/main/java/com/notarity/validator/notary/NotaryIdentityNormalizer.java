package com.notarity.validator.notary;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

@Component
public class NotaryIdentityNormalizer {
    private static final Set<String> TITLES = Set.of(
        "dr", "drin", "mag", "magister", "magistra", "mmag", "prof", "dipl", "ing");

    public String normalize(String value) {
        if (value == null || value.isBlank()) return "";
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .toLowerCase(Locale.ROOT)
            .replace("ß", "ss")
            .replaceAll("[^\\p{L}\\p{N}]+", " ")
            .trim().replaceAll("\\s+", " ");
        return java.util.Arrays.stream(ascii.split(" "))
            .filter(token -> !TITLES.contains(token))
            .reduce((left, right) -> left + " " + right).orElse("");
    }
}

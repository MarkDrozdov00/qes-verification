package com.notarity.validator.validation.dss;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class SignerDisplayNameNormalizer {
    private static final Pattern WORD_BOUNDARY = Pattern.compile("([\\s\\-'])");
    private static final Set<String> LEGAL_SUFFIXES = Set.of("gmbh", "ag", "se", "ltd", "llc", "e.u.");
    private static final Map<String, String> CANONICAL_SUFFIXES = Map.of(
        "gmbh", "GmbH", "ag", "AG", "se", "SE", "ltd", "Ltd", "llc", "LLC", "e.u.", "e.U.");

    public String person(String value) {
        if (value == null || value.isBlank() || !isAllUppercase(value)) return value;
        String[] parts = WORD_BOUNDARY.split(value, -1);
        var separators = WORD_BOUNDARY.matcher(value);
        StringBuilder normalized = new StringBuilder();
        for (int index = 0; index < parts.length; index++) {
            normalized.append(titleCase(parts[index]));
            if (separators.find()) normalized.append(separators.group());
        }
        return normalized.toString();
    }

    public String organization(String value) {
        if (value == null || value.isBlank()) return value;
        String[] words = value.trim().split("\\s+");
        for (int index = 0; index < words.length; index++) {
            String key = words[index].toLowerCase(Locale.ROOT);
            if (LEGAL_SUFFIXES.contains(key)) words[index] = CANONICAL_SUFFIXES.get(key);
            else if (index == 0 && words[index].equals(key)) words[index] = titleCase(words[index]);
        }
        return String.join(" ", words);
    }

    private boolean isAllUppercase(String value) {
        boolean containsLetter = value.codePoints().anyMatch(Character::isLetter);
        return containsLetter && value.equals(value.toUpperCase(Locale.ROOT));
    }

    private String titleCase(String word) {
        if (word == null || word.isEmpty()) return word;
        int first = word.codePointAt(0);
        int offset = Character.charCount(first);
        return new String(Character.toChars(Character.toTitleCase(first)))
            + word.substring(offset).toLowerCase(Locale.ROOT);
    }
}

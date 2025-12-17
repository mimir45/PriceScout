package com.samir.pricecomparator.util;

import java.text.Normalizer;

public class TextNormalizationUtil {

    private TextNormalizationUtil() {
    }

    public static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "");

        normalized = normalized.toLowerCase().trim();

        normalized = normalized.replaceAll("\\s+", " ");

        normalized = normalized.replaceAll("[^a-z0-9\\s]", " ");

        return normalized.trim();
    }


    public static String extractCleanName(String title) {
        String normalized = normalize(title);

        normalized = normalized.replaceAll("\\b(new|used|original|authentic|official)\\b", "");

        return normalized.trim();
    }
}

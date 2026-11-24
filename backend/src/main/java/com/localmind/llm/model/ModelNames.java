package com.localmind.llm.model;

import java.util.Locale;

/**
 * Ollama model-name rules shared by every lookup, so the startup check, the default model in {@code /api/models} and
 * tool-support decisions always agree on which installed model a configured name means.
 */
public final class ModelNames {

    public static final String LATEST_TAG = ":latest";

    private ModelNames() {
    }

    public static boolean matches(String candidate, String requested) {
        if (isBlank(candidate) || isBlank(requested)) {
            return false;
        }
        return canonical(candidate).equals(canonical(requested));
    }

    public static String canonical(String name) {
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        return normalized.endsWith(LATEST_TAG)
                ? normalized.substring(0, normalized.length() - LATEST_TAG.length())
                : normalized;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

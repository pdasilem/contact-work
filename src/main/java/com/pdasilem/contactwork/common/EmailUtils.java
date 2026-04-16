package com.pdasilem.contactwork.common;

import java.util.Locale;
import java.util.regex.Pattern;

public final class EmailUtils {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

    private EmailUtils() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String value) {
        String normalized = normalize(value);
        return normalized != null && EMAIL_PATTERN.matcher(normalized).matches();
    }
}

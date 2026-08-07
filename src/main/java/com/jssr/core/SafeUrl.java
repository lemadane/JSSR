package com.jssr.core;

import java.util.Locale;

/**
 * Wrapper and utility for safe URLs that sanitizes dangerous protocols like javascript:, data:, and vbscript:.
 */
public record SafeUrl(String value) implements JssrComponent {

    public static SafeUrl of(String value) {
        return new SafeUrl(value);
    }

    /**
     * Sanitize a URL string to prevent protocol-based XSS attacks.
     *
     * @param url Input URL
     * @return Sanitized URL or 'about:blank' if unsafe protocol is detected
     */
    public static String sanitize(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        String trimmed = url.trim().toLowerCase(Locale.ROOT);
        if (trimmed.startsWith("javascript:") || trimmed.startsWith("vbscript:") || trimmed.startsWith("data:text/html")) {
            return "about:blank";
        }
        return url;
    }

    @Override
    public String template() {
        return sanitize(value);
    }

    @Override
    public String toString() {
        return sanitize(value);
    }
}

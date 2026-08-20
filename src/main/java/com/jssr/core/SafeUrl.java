package com.jssr.core;

import java.util.Locale;

/**
 * Wrapper and utility for safe URLs that enforces a strict protocol allowlist
 * (http:, https:, mailto:, tel:, relative URLs, #, ?, /) and blocks dangerous protocols
 * like javascript:, vbscript:, and data: (including HTML entity-encoded variations).
 */
public record SafeUrl(String value) implements JssrComponent {

    public static SafeUrl of(String value) {
        return new SafeUrl(value);
    }

    /**
     * Sanitize a URL string using a strict protocol allowlist.
     *
     * @param url Input URL string
     * @return Sanitized URL or 'about:blank' if unsafe
     */
    public static String sanitize(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }

        String raw = url.trim();

        // Unescape HTML entities before protocol checking to prevent entity bypasses (e.g. java&#115;cript:)
        String unescaped = JssrComponent.unescapeHtml(raw).trim().toLowerCase(Locale.ROOT);

        // Strip non-printable ASCII / control characters
        StringBuilder cleanScheme = new StringBuilder();
        for (int i = 0; i < unescaped.length(); i++) {
            char c = unescaped.charAt(i);
            if (c > 32 && c < 127) {
                cleanScheme.append(c);
            }
        }
        String normalized = cleanScheme.toString();

        // Allow relative URLs, fragment IDs, query strings, and absolute path URLs
        if (normalized.startsWith("/") || normalized.startsWith("#") || normalized.startsWith("?") || normalized.startsWith(".")) {
            return raw;
        }

        int colonIndex = normalized.indexOf(':');
        if (colonIndex == -1) {
            // No scheme specified; treat as relative URL path
            return raw;
        }

        String scheme = normalized.substring(0, colonIndex);
        if ("http".equals(scheme) || "https".equals(scheme) || "mailto".equals(scheme) || "tel".equals(scheme)) {
            return raw;
        }

        // Unsafe or unrecognized protocol scheme
        return "about:blank";
    }

    @Override
    public String render() {
        return sanitize(value);
    }

    @Override
    public String toString() {
        return sanitize(value);
    }
}

package com.jssr.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Type-safe wrapper for space-separated HTML URL attributes (such as <a ping="..."> and <area ping="...">)
 * that parses and individually sanitizes every space-separated URL candidate against dangerous protocols.
 */
public record SafeUrlList(String value) implements JssrComponent {

    public static SafeUrlList of(String value) {
        return new SafeUrlList(value);
    }

    /**
     * Parse and sanitize each space-separated URL in a url list string.
     *
     * @param urlList Raw space-separated URL list string (e.g. "https://analytics.org/ping javascript:alert(1)")
     * @return Sanitized URL list string with unsafe URLs sanitized to 'about:blank'
     */
    public static String sanitize(String urlList) {
        if (urlList == null || urlList.isBlank()) {
            return "";
        }

        String[] urls = urlList.trim().split("\\s+");
        List<String> sanitizedList = new ArrayList<>();

        for (String rawUrl : urls) {
            if (rawUrl.isBlank()) {
                continue;
            }
            sanitizedList.add(SafeUrl.sanitize(rawUrl.trim()));
        }

        return String.join(" ", sanitizedList);
    }

    public String render() {
        return sanitize(value);
    }

    @Override
    public String template() {
        return render();
    }

    @Override
    public String toString() {
        return render();
    }
}

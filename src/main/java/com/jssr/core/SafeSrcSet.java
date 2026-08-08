package com.jssr.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Type-safe wrapper for image 'srcset' and 'imagesrcset' attributes that parses and individually
 * sanitizes every comma-separated candidate URL against dangerous protocols (javascript:, data:, vbscript:).
 */
public record SafeSrcSet(String value) implements JssrComponent {

    public static SafeSrcSet of(String value) {
        return new SafeSrcSet(value);
    }

    /**
     * Parse and sanitize each comma-separated candidate URL in a srcset string.
     *
     * @param srcset Raw srcset string (e.g. "/img/a.png 1x, javascript:alert(1) 2x")
     * @return Sanitized srcset string with unsafe candidate URLs sanitized to 'about:blank'
     */
    public static String sanitize(String srcset) {
        if (srcset == null || srcset.isBlank()) {
            return "";
        }

        String[] candidates = srcset.split(",");
        List<String> sanitizedCandidates = new ArrayList<>();

        for (String rawCandidate : candidates) {
            String candidate = rawCandidate.trim();
            if (candidate.isEmpty()) {
                continue;
            }

            String[] parts = candidate.split("\\s+", 2);
            String urlPart = parts[0];
            String descriptorPart = parts.length > 1 ? parts[1].trim() : "";

            String sanitizedUrl = SafeUrl.sanitize(urlPart);
            if (!descriptorPart.isEmpty()) {
                sanitizedCandidates.add(sanitizedUrl + " " + descriptorPart);
            } else {
                sanitizedCandidates.add(sanitizedUrl);
            }
        }

        return String.join(", ", sanitizedCandidates);
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

package com.jssr.core;

import java.util.Locale;
import java.util.Set;

/**
 * Type-safe representation for dynamic HTML attribute pairs (e.g. data-role="admin").
 * Enforces strict attribute name syntax validation and security blocklists.
 */
public record HtmlAttribute(String name, String value) implements JssrComponent {

    private static final Set<String> FORBIDDEN_EXACT_ATTRIBUTES = Set.of(
        "style", "srcdoc", "href", "src", "action", "formaction", "poster", "xlink:href"
    );

    public HtmlAttribute {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("HtmlAttribute name cannot be null or blank.");
        }
        String cleanName = name.trim();

        // 1. Syntactic validation: no spaces, quotes, equals, or control chars
        if (!cleanName.matches("^[a-zA-Z0-9_:-]+$")) {
            throw new IllegalArgumentException("Invalid HTML attribute name '" + name 
                    + "'. Attribute names must contain only alphanumeric characters, underscores, colons, or hyphens.");
        }

        String lowerName = cleanName.toLowerCase(Locale.ROOT);

        // 2. Security blocklist: event handlers, inline style, srcdoc, framework attributes, and URL attributes
        if (FORBIDDEN_EXACT_ATTRIBUTES.contains(lowerName) 
                || lowerName.startsWith("on") 
                || lowerName.startsWith("x-") 
                || lowerName.startsWith("@") 
                || lowerName.startsWith(":") 
                || lowerName.startsWith("hx-on")) {
            throw new IllegalArgumentException("Unsafe HTML attribute name '" + name 
                    + "' in HtmlAttribute. Attribute names starting with 'on', 'x-', '@', ':', 'hx-on', 'style', 'srcdoc', or URL attributes are forbidden. For URL attributes like 'href', use SafeUrl or standard template attributes.");
        }
    }

    public static HtmlAttribute of(String name, String value) {
        return new HtmlAttribute(name, value);
    }

    @Override
    public String template() {
        if (value == null) {
            return name;
        }
        return name + "=\"" + JssrComponent.escapeHtml(value) + "\"";
    }

    @Override
    public String toString() {
        return template();
    }
}

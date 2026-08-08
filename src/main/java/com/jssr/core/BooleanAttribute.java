package com.jssr.core;

import java.util.Locale;
import java.util.Set;

/**
 * Type-safe representation for boolean HTML attributes (e.g. checked, disabled, selected, readonly).
 * Enforces a strict allowlist of standard HTML boolean attribute names.
 */
public record BooleanAttribute(String name, boolean present) implements JssrComponent {

    private static final Set<String> ALLOWED_BOOLEAN_ATTRIBUTES = Set.of(
        "allowfullscreen", "async", "autofocus", "autoplay", "checked", "controls",
        "default", "defer", "disabled", "formnovalidate", "hidden", "inert",
        "ismap", "itemscope", "loop", "multiple", "muted", "nomodule",
        "novalidate", "open", "playsinline", "readonly", "required", "reversed", "selected"
    );

    public BooleanAttribute {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("BooleanAttribute name cannot be null or blank.");
        }
        String cleanName = name.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_BOOLEAN_ATTRIBUTES.contains(cleanName)) {
            throw new IllegalArgumentException("Invalid or unsafe boolean HTML attribute name '" + name 
                    + "'. Must be a standard HTML boolean attribute (e.g. checked, disabled, selected, readonly).");
        }
    }

    public static BooleanAttribute of(String name, boolean present) {
        return new BooleanAttribute(name, present);
    }

    public static BooleanAttribute present(String name) {
        return new BooleanAttribute(name, true);
    }

    public static BooleanAttribute absent(String name) {
        return new BooleanAttribute(name, false);
    }

    @Override
    public String template() {
        return present ? name : "";
    }

    @Override
    public String toString() {
        return template();
    }
}

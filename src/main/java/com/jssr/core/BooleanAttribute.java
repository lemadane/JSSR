package com.jssr.core;

/**
 * Type-safe representation for boolean HTML attributes (e.g. checked, disabled, selected, readonly).
 */
public record BooleanAttribute(String name, boolean present) implements JssrComponent {

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
        return present ? (name == null ? "" : name) : "";
    }

    @Override
    public String toString() {
        return template();
    }
}

package com.jssr.core;

/**
 * Type-safe representation for dynamic HTML attribute pairs (e.g. data-role="admin").
 */
public record HtmlAttribute(String name, String value) implements JssrComponent {

    public static HtmlAttribute of(String name, String value) {
        return new HtmlAttribute(name, value);
    }

    @Override
    public String template() {
        if (name == null || name.isBlank()) {
            return "";
        }
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

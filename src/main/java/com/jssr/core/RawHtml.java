package com.jssr.core;

/**
 * Wrapper for trusted raw HTML content that should not be HTML-escaped during JSSR template rendering.
 */
public record RawHtml(String value) implements JssrComponent {

    public static RawHtml of(String value) {
        return new RawHtml(value);
    }

    public static RawHtml trustedHtml(String value) {
        return new RawHtml(value);
    }

    @Override
    public String render() {
        return value == null ? "" : value;
    }

    @Override
    public String toString() {
        return value == null ? "" : value;
    }
}

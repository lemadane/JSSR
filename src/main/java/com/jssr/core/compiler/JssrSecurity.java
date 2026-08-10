package com.jssr.core.compiler;

/**
 * Security helper methods invoked by precompiled bytecode templates to enforce
 * 100% security parity with the interpreted JSSR component rendering engine.
 */
public final class JssrSecurity {

    private JssrSecurity() {}

    public static void rejectScriptInterpolation(String expr) {
        throw new IllegalArgumentException("JSSR interpolation ${" + expr + "} inside <script> blocks is forbidden for security.");
    }

    public static void rejectStyleInterpolation(String expr) {
        throw new IllegalArgumentException("JSSR interpolation ${" + expr + "} inside <style> blocks is forbidden for security.");
    }

    public static void rejectCommentInterpolation(String expr) {
        throw new IllegalArgumentException("JSSR interpolation ${" + expr + "} inside HTML comments is forbidden for security.");
    }

    public static void rejectUnquotedAttribute(String expr, String attr) {
        throw new IllegalArgumentException("JSSR interpolation in an unquoted HTML attribute is forbidden. Quote the attribute value: " + attr + "=\"${" + expr + "}\"");
    }

    public static void rejectSrcdocAttribute(String expr) {
        throw new IllegalArgumentException("JSSR interpolation ${" + expr + "} inside 'srcdoc' attribute is forbidden due to HTML nested decoding risks.");
    }

    public static void rejectFrameworkAttribute(String expr, String attr) {
        throw new IllegalArgumentException("JSSR interpolation ${" + expr + "} is not allowed inside executable framework attribute '" + attr + "'. Use safe server-side state or explicit expression APIs.");
    }

    public static void rejectEventHandlerAttribute(String expr, String attr) {
        throw new IllegalArgumentException("JSSR interpolation ${" + expr + "} is not allowed inside inline event handler attribute '" + attr + "'. Use HTMX/Alpine.js attributes or unobtrusive event listeners.");
    }

    public static void rejectStyleAttribute(String expr) {
        throw new IllegalArgumentException("JSSR interpolation ${" + expr + "} is not allowed inside inline style attribute 'style'. Use CSS custom properties or external stylesheets.");
    }

    public static void rejectInvalidUrl(String expr, String attr) {
        throw new IllegalArgumentException("JSSR interpolation ${" + expr + "} inside URL attribute '" + attr + "' requires a SafeUrl field type.");
    }

    public static void rejectInvalidSrcSet(String expr, String attr) {
        throw new IllegalArgumentException("JSSR interpolation ${" + expr + "} inside multi-candidate image attribute '" + attr + "' requires a SafeSrcSet field type.");
    }

    public static void rejectInvalidUrlList(String expr, String attr) {
        throw new IllegalArgumentException("JSSR interpolation ${" + expr + "} inside space-separated URL attribute '" + attr + "' requires a SafeUrlList field type.");
    }

    public static void rejectRawHtmlInAttribute(String expr, String attr) {
        throw new IllegalArgumentException("RawHtml cannot be interpolated inside an HTML attribute. Use safe string values, SafeUrl, BooleanAttribute, or HtmlAttribute.");
    }
}

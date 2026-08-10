package com.jssr.core.compiler;

import com.jssr.core.JssrComponent;
import java.util.Locale;

/**
 * Security helper methods invoked by precompiled bytecode templates to enforce
 * 100% security parity with the interpreted JSSR component rendering engine.
 */
public final class JssrSecurity {

    private JssrSecurity() {}

    public enum AttributeContext {
        STANDARD,
        URL,
        SRCSET,
        URL_LIST,
        EVENT_HANDLER,
        STYLE,
        SRCDOC,
        FRAMEWORK_EXPRESSION
    }

    /**
     * Classify an HTML attribute name into its security policy context.
     * Shared across both interpreted and precompiled AST rendering engines.
     *
     * @param attr Raw HTML attribute name
     * @return AttributeContext classification
     */
    public static AttributeContext classifyAttribute(String attr) {
        if (attr == null || attr.isEmpty()) {
            return AttributeContext.STANDARD;
        }
        String lower = attr.toLowerCase(Locale.ROOT);
        if ("srcdoc".equals(lower)) {
            return AttributeContext.SRCDOC;
        }
        if (lower.startsWith("x-") || lower.startsWith("@") || lower.startsWith(":") || lower.startsWith("hx-on")) {
            return AttributeContext.FRAMEWORK_EXPRESSION;
        }
        if (lower.startsWith("on")) {
            return AttributeContext.EVENT_HANDLER;
        }
        if ("style".equals(lower)) {
            return AttributeContext.STYLE;
        }
        if ("srcset".equals(lower) || "imagesrcset".equals(lower)) {
            return AttributeContext.SRCSET;
        }
        if ("ping".equals(lower)) {
            return AttributeContext.URL_LIST;
        }
        if (JssrComponent.URL_ATTRIBUTES.contains(lower)) {
            return AttributeContext.URL;
        }
        return AttributeContext.STANDARD;
    }

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

    public static void rejectFreestandingAttribute(String expr, String typeName) {
        throw new IllegalArgumentException("JSSR interpolation ${" + expr + "} of type " + typeName + " in free-standing HTML attribute position is forbidden. Use boolean fields, BooleanAttribute, or HtmlAttribute for dynamic attributes.");
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

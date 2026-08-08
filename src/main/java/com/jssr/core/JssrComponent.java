package com.jssr.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core interface for Record-based Java Server-Side Rendering (JSSR) components.
 * Every UI component is an immutable Java Record implementing JssrComponent.
 */
public interface JssrComponent {

    /**
     * Central registry for custom child component tags.
     */
    Map<String, Class<? extends JssrComponent>> REGISTRY = new ConcurrentHashMap<>();

    /**
     * ThreadLocal tracking rendering depth to prevent infinite component recursion.
     */
    ThreadLocal<Integer> RENDER_DEPTH = ThreadLocal.withInitial(() -> 0);

    /**
     * Maximum allowed component nesting depth before throwing a recursion error.
     */
    int MAX_RENDER_DEPTH = 100;

    /**
     * Register a custom component tag.
     *
     * @param tagName Tag name (e.g. "UserCard")
     * @param clazz Component class implementing JssrComponent
     */
    static void register(String tagName, Class<? extends JssrComponent> clazz) {
        REGISTRY.put(tagName, clazz);
    }

    /**
     * Component HTML template method implemented by JSSR component Records.
     *
     * @return Native Java 17 multiline text block string
     */
    String template();

    /**
     * Primary entry point. Interpolates ${fieldName} variables in a single pass, renders the template,
     * and automatically processes custom tags with depth recursion protection.
     *
     * @return Fully rendered HTML string with resolved variables and child tags
     */
    default String render() {
        int depth = RENDER_DEPTH.get();
        if (depth > MAX_RENDER_DEPTH) {
            throw new IllegalStateException("JSSR component recursion limit exceeded (max depth " 
                    + MAX_RENDER_DEPTH + ") for component: " + getClass().getSimpleName());
        }

        RENDER_DEPTH.set(depth + 1);
        try {
            String rawHtml = template();
            if (rawHtml == null || rawHtml.isBlank()) {
                return rawHtml == null ? "" : rawHtml;
            }

            String interpolatedHtml = interpolateVariables(this, rawHtml);
            return processCustomTags(interpolatedHtml);
        } finally {
            RENDER_DEPTH.set(depth);
        }
    }

    /**
     * Escape special HTML characters to prevent XSS.
     *
     * @param input Raw text input
     * @return HTML-escaped text
     */
    static String escapeHtml(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Unescape standard HTML entities (&amp;, &lt;, &gt;, &quot;, &#39;) to raw text.
     *
     * @param input HTML-escaped text input
     * @return Unescaped text
     */
    static String unescapeHtml(String input) {
        if (input == null || input.isEmpty() || !input.contains("&")) {
            return input == null ? "" : input;
        }
        return input.replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'");
    }

    /**
     * Sanitize a URL string to prevent dangerous protocols like javascript:, vbscript:, and data:.
     *
     * @param url Input URL string
     * @return Sanitized URL
     */
    static String sanitizeUrl(String url) {
        return SafeUrl.sanitize(url);
    }

    /**
     * Create a RawHtml wrapper for trusted unescaped HTML content.
     *
     * @param input Raw trusted HTML content
     * @return RawHtml record instance
     */
    static RawHtml trustedHtml(String input) {
        return RawHtml.trustedHtml(input);
    }

    /**
     * Interpolate ${fieldName} placeholders in HTML templates using a context-aware single-pass scanner to prevent
     * cascading interpolation or unsafe variable evaluation inside <script>, <style>, and comment blocks.
     *
     * @param component The component instance
     * @param html HTML template string containing ${fieldName} placeholders
     * @return HTML string with interpolated variable values
     */
    static String interpolateVariables(JssrComponent component, String html) {
        if (component == null || html == null || html.isBlank() || !html.contains("${")) {
            return html == null ? "" : html;
        }

        Class<?> clazz = component.getClass();
        if (!clazz.isRecord()) {
            return html;
        }

        RecordComponent[] recordComponents = clazz.getRecordComponents();
        Map<String, String> valuesMap = new HashMap<>();
        for (RecordComponent rc : recordComponents) {
            try {
                Method accessor = rc.getAccessor();
                accessor.setAccessible(true);
                Object val = accessor.invoke(component);
                String valStr;
                if (val == null) {
                    valStr = "";
                } else if (val instanceof RawHtml raw) {
                    valStr = raw.value() == null ? "" : raw.value();
                } else if (val instanceof SafeUrl safe) {
                    valStr = escapeHtml(safe.render());
                } else if (val instanceof JssrComponent jc) {
                    valStr = jc.render();
                } else {
                    valStr = escapeHtml(val.toString());
                }
                valuesMap.put(rc.getName(), valStr);
            } catch (Exception e) {
                throw new RuntimeException("JSSR render error: Unable to read property '"
                        + rc.getName() + "' from component " + clazz.getSimpleName(), e);
            }
        }

        StringBuilder sb = new StringBuilder(html.length() + 32);
        int len = html.length();
        int i = 0;

        boolean inTag = false;
        char quoteChar = 0;
        String blockContext = null; // "script", "style", "comment"

        while (i < len) {
            // Check for variable placeholder ${varName}
            if (i + 1 < len && html.charAt(i) == '$' && html.charAt(i + 1) == '{') {
                int end = html.indexOf('}', i + 2);
                if (end != -1) {
                    String varName = html.substring(i + 2, end).trim();
                    if (blockContext != null) {
                        if ("script".equals(blockContext)) {
                            throw new IllegalArgumentException("JSSR interpolation ${" + varName 
                                    + "} is not allowed inside <script> blocks without an explicit safe JavaScript value API.");
                        } else if ("style".equals(blockContext)) {
                            throw new IllegalArgumentException("JSSR interpolation ${" + varName 
                                    + "} is not allowed inside <style> blocks.");
                        } else if ("comment".equals(blockContext)) {
                            throw new IllegalArgumentException("JSSR interpolation ${" + varName 
                                    + "} is not allowed inside HTML comments.");
                        }
                    }

                    if (valuesMap.containsKey(varName)) {
                        sb.append(valuesMap.get(varName));
                    } else {
                        sb.append("${").append(varName).append("}");
                    }
                    i = end + 1;
                    continue;
                }
            }

            char c = html.charAt(i);

            if ("comment".equals(blockContext)) {
                if (i + 2 < len && html.startsWith("-->", i)) {
                    sb.append("-->");
                    i += 3;
                    blockContext = null;
                    continue;
                }
            } else if ("script".equals(blockContext)) {
                if (i + 8 < len && html.substring(i, i + 9).toLowerCase(Locale.ROOT).equals("</script>")) {
                    sb.append(html, i, i + 9);
                    i += 9;
                    blockContext = null;
                    continue;
                }
            } else if ("style".equals(blockContext)) {
                if (i + 7 < len && html.substring(i, i + 8).toLowerCase(Locale.ROOT).equals("</style>")) {
                    sb.append(html, i, i + 8);
                    i += 8;
                    blockContext = null;
                    continue;
                }
            } else {
                if (c == '<') {
                    if (i + 3 < len && html.startsWith("<!--", i)) {
                        blockContext = "comment";
                    } else if (i + 6 < len && html.substring(i, Math.min(i + 7, len)).toLowerCase(Locale.ROOT).startsWith("<script")) {
                        char next = i + 7 < len ? html.charAt(i + 7) : '>';
                        if (next == ' ' || next == '\t' || next == '\n' || next == '\r' || next == '>') {
                            blockContext = "script";
                        }
                    } else if (i + 5 < len && html.substring(i, Math.min(i + 6, len)).toLowerCase(Locale.ROOT).startsWith("<style")) {
                        char next = i + 6 < len ? html.charAt(i + 6) : '>';
                        if (next == ' ' || next == '\t' || next == '\n' || next == '\r' || next == '>') {
                            blockContext = "style";
                        }
                    }
                    inTag = true;
                } else if (inTag) {
                    if (quoteChar != 0) {
                        if (c == quoteChar) {
                            quoteChar = 0;
                        }
                    } else {
                        if (c == '"' || c == '\'') {
                            quoteChar = c;
                        } else if (c == '>') {
                            inTag = false;
                        }
                    }
                }
            }

            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /**
     * Process custom JSX-like child tags inside rendered HTML strings using a context-aware state machine parser
     * skipping comments, scripts, styles, and quoted attribute values, with nesting depth tracking for paired tags.
     *
     * @param html HTML input string containing custom tags
     * @return Rendered HTML string with custom tags replaced by component HTML
     */
    static String processCustomTags(String html) {
        if (REGISTRY.isEmpty() || html == null || html.isBlank()) {
            return html == null ? "" : html;
        }

        StringBuilder sb = new StringBuilder();
        int len = html.length();
        int i = 0;

        while (i < len) {
            int openBracket = html.indexOf('<', i);
            if (openBracket == -1) {
                sb.append(html.substring(i));
                break;
            }

            sb.append(html, i, openBracket);
            i = openBracket;

            // 1. Skip HTML comments: <!-- ... -->
            if (i + 4 <= len && html.startsWith("<!--", i)) {
                int commentEnd = html.indexOf("-->", i + 4);
                if (commentEnd != -1) {
                    int endPos = commentEnd + 3;
                    sb.append(html, i, endPos);
                    i = endPos;
                    continue;
                }
            }

            // 2. Skip <script ...>...</script>
            if (i + 7 <= len && html.substring(i, Math.min(i + 8, len)).toLowerCase(Locale.ROOT).startsWith("<script")) {
                int tagClose = html.indexOf('>', i);
                if (tagClose != -1) {
                    int scriptEnd = html.toLowerCase(Locale.ROOT).indexOf("</script>", tagClose + 1);
                    if (scriptEnd != -1) {
                        int endPos = scriptEnd + 9;
                        sb.append(html, i, endPos);
                        i = endPos;
                        continue;
                    }
                }
            }

            // 3. Skip <style ...>...</style>
            if (i + 6 <= len && html.substring(i, Math.min(i + 7, len)).toLowerCase(Locale.ROOT).startsWith("<style")) {
                int tagClose = html.indexOf('>', i);
                if (tagClose != -1) {
                    int styleEnd = html.toLowerCase(Locale.ROOT).indexOf("</style>", tagClose + 1);
                    if (styleEnd != -1) {
                        int endPos = styleEnd + 8;
                        sb.append(html, i, endPos);
                        i = endPos;
                        continue;
                    }
                }
            }

            int tagStart = i + 1;
            if (tagStart < len && Character.isUpperCase(html.charAt(tagStart))) {
                int tagNameEnd = tagStart;
                while (tagNameEnd < len && Character.isLetterOrDigit(html.charAt(tagNameEnd))) {
                    tagNameEnd++;
                }

                String tagName = html.substring(tagStart, tagNameEnd);
                if (tagNameEnd < len) {
                    char delim = html.charAt(tagNameEnd);
                    if (delim == ' ' || delim == '\t' || delim == '\n' || delim == '\r' || delim == '/' || delim == '>') {
                        if (REGISTRY.containsKey(tagName)) {
                            int tagEnd = -1;
                            char inQuote = 0;
                            for (int j = tagNameEnd; j < len; j++) {
                                char c = html.charAt(j);
                                if (inQuote != 0) {
                                    if (c == inQuote) {
                                        inQuote = 0;
                                    }
                                } else {
                                    if (c == '"' || c == '\'') {
                                        inQuote = c;
                                    } else if (c == '>') {
                                        tagEnd = j;
                                        break;
                                    }
                                }
                            }

                            if (tagEnd != -1) {
                                String rawTagContent = html.substring(tagNameEnd, tagEnd).trim();
                                boolean isSelfClosing = rawTagContent.endsWith("/");
                                String attrString = isSelfClosing 
                                        ? rawTagContent.substring(0, rawTagContent.length() - 1).trim()
                                        : rawTagContent;

                                Class<? extends JssrComponent> clazz = REGISTRY.get(tagName);
                                Map<String, String> attrs = parseAttributes(attrString);

                                int nextIndex = tagEnd + 1;
                                boolean hasPairedBody = false;
                                if (!isSelfClosing) {
                                    int matchingClose = findMatchingClosingTagIndex(html, tagEnd + 1, tagName);
                                    if (matchingClose == -1) {
                                        throw new IllegalArgumentException("Unclosed JSSR component tag <" + tagName 
                                                + ">. Expected self-closing tag <" + tagName + " ... /> or matching closing tag </" + tagName + ">.");
                                    }
                                    String bodyContent = html.substring(tagEnd + 1, matchingClose);
                                    attrs.put("children", bodyContent);
                                    attrs.put("content", bodyContent);
                                    hasPairedBody = true;
                                    nextIndex = matchingClose + ("</" + tagName + ">").length();
                                }

                                String renderedChild = instantiateAndRender(clazz, attrs, hasPairedBody);
                                sb.append(renderedChild);
                                i = nextIndex;
                                continue;
                            }
                        }
                    }
                }
            }

            sb.append('<');
            i++;
        }

        return sb.toString();
    }

    private static int findMatchingClosingTagIndex(String html, int searchFrom, String tagName) {
        String openTagPrefix = "<" + tagName;
        String closeTag = "</" + tagName + ">";
        int len = html.length();
        int nestingDepth = 1;
        int curr = searchFrom;

        while (curr < len) {
            int nextOpen = html.indexOf(openTagPrefix, curr);
            int nextClose = html.indexOf(closeTag, curr);

            if (nextClose == -1) {
                return -1;
            }

            boolean isValidOpen = false;
            boolean isSelfClosingOpen = false;
            if (nextOpen != -1) {
                int endNameIndex = nextOpen + openTagPrefix.length();
                if (endNameIndex < len) {
                    char c = html.charAt(endNameIndex);
                    if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '/' || c == '>') {
                        isValidOpen = true;
                        int openTagEnd = html.indexOf('>', endNameIndex);
                        if (openTagEnd != -1) {
                            String rawContent = html.substring(endNameIndex, openTagEnd).trim();
                            if (rawContent.endsWith("/")) {
                                isSelfClosingOpen = true;
                            }
                        }
                    }
                }
            }

            if (isValidOpen && nextOpen < nextClose) {
                if (!isSelfClosingOpen) {
                    nestingDepth++;
                }
                curr = nextOpen + openTagPrefix.length();
            } else {
                nestingDepth--;
                if (nestingDepth == 0) {
                    return nextClose;
                }
                curr = nextClose + closeTag.length();
            }
        }
        return -1;
    }

    private static Map<String, String> parseAttributes(String attrString) {
        Map<String, String> attrs = new HashMap<>();
        if (attrString == null || attrString.isBlank()) {
            return attrs;
        }

        int len = attrString.length();
        int i = 0;
        while (i < len) {
            while (i < len && Character.isWhitespace(attrString.charAt(i))) {
                i++;
            }
            if (i >= len) break;

            int nameStart = i;
            while (i < len) {
                char c = attrString.charAt(i);
                if (Character.isWhitespace(c) || c == '=' || c == '/' || c == '>') {
                    break;
                }
                i++;
            }
            if (i == nameStart) {
                i++;
                continue;
            }
            String name = attrString.substring(nameStart, i);

            while (i < len && Character.isWhitespace(attrString.charAt(i))) {
                i++;
            }

            String value = "true";
            if (i < len && attrString.charAt(i) == '=') {
                i++; // skip '='
                while (i < len && Character.isWhitespace(attrString.charAt(i))) {
                    i++;
                }
                if (i < len) {
                    char quote = attrString.charAt(i);
                    if (quote == '"' || quote == '\'') {
                        i++; // skip open quote
                        int valStart = i;
                        while (i < len && attrString.charAt(i) != quote) {
                            i++;
                        }
                        value = attrString.substring(valStart, Math.min(i, len));
                        if (i < len) i++; // skip close quote
                    } else {
                        int valStart = i;
                        while (i < len) {
                            char c = attrString.charAt(i);
                            if (Character.isWhitespace(c) || c == '>') {
                                break;
                            }
                            if (c == '/' && i + 1 < len && attrString.charAt(i + 1) == '>') {
                                break;
                            }
                            i++;
                        }
                        value = attrString.substring(valStart, i);
                    }
                }
            }
            attrs.put(name, value);
        }
        return attrs;
    }

    private static String instantiateAndRender(Class<? extends JssrComponent> clazz, Map<String, String> attrs, boolean hasPairedBody) {
        try {
            if (clazz.isRecord()) {
                RecordComponent[] recordComponents = clazz.getRecordComponents();
                Set<String> validNames = new HashSet<>();
                boolean acceptsBody = false;
                for (RecordComponent rc : recordComponents) {
                    validNames.add(rc.getName());
                    if ("children".equals(rc.getName()) || "content".equals(rc.getName())) {
                        acceptsBody = true;
                    }
                }

                if (hasPairedBody && !acceptsBody) {
                    throw new IllegalArgumentException("Component <" + clazz.getSimpleName() 
                            + "> does not accept paired body content. Use self-closing tag <" 
                            + clazz.getSimpleName() + " ... /> or declare a 'children' or 'content' prop.");
                }

                for (String attrName : attrs.keySet()) {
                    if (!validNames.contains(attrName) && !"children".equals(attrName) && !"content".equals(attrName)) {
                        throw new IllegalArgumentException("Unknown attribute '" + attrName 
                                + "' specified for JSSR component <" + clazz.getSimpleName() + ">");
                    }
                }

                Object[] args = new Object[recordComponents.length];
                Class<?>[] paramTypes = new Class<?>[recordComponents.length];

                for (int i = 0; i < recordComponents.length; i++) {
                    RecordComponent rc = recordComponents[i];
                    paramTypes[i] = rc.getType();
                    String rawVal = attrs.get(rc.getName());
                    args[i] = convertStringValue(rawVal, rc.getType());
                }

                Constructor<? extends JssrComponent> ctor = clazz.getDeclaredConstructor(paramTypes);
                ctor.setAccessible(true);
                JssrComponent instance = ctor.newInstance(args);
                return instance.render();
            } else {
                Constructor<?> ctor = clazz.getDeclaredConstructor();
                ctor.setAccessible(true);
                JssrComponent instance = (JssrComponent) ctor.newInstance();
                return instance.render();
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("Error rendering JSSR component tag <" + clazz.getSimpleName() + ">: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object convertStringValue(String rawVal, Class<?> targetType) {
        if (rawVal == null) {
            if (targetType == boolean.class) return false;
            if (targetType == int.class) return 0;
            if (targetType == long.class) return 0L;
            if (targetType == double.class) return 0.0d;
            if (targetType == float.class) return 0.0f;
            if (targetType == short.class) return (short) 0;
            if (targetType == byte.class) return (byte) 0;
            if (targetType == char.class) return '\0';
            return null;
        }

        if (targetType == String.class || targetType == Object.class) {
            return unescapeHtml(rawVal);
        }
        if (targetType == RawHtml.class) {
            return RawHtml.of(rawVal);
        }
        if (targetType == SafeUrl.class) {
            return SafeUrl.of(unescapeHtml(rawVal));
        }
        if (targetType == Double.class || targetType == double.class) {
            return rawVal.isEmpty() ? 0.0d : Double.parseDouble(rawVal);
        }
        if (targetType == Float.class || targetType == float.class) {
            return rawVal.isEmpty() ? 0.0f : Float.parseFloat(rawVal);
        }
        if (targetType == Long.class || targetType == long.class) {
            return rawVal.isEmpty() ? 0L : Long.parseLong(rawVal);
        }
        if (targetType == Integer.class || targetType == int.class) {
            return rawVal.isEmpty() ? 0 : Integer.parseInt(rawVal);
        }
        if (targetType == Short.class || targetType == short.class) {
            return rawVal.isEmpty() ? (short) 0 : Short.parseShort(rawVal);
        }
        if (targetType == Byte.class || targetType == byte.class) {
            return rawVal.isEmpty() ? (byte) 0 : Byte.parseByte(rawVal);
        }
        if (targetType == Character.class || targetType == char.class) {
            return rawVal.isEmpty() ? '\0' : unescapeHtml(rawVal).charAt(0);
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.parseBoolean(rawVal);
        }
        if (targetType.isEnum()) {
            return parseEnum(targetType, rawVal);
        }

        return rawVal;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object parseEnum(Class<?> targetType, String rawVal) {
        Class<Enum> enumType = (Class<Enum>) targetType;
        try {
            return Enum.valueOf(enumType, rawVal);
        } catch (IllegalArgumentException e1) {
            try {
                return Enum.valueOf(enumType, rawVal.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e2) {
                for (Enum constant : enumType.getEnumConstants()) {
                    if (constant.name().equalsIgnoreCase(rawVal)) {
                        return constant;
                    }
                }
                throw e1;
            }
        }
    }
}

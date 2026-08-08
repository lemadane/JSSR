package com.jssr.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
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
     * List of HTML attribute names that strictly require a SafeUrl, SafeSrcSet, or SafeUrlList value type.
     * Includes all URL-bearing HTML attributes (href, src, action, formaction, poster, data, srcset, imagesrcset, ping, etc.).
     */
    Set<String> URL_ATTRIBUTES = Set.of(
        "href", "src", "action", "formaction", "poster", "xlink:href",
        "data", "srcset", "imagesrcset", "ping", "codebase", "icon", "manifest", "profile", "cite", "longdesc", "usemap"
    );

    /**
     * Internal reflection metadata cache to optimize render performance across renders.
     */
    ClassValue<ComponentMetadata> METADATA_CACHE = new ClassValue<>() {
        @Override
        protected ComponentMetadata computeValue(Class<?> type) {
            return new ComponentMetadata(type);
        }
    };

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

            Map<String, Object> localScope = Collections.emptyMap();
            String controlFlowProcessed = processControlFlow(this, localScope, rawHtml);
            String interpolatedHtml = interpolateVariables(this, localScope, controlFlowProcessed);
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
     * Interpolate ${fieldName} placeholders in HTML templates using a context-aware state machine scanner.
     * Robustly tracks tag attribute names across whitespace around '=' and enforces strict XSS context rules.
     *
     * @param component The component instance
     * @param html HTML template string containing ${fieldName} placeholders
     * @return HTML string with interpolated variable values
     */
    static String interpolateVariables(JssrComponent component, String html) {
        return interpolateVariables(component, Collections.emptyMap(), html);
    }

    static String interpolateVariables(JssrComponent component, Map<String, Object> localScope, String html) {
        if (component == null || html == null || html.isBlank() || !html.contains("${")) {
            return html == null ? "" : html;
        }

        Class<?> clazz = component.getClass();
        if (!clazz.isRecord() && (localScope == null || localScope.isEmpty())) {
            return html;
        }

        StringBuilder sb = new StringBuilder(html.length() + 32);
        int len = html.length();
        int i = 0;

        boolean inTag = false;
        char quoteChar = 0;
        String blockContext = null; // "script", "style", "comment"
        String currentAttr = "";
        String pendingAttr = "";
        boolean seenEquals = false;
        StringBuilder attrBuf = new StringBuilder();

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

                    PropertyResult propRes = resolveProperty(component, localScope, varName);
                    if (!propRes.found()) {
                        throw new IllegalArgumentException("Unknown JSSR interpolation property '${" + varName 
                                + "}' in component " + clazz.getSimpleName());
                    }

                    Object val = propRes.value();
                    Class<?> valType = propRes.type();

                    if (inTag) {
                        String activeAttr = currentAttr;
                        if (activeAttr.isEmpty() && seenEquals && !pendingAttr.isEmpty()) {
                            activeAttr = pendingAttr;
                        }

                        if (val instanceof RawHtml) {
                            throw new IllegalArgumentException("RawHtml cannot be interpolated inside an HTML attribute. Use safe string values, SafeUrl, BooleanAttribute, or HtmlAttribute.");
                        }

                        if (quoteChar == 0 && activeAttr.isEmpty()) {
                            // Free-standing attribute position inside tag, e.g. <input ${extra} />
                            if (val instanceof BooleanAttribute ba) {
                                sb.append(ba.template());
                            } else if (val instanceof HtmlAttribute ha) {
                                sb.append(ha.template());
                            } else if (valType == boolean.class || valType == Boolean.class || val instanceof Boolean) {
                                boolean boolVal = val != null && (Boolean) val;
                                String attrName = varName.contains(".") ? varName.substring(varName.lastIndexOf('.') + 1) : varName;
                                sb.append(boolVal ? attrName : "");
                            } else {
                                throw new IllegalArgumentException("JSSR interpolation ${" + varName 
                                        + "} of type " + (valType != null ? valType.getSimpleName() : "unknown") 
                                        + " in free-standing HTML attribute position is forbidden. Use boolean fields, BooleanAttribute, or HtmlAttribute for dynamic attributes.");
                            }
                            i = end + 1;
                            continue;
                        } else if (quoteChar == 0 && !activeAttr.isEmpty()) {
                            // Unquoted attribute value position, e.g. title=${title} or title = ${title}
                            throw new IllegalArgumentException("JSSR interpolation in an unquoted HTML attribute is forbidden. Quote the attribute value: " 
                                    + activeAttr + "=\"${" + varName + "}\"");
                        } else {
                            // Quoted attribute value position, e.g. title="${title}" or href = "${title}"
                            String lowerAttr = activeAttr.toLowerCase(Locale.ROOT);

                            if ("srcdoc".equals(lowerAttr)) {
                                throw new IllegalArgumentException("JSSR interpolation ${" + varName 
                                        + "} inside 'srcdoc' attribute is forbidden due to HTML nested decoding risks.");
                            }

                            if (lowerAttr.startsWith("x-") || lowerAttr.startsWith("@") || lowerAttr.startsWith(":") || lowerAttr.startsWith("hx-on")) {
                                throw new IllegalArgumentException("JSSR interpolation ${" + varName 
                                        + "} is not allowed inside executable framework attribute '" + activeAttr 
                                        + "'. Use safe server-side state or explicit expression APIs.");
                            }

                            if (lowerAttr.startsWith("on")) {
                                throw new IllegalArgumentException("JSSR interpolation ${" + varName 
                                        + "} is not allowed inside inline event handler attribute '" + activeAttr 
                                        + "'. Use HTMX/Alpine.js attributes or unobtrusive event listeners.");
                            }

                            if ("style".equals(lowerAttr)) {
                                throw new IllegalArgumentException("JSSR interpolation ${" + varName 
                                        + "} is not allowed inside inline style attribute 'style'. Use CSS custom properties or external stylesheets.");
                            }

                            if ("srcset".equals(lowerAttr) || "imagesrcset".equals(lowerAttr)) {
                                if (!(val instanceof SafeSrcSet) && valType != SafeSrcSet.class) {
                                    throw new IllegalArgumentException("JSSR interpolation ${" + varName 
                                            + "} inside multi-candidate image attribute '" + activeAttr + "' requires a SafeSrcSet field type instead of " 
                                            + (valType != null ? valType.getSimpleName() : "String") + ".");
                                }
                            } else if ("ping".equals(lowerAttr)) {
                                if (!(val instanceof SafeUrlList) && !(val instanceof SafeUrl) && valType != SafeUrlList.class && valType != SafeUrl.class) {
                                    throw new IllegalArgumentException("JSSR interpolation ${" + varName 
                                            + "} inside space-separated URL attribute 'ping' requires a SafeUrlList or SafeUrl field type instead of " 
                                            + (valType != null ? valType.getSimpleName() : "String") + ".");
                                }
                            } else if (URL_ATTRIBUTES.contains(lowerAttr)) {
                                if (!(val instanceof SafeUrl) && valType != SafeUrl.class) {
                                    throw new IllegalArgumentException("JSSR interpolation ${" + varName 
                                            + "} inside URL attribute '" + activeAttr + "' requires a SafeUrl field type instead of " 
                                            + (valType != null ? valType.getSimpleName() : "String") + ".");
                                }
                            }

                            String formattedVal;
                            if (val == null) {
                                formattedVal = "";
                            } else if (val instanceof SafeUrl safe) {
                                formattedVal = escapeHtml(safe.render());
                            } else if (val instanceof SafeSrcSet safeSet) {
                                formattedVal = escapeHtml(safeSet.render());
                            } else if (val instanceof SafeUrlList safeList) {
                                formattedVal = escapeHtml(safeList.render());
                            } else if (val instanceof Optional<?> opt) {
                                formattedVal = opt.map(o -> escapeHtml(o.toString())).orElse("");
                            } else {
                                formattedVal = escapeHtml(val.toString());
                            }
                            sb.append(formattedVal);
                            i = end + 1;
                            continue;
                        }
                    } else {
                        // Body text interpolation
                        String valStr;
                        if (val == null) {
                            valStr = "";
                        } else if (val instanceof RawHtml raw) {
                            valStr = raw.value() == null ? "" : raw.value();
                        } else if (val instanceof SafeUrl safe) {
                            valStr = escapeHtml(safe.render());
                        } else if (val instanceof JssrComponent jc) {
                            valStr = jc.render();
                        } else if (val instanceof Optional<?> opt) {
                            valStr = opt.map(o -> escapeHtml(o.toString())).orElse("");
                        } else {
                            valStr = escapeHtml(val.toString());
                        }
                        sb.append(valStr);
                        i = end + 1;
                        continue;
                    }
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
                    } else if (i + 5 < len && html.substring(i, Math.min(i + 7, len)).toLowerCase(Locale.ROOT).startsWith("<style")) {
                        char next = i + 6 < len ? html.charAt(i + 6) : '>';
                        if (next == ' ' || next == '\t' || next == '\n' || next == '\r' || next == '>') {
                            blockContext = "style";
                        }
                    }
                    inTag = true;
                    currentAttr = "";
                    pendingAttr = "";
                    seenEquals = false;
                    attrBuf.setLength(0);
                } else if (inTag) {
                    if (quoteChar != 0) {
                        if (c == quoteChar) {
                            quoteChar = 0;
                            currentAttr = "";
                            pendingAttr = "";
                            seenEquals = false;
                            attrBuf.setLength(0);
                        }
                    } else {
                        if (c == '"' || c == '\'') {
                            quoteChar = c;
                            if (seenEquals && !pendingAttr.isEmpty()) {
                                currentAttr = pendingAttr;
                            } else if (!attrBuf.toString().isBlank()) {
                                currentAttr = attrBuf.toString().trim();
                            }
                            attrBuf.setLength(0);
                        } else if (c == '=') {
                            if (!seenEquals) {
                                if (!attrBuf.toString().isBlank()) {
                                    pendingAttr = attrBuf.toString().trim();
                                    attrBuf.setLength(0);
                                }
                                seenEquals = true;
                            }
                        } else if (c == '>') {
                            inTag = false;
                            quoteChar = 0;
                            currentAttr = "";
                            pendingAttr = "";
                            seenEquals = false;
                            attrBuf.setLength(0);
                        } else if (Character.isWhitespace(c)) {
                            if (!seenEquals && !attrBuf.toString().isBlank()) {
                                pendingAttr = attrBuf.toString().trim();
                                attrBuf.setLength(0);
                            }
                        } else {
                            if (!seenEquals && !pendingAttr.isEmpty() && attrBuf.length() == 0) {
                                pendingAttr = "";
                            }
                            attrBuf.append(c);
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
     * Process custom JSX-like child tags inside rendered HTML strings using a context-aware state machine parser.
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
            ComponentMetadata meta = METADATA_CACHE.get(clazz);
            if (meta.isRecord) {
                RecordComponent[] recordComponents = meta.recordComponents;
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
                for (int i = 0; i < recordComponents.length; i++) {
                    RecordComponent rc = recordComponents[i];
                    String rawVal = attrs.get(rc.getName());
                    if (rawVal == null) {
                        if (rc.getType() == Optional.class) {
                            args[i] = Optional.empty();
                        } else if ("children".equals(rc.getName()) || "content".equals(rc.getName())) {
                            if (rc.getType() == RawHtml.class) {
                                args[i] = RawHtml.of("");
                            } else if (rc.getType() == String.class) {
                                args[i] = "";
                            } else {
                                args[i] = null;
                            }
                        } else {
                            throw new IllegalArgumentException("Missing required attribute '" + rc.getName() 
                                    + "' for JSSR component <" + clazz.getSimpleName() + ">");
                        }
                    } else {
                        args[i] = convertStringValue(rawVal, rc.getType(), rc.getGenericType());
                    }
                }

                JssrComponent instance = (JssrComponent) meta.constructor.newInstance(args);
                return instance.render();
            } else {
                JssrComponent instance = (JssrComponent) meta.constructor.newInstance();
                return instance.render();
            }
        } catch (Exception e) {
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException("Error rendering JSSR component tag <" + clazz.getSimpleName() + ">: " + e.getMessage(), e);
        }
    }

    private static Object convertStringValue(String rawVal, Class<?> targetType, Type genericType) {
        if (targetType == Optional.class) {
            if (rawVal == null) {
                return Optional.empty();
            }
            Class<?> innerType = String.class;
            if (genericType instanceof ParameterizedType pt) {
                Type[] actualArgs = pt.getActualTypeArguments();
                if (actualArgs.length > 0 && actualArgs[0] instanceof Class<?> c) {
                    innerType = c;
                }
            }
            Object innerObj = convertStringValue(rawVal, innerType, innerType);
            return Optional.ofNullable(innerObj);
        }
        return convertStringValue(rawVal, targetType);
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

    private static PropertyResult resolveProperty(Object obj, String propertyPath) {
        return resolveProperty(obj, Collections.emptyMap(), propertyPath);
    }

    private static PropertyResult resolveProperty(Object obj, Map<String, Object> localScope, String propertyPath) {
        if (propertyPath == null || propertyPath.isBlank()) {
            return new PropertyResult(null, Object.class, false);
        }
        String trimmedPath = propertyPath.trim();

        if (localScope != null && localScope.containsKey(trimmedPath)) {
            Object val = localScope.get(trimmedPath);
            return new PropertyResult(val, val == null ? Object.class : val.getClass(), true);
        }

        if (trimmedPath.startsWith("typeof(") && trimmedPath.endsWith(")")) {
            String targetExpr = trimmedPath.substring(7, trimmedPath.length() - 1).trim();
            PropertyResult targetRes = resolveProperty(obj, localScope, targetExpr);
            Object targetVal = targetRes.value();
            if (targetVal instanceof Optional<?> opt) {
                targetVal = opt.orElse(null);
            }
            if (targetVal == null) {
                return new PropertyResult("null", String.class, true);
            }
            return new PropertyResult(targetVal.getClass().getSimpleName(), String.class, true);
        }

        String[] parts = trimmedPath.split("\\.");
        String rootName = parts[0].trim();

        if (localScope != null && localScope.containsKey(rootName)) {
            Object rootVal = localScope.get(rootName);
            if (parts.length == 1) {
                return new PropertyResult(rootVal, rootVal == null ? Object.class : rootVal.getClass(), true);
            }
            String subPath = propertyPath.substring(rootName.length() + 1);
            return resolveProperty(rootVal, Collections.emptyMap(), subPath);
        }

        if (obj == null) {
            return new PropertyResult(null, Object.class, false);
        }
        Object curr = obj;
        Class<?> currType = obj.getClass();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (curr == null) {
                return new PropertyResult(null, Object.class, false);
            }
            ComponentMetadata meta = METADATA_CACHE.get(curr.getClass());
            if (meta != null && meta.isRecord && meta.accessors.containsKey(part)) {
                try {
                    Method m = meta.accessors.get(part);
                    curr = m.invoke(curr);
                    currType = meta.types.get(part);
                } catch (Exception e) {
                    return new PropertyResult(null, Object.class, false);
                }
            } else {
                try {
                    Method m = null;
                    try {
                        m = curr.getClass().getMethod(part);
                    } catch (NoSuchMethodException e1) {
                        String getterName = "get" + Character.toUpperCase(part.charAt(0)) + part.substring(1);
                        m = curr.getClass().getMethod(getterName);
                    }
                    m.setAccessible(true);
                    curr = m.invoke(curr);
                    currType = m.getReturnType();
                } catch (Exception e) {
                    return new PropertyResult(null, Object.class, false);
                }
            }
        }
        return new PropertyResult(curr, currType, true);
    }

    int MAX_WHILE_ITERATIONS = 1000;

    enum LoopSignal { NONE, CONTINUE, BREAK }
    record ControlFlowResult(String content, LoopSignal signal) {}

    /**
     * Parse control flow directives (@if, @for, @while, @continue, @break) in component templates.
     */
    static String processControlFlow(JssrComponent component, String template) {
        return processControlFlow(component, Collections.emptyMap(), template);
    }

    static String processControlFlow(JssrComponent component, Map<String, Object> localScope, String template) {
        if (component == null || template == null || template.isBlank() 
                || (!template.contains("@if") && !template.contains("@for") && !template.contains("@while") 
                    && !template.contains("@switch") && !template.contains("@try") && !template.contains("@throw")
                    && !template.contains("@continue") && !template.contains("@break"))) {
            return template == null ? "" : template;
        }
        return parseControlFlowBlocks(component, localScope, template).content();
    }

    private static ControlFlowResult parseControlFlowBlocks(JssrComponent component, Map<String, Object> localScope, String text) {
        if (text == null || text.isEmpty() 
                || (!text.contains("@if") && !text.contains("@for") && !text.contains("@while") 
                    && !text.contains("@switch") && !text.contains("@try") && !text.contains("@throw")
                    && !text.contains("@continue") && !text.contains("@break"))) {
            return new ControlFlowResult(text == null ? "" : text, LoopSignal.NONE);
        }

        StringBuilder result = new StringBuilder();
        int len = text.length();
        int curr = 0;

        while (curr < len) {
            int directiveIdx = findNextControlFlowDirective(text, curr);
            if (directiveIdx == -1) {
                result.append(text, curr, len);
                break;
            }

            result.append(text, curr, directiveIdx);

            if (text.startsWith("@continue", directiveIdx) && isValidDirectiveBoundary(text, directiveIdx, 9)) {
                return new ControlFlowResult(result.toString(), LoopSignal.CONTINUE);
            } else if (text.startsWith("@break", directiveIdx) && isValidDirectiveBoundary(text, directiveIdx, 6)) {
                return new ControlFlowResult(result.toString(), LoopSignal.BREAK);
            } else if (text.startsWith("@throw", directiveIdx) && isValidDirectiveBoundary(text, directiveIdx, 6)) {
                int openParen = text.indexOf('(', directiveIdx + 6);
                int closeParen = (openParen != -1) ? findMatchingParen(text, openParen) : -1;
                String throwExpr = (openParen != -1 && closeParen != -1) ? text.substring(openParen + 1, closeParen).trim() : "";
                int endIndex = (closeParen != -1) ? closeParen + 1 : directiveIdx + 6;
                if (endIndex < len && text.charAt(endIndex) == ':') {
                    endIndex++;
                }
                executeThrowDirective(component, localScope, throwExpr);
                curr = endIndex;
            } else if (text.startsWith("@if", directiveIdx) && isValidDirectiveBoundary(text, directiveIdx, 3)) {
                IfBlockResult blockResult = parseIfBlockAt(component, text, directiveIdx);
                ControlFlowResult evalRes = evaluateIfBlockResult(component, localScope, blockResult);
                result.append(evalRes.content());
                if (evalRes.signal() != LoopSignal.NONE) {
                    return new ControlFlowResult(result.toString(), evalRes.signal());
                }
                curr = blockResult.endIndex;
            } else if (text.startsWith("@try", directiveIdx) && isValidDirectiveBoundary(text, directiveIdx, 4)) {
                TryBlockResult blockResult = parseTryBlockAt(component, text, directiveIdx);
                ControlFlowResult evalRes = evaluateTryBlockResult(component, localScope, blockResult);
                result.append(evalRes.content());
                if (evalRes.signal() != LoopSignal.NONE) {
                    return new ControlFlowResult(result.toString(), evalRes.signal());
                }
                curr = blockResult.endIndex;
            } else if (text.startsWith("@for", directiveIdx) && isValidDirectiveBoundary(text, directiveIdx, 4)) {
                ForBlockResult blockResult = parseForBlockAt(component, text, directiveIdx);
                String renderedFor = evaluateForBlockResult(component, localScope, blockResult);
                result.append(renderedFor);
                curr = blockResult.endIndex;
            } else if (text.startsWith("@while", directiveIdx) && isValidDirectiveBoundary(text, directiveIdx, 6)) {
                WhileBlockResult blockResult = parseWhileBlockAt(component, text, directiveIdx);
                String renderedWhile = evaluateWhileBlockResult(component, localScope, blockResult);
                result.append(renderedWhile);
                curr = blockResult.endIndex;
            } else if (text.startsWith("@switch", directiveIdx) && isValidDirectiveBoundary(text, directiveIdx, 7)) {
                SwitchBlockResult blockResult = parseSwitchBlockAt(component, text, directiveIdx);
                ControlFlowResult evalRes = evaluateSwitchBlockResult(component, localScope, blockResult);
                result.append(evalRes.content());
                if (evalRes.signal() != LoopSignal.NONE) {
                    return new ControlFlowResult(result.toString(), evalRes.signal());
                }
                curr = blockResult.endIndex;
            } else {
                curr = directiveIdx + 1;
            }
        }

        return new ControlFlowResult(result.toString(), LoopSignal.NONE);
    }

    record IfBlockResult(List<Branch> branches, int endIndex) {}
    record Branch(String conditionExpr, boolean isElse, String body) {}

    private static IfBlockResult parseIfBlockAt(JssrComponent component, String text, int startIdx) {
        int len = text.length();
        List<Branch> branches = new ArrayList<>();

        int condOpen = text.indexOf('(', startIdx + 3);
        int condClose = findMatchingParen(text, condOpen);
        if (condOpen == -1 || condClose == -1) {
            throw new IllegalArgumentException("Malformed '@if' condition in directive starting at index " + startIdx 
                    + " in component " + component.getClass().getSimpleName());
        }
        String firstCond = text.substring(condOpen + 1, condClose).trim();

        int curr = condClose + 1;
        int bodyStart = curr;

        String currentCond = firstCond;
        boolean currentIsElse = false;
        int nestedDepth = 0;

        while (curr < len) {
            if (curr + 4 <= len && text.startsWith("<!--", curr)) {
                int commentEnd = text.indexOf("-->", curr + 4);
                if (commentEnd != -1) {
                    curr = commentEnd + 3;
                    continue;
                }
            }
            if (curr + 7 <= len && text.substring(curr, Math.min(curr + 8, len)).toLowerCase(Locale.ROOT).startsWith("<script")) {
                int scriptEnd = text.toLowerCase(Locale.ROOT).indexOf("</script>", curr);
                if (scriptEnd != -1) {
                    curr = scriptEnd + 9;
                    continue;
                }
            }
            if (curr + 6 <= len && text.substring(curr, Math.min(curr + 7, len)).toLowerCase(Locale.ROOT).startsWith("<style")) {
                int styleEnd = text.toLowerCase(Locale.ROOT).indexOf("</style>", curr);
                if (styleEnd != -1) {
                    curr = styleEnd + 8;
                    continue;
                }
            }

            if (text.startsWith("@try", curr) && isValidDirectiveBoundary(text, curr, 4)) {
                nestedDepth++;
                curr += 4;
            } else if (text.startsWith("@switch", curr) && isValidDirectiveBoundary(text, curr, 7)) {
                nestedDepth++;
                curr += 7;
            } else if (text.startsWith("@while", curr) && isValidDirectiveBoundary(text, curr, 6)) {
                nestedDepth++;
                curr += 6;
            } else if (text.startsWith("@for", curr) && isValidDirectiveBoundary(text, curr, 4)) {
                nestedDepth++;
                curr += 4;
            } else if (text.startsWith("@if", curr) && isValidDirectiveBoundary(text, curr, 3)) {
                nestedDepth++;
                curr += 3;
            } else if (text.startsWith("@end", curr) && isValidDirectiveBoundary(text, curr, 4)) {
                if (nestedDepth == 0) {
                    String body = text.substring(bodyStart, curr);
                    branches.add(new Branch(currentCond, currentIsElse, body));
                    return new IfBlockResult(branches, curr + 4);
                } else {
                    nestedDepth--;
                    curr += 4;
                }
            } else if (nestedDepth == 0 && text.startsWith("@elseif", curr) && isValidDirectiveBoundary(text, curr, 7)) {
                String body = text.substring(bodyStart, curr);
                branches.add(new Branch(currentCond, currentIsElse, body));

                int nextCondOpen = text.indexOf('(', curr + 7);
                int nextCondClose = findMatchingParen(text, nextCondOpen);
                if (nextCondOpen == -1 || nextCondClose == -1) {
                    throw new IllegalArgumentException("Malformed '@elseif' condition near index " + curr 
                            + " in component " + component.getClass().getSimpleName());
                }
                currentCond = text.substring(nextCondOpen + 1, nextCondClose).trim();
                currentIsElse = false;
                curr = nextCondClose + 1;
                bodyStart = curr;
            } else if (nestedDepth == 0 && text.startsWith("@else", curr) && isValidDirectiveBoundary(text, curr, 5)) {
                String body = text.substring(bodyStart, curr);
                branches.add(new Branch(currentCond, currentIsElse, body));

                currentCond = null;
                currentIsElse = true;
                curr += 5;
                bodyStart = curr;
            } else {
                curr++;
            }
        }

        throw new IllegalArgumentException("Unclosed JSSR control flow directive '@if' in component " 
                + component.getClass().getSimpleName() + ". Expected matching '@end'.");
    }

    record ForBlockResult(String loopVar, String listExpr, String loopBody, String elseBody, boolean hasElse, int endIndex) {}

    private static ForBlockResult parseForBlockAt(JssrComponent component, String text, int startIdx) {
        int len = text.length();
        int condOpen = text.indexOf('(', startIdx + 4);
        int condClose = findMatchingParen(text, condOpen);
        if (condOpen == -1 || condClose == -1) {
            throw new IllegalArgumentException("Malformed '@for' condition in directive starting at index " + startIdx 
                    + " in component " + component.getClass().getSimpleName());
        }

        String header = text.substring(condOpen + 1, condClose).trim();
        String[] parts = header.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Malformed '@for' header '" + header + "' (expected format: 'item : collection') in component " 
                    + component.getClass().getSimpleName());
        }

        String loopVar = parts[0].trim();
        String listExpr = parts[1].trim();

        int curr = condClose + 1;
        int loopBodyStart = curr;
        String loopBody = "";
        String elseBody = "";
        boolean hasElse = false;

        int nestedDepth = 0;

        while (curr < len) {
            if (curr + 4 <= len && text.startsWith("<!--", curr)) {
                int commentEnd = text.indexOf("-->", curr + 4);
                if (commentEnd != -1) {
                    curr = commentEnd + 3;
                    continue;
                }
            }
            if (curr + 7 <= len && text.substring(curr, Math.min(curr + 8, len)).toLowerCase(Locale.ROOT).startsWith("<script")) {
                int scriptEnd = text.toLowerCase(Locale.ROOT).indexOf("</script>", curr);
                if (scriptEnd != -1) {
                    curr = scriptEnd + 9;
                    continue;
                }
            }
            if (curr + 6 <= len && text.substring(curr, Math.min(curr + 7, len)).toLowerCase(Locale.ROOT).startsWith("<style")) {
                int styleEnd = text.toLowerCase(Locale.ROOT).indexOf("</style>", curr);
                if (styleEnd != -1) {
                    curr = styleEnd + 8;
                    continue;
                }
            }

            if (text.startsWith("@try", curr) && isValidDirectiveBoundary(text, curr, 4)) {
                nestedDepth++;
                curr += 4;
            } else if (text.startsWith("@switch", curr) && isValidDirectiveBoundary(text, curr, 7)) {
                nestedDepth++;
                curr += 7;
            } else if (text.startsWith("@while", curr) && isValidDirectiveBoundary(text, curr, 6)) {
                nestedDepth++;
                curr += 6;
            } else if (text.startsWith("@for", curr) && isValidDirectiveBoundary(text, curr, 4)) {
                nestedDepth++;
                curr += 4;
            } else if (text.startsWith("@if", curr) && isValidDirectiveBoundary(text, curr, 3)) {
                nestedDepth++;
                curr += 3;
            } else if (text.startsWith("@end", curr) && isValidDirectiveBoundary(text, curr, 4)) {
                if (nestedDepth > 0) {
                    nestedDepth--;
                    curr += 4;
                } else {
                    if (hasElse) {
                        elseBody = text.substring(loopBodyStart, curr);
                    } else {
                        loopBody = text.substring(loopBodyStart, curr);
                    }
                    return new ForBlockResult(loopVar, listExpr, loopBody, elseBody, hasElse, curr + 4);
                }
            } else if (nestedDepth == 0 && text.startsWith("@else", curr) && isValidDirectiveBoundary(text, curr, 5)) {
                loopBody = text.substring(loopBodyStart, curr);
                hasElse = true;
                curr += 5;
                loopBodyStart = curr;
            } else {
                curr++;
            }
        }

        throw new IllegalArgumentException("Unclosed JSSR control flow directive '@for' in component " 
                + component.getClass().getSimpleName() + ". Expected matching '@end'.");
    }

    record WhileBlockResult(String conditionExpr, String loopBody, int endIndex) {}

    private static WhileBlockResult parseWhileBlockAt(JssrComponent component, String text, int startIdx) {
        int len = text.length();
        int condOpen = text.indexOf('(', startIdx + 6);
        int condClose = findMatchingParen(text, condOpen);
        if (condOpen == -1 || condClose == -1) {
            throw new IllegalArgumentException("Malformed '@while' condition in directive starting at index " + startIdx 
                    + " in component " + component.getClass().getSimpleName());
        }

        String conditionExpr = text.substring(condOpen + 1, condClose).trim();

        int curr = condClose + 1;
        int loopBodyStart = curr;

        int nestedDepth = 0;

        while (curr < len) {
            if (curr + 4 <= len && text.startsWith("<!--", curr)) {
                int commentEnd = text.indexOf("-->", curr + 4);
                if (commentEnd != -1) {
                    curr = commentEnd + 3;
                    continue;
                }
            }
            if (curr + 7 <= len && text.substring(curr, Math.min(curr + 8, len)).toLowerCase(Locale.ROOT).startsWith("<script")) {
                int scriptEnd = text.toLowerCase(Locale.ROOT).indexOf("</script>", curr);
                if (scriptEnd != -1) {
                    curr = scriptEnd + 9;
                    continue;
                }
            }
            if (curr + 6 <= len && text.substring(curr, Math.min(curr + 7, len)).toLowerCase(Locale.ROOT).startsWith("<style")) {
                int styleEnd = text.toLowerCase(Locale.ROOT).indexOf("</style>", curr);
                if (styleEnd != -1) {
                    curr = styleEnd + 8;
                    continue;
                }
            }

            if (text.startsWith("@try", curr) && isValidDirectiveBoundary(text, curr, 4)) {
                nestedDepth++;
                curr += 4;
            } else if (text.startsWith("@switch", curr) && isValidDirectiveBoundary(text, curr, 7)) {
                nestedDepth++;
                curr += 7;
            } else if (text.startsWith("@while", curr) && isValidDirectiveBoundary(text, curr, 6)) {
                nestedDepth++;
                curr += 6;
            } else if (text.startsWith("@for", curr) && isValidDirectiveBoundary(text, curr, 4)) {
                nestedDepth++;
                curr += 4;
            } else if (text.startsWith("@if", curr) && isValidDirectiveBoundary(text, curr, 3)) {
                nestedDepth++;
                curr += 3;
            } else if (text.startsWith("@end", curr) && isValidDirectiveBoundary(text, curr, 4)) {
                if (nestedDepth > 0) {
                    nestedDepth--;
                    curr += 4;
                } else {
                    String loopBody = text.substring(loopBodyStart, curr);
                    return new WhileBlockResult(conditionExpr, loopBody, curr + 4);
                }
            } else {
                curr++;
            }
        }

        throw new IllegalArgumentException("Unclosed JSSR control flow directive '@while' in component " 
                + component.getClass().getSimpleName() + ". Expected matching '@end'.");
    }

    private static int findNextControlFlowDirective(String text, int fromIdx) {
        int len = text.length();
        int curr = fromIdx;
        while (curr < len) {
            if (curr + 4 <= len && text.startsWith("<!--", curr)) {
                int commentEnd = text.indexOf("-->", curr + 4);
                if (commentEnd != -1) {
                    curr = commentEnd + 3;
                    continue;
                }
            }
            if (curr + 7 <= len && text.substring(curr, Math.min(curr + 8, len)).toLowerCase(Locale.ROOT).startsWith("<script")) {
                int scriptEnd = text.toLowerCase(Locale.ROOT).indexOf("</script>", curr);
                if (scriptEnd != -1) {
                    curr = scriptEnd + 9;
                    continue;
                }
            }
            if (curr + 6 <= len && text.substring(curr, Math.min(curr + 7, len)).toLowerCase(Locale.ROOT).startsWith("<style")) {
                int styleEnd = text.toLowerCase(Locale.ROOT).indexOf("</style>", curr);
                if (styleEnd != -1) {
                    curr = styleEnd + 8;
                    continue;
                }
            }

            if ((text.startsWith("@if", curr) && isValidDirectiveBoundary(text, curr, 3))
                    || (text.startsWith("@for", curr) && isValidDirectiveBoundary(text, curr, 4))
                    || (text.startsWith("@while", curr) && isValidDirectiveBoundary(text, curr, 6))
                    || (text.startsWith("@switch", curr) && isValidDirectiveBoundary(text, curr, 7))
                    || (text.startsWith("@case", curr) && isValidDirectiveBoundary(text, curr, 5))
                    || (text.startsWith("@default", curr) && isValidDirectiveBoundary(text, curr, 8))
                    || (text.startsWith("@try", curr) && isValidDirectiveBoundary(text, curr, 4))
                    || (text.startsWith("@catch", curr) && isValidDirectiveBoundary(text, curr, 6))
                    || (text.startsWith("@finally", curr) && isValidDirectiveBoundary(text, curr, 8))
                    || (text.startsWith("@throw", curr) && isValidDirectiveBoundary(text, curr, 6))
                    || (text.startsWith("@continue", curr) && isValidDirectiveBoundary(text, curr, 9))
                    || (text.startsWith("@break", curr) && isValidDirectiveBoundary(text, curr, 6))) {
                return curr;
            }
            curr++;
        }
        return -1;
    }

    private static void executeThrowDirective(JssrComponent component, Map<String, Object> localScope, String throwExpr) {
        if (throwExpr == null || throwExpr.isBlank()) {
            throw new RuntimeException("Template error triggered via @throw in component " + component.getClass().getSimpleName());
        }

        String trimmed = throwExpr.trim();

        // 1. Instantiation syntax: @throw(new ExceptionClass("message"))
        if (trimmed.startsWith("new ")) {
            int openParen = trimmed.indexOf('(');
            int closeParen = trimmed.lastIndexOf(')');
            if (openParen != -1 && closeParen > openParen) {
                String className = trimmed.substring(4, openParen).trim();
                String argStr = trimmed.substring(openParen + 1, closeParen).trim();
                Object argVal = null;
                if ((argStr.startsWith("\"") && argStr.endsWith("\"")) || (argStr.startsWith("'") && argStr.endsWith("'"))) {
                    argVal = argStr.substring(1, argStr.length() - 1);
                } else if (!argStr.isBlank()) {
                    PropertyResult res = resolveProperty(component, localScope, argStr);
                    if (res.found()) {
                        argVal = res.value();
                    } else {
                        argVal = argStr;
                    }
                }

                Throwable instantiated = instantiateException(className, argVal);
                if (instantiated != null) {
                    if (instantiated instanceof RuntimeException re) {
                        throw re;
                    } else if (instantiated instanceof Error err) {
                        throw err;
                    } else {
                        throw new RuntimeException(instantiated.getMessage(), instantiated);
                    }
                }
            }
        }

        // 2. String literal syntax: @throw("error message")
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            String msg = trimmed.substring(1, trimmed.length() - 1);
            throw new RuntimeException(msg);
        }

        // 3. Property / Variable reference syntax: @throw(exVar)
        PropertyResult propRes = resolveProperty(component, localScope, trimmed);
        if (propRes.found()) {
            Object val = propRes.value();
            if (val instanceof Throwable t) {
                if (t instanceof RuntimeException re) {
                    throw re;
                } else if (t instanceof Error err) {
                    throw err;
                }
                throw new RuntimeException(t.getMessage(), t);
            }
            if (val != null) {
                throw new RuntimeException(val.toString());
            }
        }

        throw new RuntimeException(trimmed);
    }

    private static Throwable instantiateException(String className, Object arg) {
        Class<?> clazz = resolveExceptionClass(className);
        if (clazz == null || !Throwable.class.isAssignableFrom(clazz)) {
            return null;
        }

        try {
            if (arg != null) {
                try {
                    Constructor<?> ctor = clazz.getConstructor(arg.getClass());
                    return (Throwable) ctor.newInstance(arg);
                } catch (NoSuchMethodException ignored) {}

                if (arg instanceof String) {
                    try {
                        Constructor<?> ctor = clazz.getConstructor(String.class);
                        return (Throwable) ctor.newInstance(arg);
                    } catch (NoSuchMethodException ignored) {}
                }
            }

            try {
                Constructor<?> ctor = clazz.getConstructor();
                return (Throwable) ctor.newInstance();
            } catch (NoSuchMethodException ignored) {}
        } catch (Throwable ignored) {}

        return null;
    }

    private static Class<?> resolveExceptionClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ignored) {}

        if (!className.contains(".")) {
            try {
                return Class.forName("java.lang." + className);
            } catch (ClassNotFoundException ignored) {}
            try {
                return Class.forName("java.io." + className);
            } catch (ClassNotFoundException ignored) {}
            try {
                return Class.forName("java.util." + className);
            } catch (ClassNotFoundException ignored) {}
        }
        return null;
    }

    record TryBlockResult(String tryBody, String catchVar, String catchBody, boolean hasCatch, String finallyBody, boolean hasFinally, int endIndex) {}

    private static TryBlockResult parseTryBlockAt(JssrComponent component, String text, int startIdx) {
        int len = text.length();
        int curr = startIdx + 4; // Skip "@try"
        if (curr < len && text.charAt(curr) == ':') {
            curr++;
        }
        int currentBodyStart = curr;
        String tryBody = "";
        String catchVar = null;
        String catchBody = "";
        boolean hasCatch = false;
        String finallyBody = "";
        boolean hasFinally = false;
        int currentSection = 0; // 0 = try, 1 = catch, 2 = finally
        int nestedDepth = 0;

        while (curr < len) {
            if (curr + 4 <= len && text.startsWith("<!--", curr)) {
                int commentEnd = text.indexOf("-->", curr + 4);
                if (commentEnd != -1) {
                    curr = commentEnd + 3;
                    continue;
                }
            }
            if (curr + 7 <= len && text.substring(curr, Math.min(curr + 8, len)).toLowerCase(Locale.ROOT).startsWith("<script")) {
                int scriptEnd = text.toLowerCase(Locale.ROOT).indexOf("</script>", curr);
                if (scriptEnd != -1) {
                    curr = scriptEnd + 9;
                    continue;
                }
            }
            if (curr + 6 <= len && text.substring(curr, Math.min(curr + 7, len)).toLowerCase(Locale.ROOT).startsWith("<style")) {
                int styleEnd = text.toLowerCase(Locale.ROOT).indexOf("</style>", curr);
                if (styleEnd != -1) {
                    curr = styleEnd + 8;
                    continue;
                }
            }

            if (text.startsWith("@try", curr) && isValidDirectiveBoundary(text, curr, 4)) {
                nestedDepth++;
                curr += 4;
                if (curr < len && text.charAt(curr) == ':') curr++;
            } else if (text.startsWith("@switch", curr) && isValidDirectiveBoundary(text, curr, 7)) {
                nestedDepth++;
                curr += 7;
            } else if (text.startsWith("@while", curr) && isValidDirectiveBoundary(text, curr, 6)) {
                nestedDepth++;
                curr += 6;
            } else if (text.startsWith("@for", curr) && isValidDirectiveBoundary(text, curr, 4)) {
                nestedDepth++;
                curr += 4;
            } else if (text.startsWith("@if", curr) && isValidDirectiveBoundary(text, curr, 3)) {
                nestedDepth++;
                curr += 3;
            } else if (text.startsWith("@end", curr) && isValidDirectiveBoundary(text, curr, 4)) {
                if (nestedDepth > 0) {
                    nestedDepth--;
                    curr += 4;
                } else {
                    if (currentSection == 0) {
                        tryBody = text.substring(currentBodyStart, curr);
                    } else if (currentSection == 1) {
                        catchBody = text.substring(currentBodyStart, curr);
                    } else if (currentSection == 2) {
                        finallyBody = text.substring(currentBodyStart, curr);
                    }
                    return new TryBlockResult(tryBody, catchVar, catchBody, hasCatch, finallyBody, hasFinally, curr + 4);
                }
            } else if (nestedDepth == 0 && text.startsWith("@catch", curr) && isValidDirectiveBoundary(text, curr, 6)) {
                tryBody = text.substring(currentBodyStart, curr);
                hasCatch = true;
                currentSection = 1;
                curr += 6;

                if (curr < len && text.charAt(curr) == '(') {
                    int parenClose = findMatchingParen(text, curr);
                    if (parenClose != -1) {
                        catchVar = text.substring(curr + 1, parenClose).trim();
                        curr = parenClose + 1;
                    }
                }
                if (curr < len && text.charAt(curr) == ':') {
                    curr++;
                }
                currentBodyStart = curr;
            } else if (nestedDepth == 0 && text.startsWith("@finally", curr) && isValidDirectiveBoundary(text, curr, 8)) {
                if (currentSection == 0) {
                    tryBody = text.substring(currentBodyStart, curr);
                } else if (currentSection == 1) {
                    catchBody = text.substring(currentBodyStart, curr);
                }
                hasFinally = true;
                currentSection = 2;
                curr += 8;
                if (curr < len && text.charAt(curr) == ':') {
                    curr++;
                }
                currentBodyStart = curr;
            } else {
                curr++;
            }
        }

        throw new IllegalArgumentException("Unclosed JSSR control flow directive '@try' in component " 
                + component.getClass().getSimpleName() + ". Expected matching '@end'.");
    }

    private static ControlFlowResult evaluateTryBlockResult(JssrComponent component, Map<String, Object> localScope, TryBlockResult tryResult) {
        StringBuilder sb = new StringBuilder();
        try {
            ControlFlowResult flowRes = parseControlFlowBlocks(component, localScope, tryResult.tryBody());
            String interpolated = interpolateVariables(component, localScope, flowRes.content());
            sb.append(interpolated);
        } catch (Exception e) {
            if (tryResult.hasCatch()) {
                Map<String, Object> catchScope = new HashMap<>(localScope);
                String varName = (tryResult.catchVar() == null || tryResult.catchVar().isBlank()) ? "err" : tryResult.catchVar();
                String rawMsg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                String safeMsg = rawMsg.replace("${", "&#36;{");

                catchScope.put(varName, e);
                catchScope.put(varName + ".message", safeMsg);
                catchScope.put("e", e);
                catchScope.put("e.message", safeMsg);
                catchScope.put("error", e);
                catchScope.put("error.message", safeMsg);

                ControlFlowResult flowRes = parseControlFlowBlocks(component, catchScope, tryResult.catchBody());
                String interpolated = interpolateVariables(component, catchScope, flowRes.content());
                sb.append(interpolated);
            }
        } finally {
            if (tryResult.hasFinally()) {
                ControlFlowResult flowRes = parseControlFlowBlocks(component, localScope, tryResult.finallyBody());
                String interpolated = interpolateVariables(component, localScope, flowRes.content());
                sb.append(interpolated);
            }
        }
        return new ControlFlowResult(sb.toString(), LoopSignal.NONE);
    }

    record SwitchBlockResult(String switchExpr, List<SwitchCase> cases, SwitchCase defaultCase, boolean hasDefault, int endIndex) {}
    record SwitchCase(String caseExpr, String body, boolean isDefault) {}

    private static SwitchBlockResult parseSwitchBlockAt(JssrComponent component, String text, int startIdx) {
        int len = text.length();
        int condOpen = text.indexOf('(', startIdx + 7);
        int condClose = findMatchingParen(text, condOpen);
        if (condOpen == -1 || condClose == -1) {
            throw new IllegalArgumentException("Malformed '@switch' condition in directive starting at index " + startIdx 
                    + " in component " + component.getClass().getSimpleName());
        }

        String switchExpr = text.substring(condOpen + 1, condClose).trim();

        int curr = condClose + 1;
        List<SwitchCase> cases = new ArrayList<>();
        SwitchCase defaultCase = null;
        boolean hasDefault = false;

        String currentCaseExpr = null;
        boolean currentIsDefault = false;
        int currentBodyStart = -1;
        int nestedDepth = 0;

        while (curr < len) {
            if (curr + 4 <= len && text.startsWith("<!--", curr)) {
                int commentEnd = text.indexOf("-->", curr + 4);
                if (commentEnd != -1) {
                    curr = commentEnd + 3;
                    continue;
                }
            }
            if (curr + 7 <= len && text.substring(curr, Math.min(curr + 8, len)).toLowerCase(Locale.ROOT).startsWith("<script")) {
                int scriptEnd = text.toLowerCase(Locale.ROOT).indexOf("</script>", curr);
                if (scriptEnd != -1) {
                    curr = scriptEnd + 9;
                    continue;
                }
            }
            if (curr + 6 <= len && text.substring(curr, Math.min(curr + 7, len)).toLowerCase(Locale.ROOT).startsWith("<style")) {
                int styleEnd = text.toLowerCase(Locale.ROOT).indexOf("</style>", curr);
                if (styleEnd != -1) {
                    curr = styleEnd + 8;
                    continue;
                }
            }

            if (text.startsWith("@switch", curr) && isValidDirectiveBoundary(text, curr, 7)) {
                nestedDepth++;
                curr += 7;
            } else if (text.startsWith("@while", curr) && isValidDirectiveBoundary(text, curr, 6)) {
                nestedDepth++;
                curr += 6;
            } else if (text.startsWith("@for", curr) && isValidDirectiveBoundary(text, curr, 4)) {
                nestedDepth++;
                curr += 4;
            } else if (text.startsWith("@if", curr) && isValidDirectiveBoundary(text, curr, 3)) {
                nestedDepth++;
                curr += 3;
            } else if (text.startsWith("@end", curr) && isValidDirectiveBoundary(text, curr, 4)) {
                if (nestedDepth == 0) {
                    if (currentBodyStart != -1) {
                        String body = text.substring(currentBodyStart, curr);
                        if (currentIsDefault) {
                            defaultCase = new SwitchCase(null, body, true);
                            hasDefault = true;
                        } else {
                            cases.add(new SwitchCase(currentCaseExpr, body, false));
                        }
                    }
                    return new SwitchBlockResult(switchExpr, cases, defaultCase, hasDefault, curr + 4);
                } else {
                    nestedDepth--;
                    curr += 4;
                }
            } else if (nestedDepth == 0 && text.startsWith("@case", curr) && isValidDirectiveBoundary(text, curr, 5)) {
                if (currentBodyStart != -1) {
                    String body = text.substring(currentBodyStart, curr);
                    if (currentIsDefault) {
                        defaultCase = new SwitchCase(null, body, true);
                        hasDefault = true;
                    } else {
                        cases.add(new SwitchCase(currentCaseExpr, body, false));
                    }
                }

                int caseCondOpen = text.indexOf('(', curr + 5);
                int caseCondClose = findMatchingParen(text, caseCondOpen);
                if (caseCondOpen == -1 || caseCondClose == -1) {
                    throw new IllegalArgumentException("Malformed '@case' condition near index " + curr 
                            + " in component " + component.getClass().getSimpleName());
                }
                currentCaseExpr = text.substring(caseCondOpen + 1, caseCondClose).trim();
                currentIsDefault = false;
                curr = caseCondClose + 1;
                currentBodyStart = curr;
            } else if (nestedDepth == 0 && text.startsWith("@default", curr) && isValidDirectiveBoundary(text, curr, 8)) {
                if (currentBodyStart != -1) {
                    String body = text.substring(currentBodyStart, curr);
                    if (currentIsDefault) {
                        defaultCase = new SwitchCase(null, body, true);
                        hasDefault = true;
                    } else {
                        cases.add(new SwitchCase(currentCaseExpr, body, false));
                    }
                }

                currentCaseExpr = null;
                currentIsDefault = true;
                curr += 8;
                currentBodyStart = curr;
            } else {
                curr++;
            }
        }

        throw new IllegalArgumentException("Unclosed JSSR control flow directive '@switch' in component " 
                + component.getClass().getSimpleName() + ". Expected matching '@end'.");
    }

    private static ControlFlowResult evaluateSwitchBlockResult(JssrComponent component, Map<String, Object> localScope, SwitchBlockResult switchResult) {
        PropertyResult switchRes = resolveProperty(component, localScope, switchResult.switchExpr);
        Object switchVal = switchRes.value();
        String switchValStr = switchVal == null ? "null" : String.valueOf(switchVal);

        for (SwitchCase sc : switchResult.cases) {
            String caseValExpr = sc.caseExpr();
            String caseValClean = cleanCaseLiteral(caseValExpr);

            if (switchValStr.equalsIgnoreCase(caseValClean) || switchValStr.equals(caseValExpr)) {
                ControlFlowResult flowRes = parseControlFlowBlocks(component, localScope, sc.body());
                if (flowRes.signal() == LoopSignal.BREAK) {
                    return new ControlFlowResult(flowRes.content(), LoopSignal.NONE);
                }
                return flowRes;
            }
        }

        if (switchResult.hasDefault()) {
            ControlFlowResult flowRes = parseControlFlowBlocks(component, localScope, switchResult.defaultCase().body());
            if (flowRes.signal() == LoopSignal.BREAK) {
                return new ControlFlowResult(flowRes.content(), LoopSignal.NONE);
            }
            return flowRes;
        }

        return new ControlFlowResult("", LoopSignal.NONE);
    }

    private static String cleanCaseLiteral(String expr) {
        if (expr == null) return "";
        String trimmed = expr.trim();
        if ((trimmed.startsWith("'") && trimmed.endsWith("'")) || (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static ControlFlowResult evaluateIfBlockResult(JssrComponent component, Map<String, Object> localScope, IfBlockResult blockResult) {
        for (Branch b : blockResult.branches) {
            if (b.isElse) {
                ControlFlowResult flowRes = parseControlFlowBlocks(component, localScope, b.body);
                String interpolated = interpolateVariables(component, localScope, flowRes.content());
                return new ControlFlowResult(interpolated, flowRes.signal());
            }
            ConditionResult condRes = evaluateConditionWithBinding(component, localScope, b.conditionExpr);
            if (condRes.matches()) {
                Map<String, Object> branchScope = localScope;
                if (!condRes.bindings().isEmpty()) {
                    branchScope = new HashMap<>(localScope);
                    branchScope.putAll(condRes.bindings());
                }
                ControlFlowResult flowRes = parseControlFlowBlocks(component, branchScope, b.body);
                String interpolated = interpolateVariables(component, branchScope, flowRes.content());
                return new ControlFlowResult(interpolated, flowRes.signal());
            }
        }
        return new ControlFlowResult("", LoopSignal.NONE);
    }

    private static String evaluateForBlockResult(JssrComponent component, Map<String, Object> localScope, ForBlockResult forResult) {
        PropertyResult listRes = resolveProperty(component, localScope, forResult.listExpr);
        List<?> items = toList(listRes.value());

        if (!items.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Object elem : items) {
                Map<String, Object> iterationScope = new HashMap<>(localScope);
                iterationScope.put(forResult.loopVar, elem);

                ControlFlowResult flowRes = parseControlFlowBlocks(component, iterationScope, forResult.loopBody);
                String interpolated = interpolateVariables(component, iterationScope, flowRes.content());
                sb.append(interpolated);

                if (flowRes.signal() == LoopSignal.BREAK) {
                    break;
                }
            }
            return sb.toString();
        } else {
            if (forResult.hasElse) {
                ControlFlowResult flowRes = parseControlFlowBlocks(component, localScope, forResult.elseBody);
                return interpolateVariables(component, localScope, flowRes.content());
            } else {
                return "";
            }
        }
    }

    private static String evaluateWhileBlockResult(JssrComponent component, Map<String, Object> localScope, WhileBlockResult whileResult) {
        StringBuilder sb = new StringBuilder();
        int iteration = 0;

        while (evaluateCondition(component, localScope, whileResult.conditionExpr)) {
            iteration++;
            if (iteration > MAX_WHILE_ITERATIONS) {
                throw new IllegalStateException("JSSR @while loop iteration limit exceeded (max " + MAX_WHILE_ITERATIONS 
                        + " iterations) for condition '" + whileResult.conditionExpr + "' in component " + component.getClass().getSimpleName());
            }

            ControlFlowResult flowRes = parseControlFlowBlocks(component, localScope, whileResult.loopBody);
            String interpolated = interpolateVariables(component, localScope, flowRes.content());
            sb.append(interpolated);

            if (flowRes.signal() == LoopSignal.BREAK) {
                break;
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<?> toList(Object obj) {
        if (obj instanceof Optional<?> opt) {
            obj = opt.orElse(null);
        }
        if (obj == null) {
            return Collections.emptyList();
        }
        if (obj instanceof Collection<?> c) {
            return new ArrayList<>(c);
        }
        if (obj instanceof Iterable<?> it) {
            List<Object> list = new ArrayList<>();
            for (Object o : it) {
                list.add(o);
            }
            return list;
        }
        if (obj instanceof Object[] arr) {
            return Arrays.asList(arr);
        }
        return List.of(obj);
    }

    private static boolean isValidDirectiveBoundary(String text, int idx, int len) {
        boolean validBefore = (idx == 0 || Character.isWhitespace(text.charAt(idx - 1)) || text.charAt(idx - 1) == '>' || text.charAt(idx - 1) == '\n');
        int afterIdx = idx + len;
        boolean validAfter = (afterIdx >= text.length() || Character.isWhitespace(text.charAt(afterIdx)) || text.charAt(afterIdx) == '(' || text.charAt(afterIdx) == '<' || text.charAt(afterIdx) == ':');
        return validBefore && validAfter;
    }

    private static int findMatchingParen(String text, int openIdx) {
        if (openIdx == -1) return -1;
        int len = text.length();
        int depth = 1;
        for (int i = openIdx + 1; i < len; i++) {
            char c = text.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    record ConditionResult(boolean matches, Map<String, Object> bindings) {}

    static ConditionResult evaluateConditionWithBinding(JssrComponent component, Map<String, Object> localScope, String conditionExpr) {
        if (conditionExpr == null || conditionExpr.isBlank()) {
            return new ConditionResult(false, Collections.emptyMap());
        }

        String expr = conditionExpr.trim();
        if (expr.startsWith("${") && expr.endsWith("}")) {
            expr = expr.substring(2, expr.length() - 1).trim();
        }
        if (expr.startsWith("(") && expr.endsWith(")")) {
            expr = expr.substring(1, expr.length() - 1).trim();
        }

        if (expr.contains(" instanceof ")) {
            String[] parts = expr.split(" instanceof ", 2);
            String leftPath = cleanExprPath(parts[0].trim());
            String rightPart = parts[1].trim();

            Object leftVal = getPropertyValueOrNull(component, localScope, leftPath);
            if (leftVal instanceof Optional<?> opt) {
                leftVal = opt.orElse(null);
            }
            if (leftVal == null) {
                return new ConditionResult(false, Collections.emptyMap());
            }

            String[] rightTokens = rightPart.split("\\s+");
            String targetType = rightTokens[0].trim();
            String patternVar = rightTokens.length > 1 ? rightTokens[1].trim() : null;

            String actualTypeName = leftVal.getClass().getSimpleName();
            String actualFullTypeName = leftVal.getClass().getName();

            boolean matches = actualTypeName.equals(targetType) 
                    || actualFullTypeName.equals(targetType)
                    || actualFullTypeName.endsWith("." + targetType) 
                    || targetType.endsWith("." + actualTypeName)
                    || targetType.equalsIgnoreCase(actualTypeName);

            if (matches) {
                Map<String, Object> bindings = Collections.emptyMap();
                if (patternVar != null && !patternVar.isBlank()) {
                    bindings = Map.of(patternVar, leftVal);
                }
                return new ConditionResult(true, bindings);
            }
            return new ConditionResult(false, Collections.emptyMap());
        }

        boolean val = evaluateCondition(component, localScope, conditionExpr);
        return new ConditionResult(val, Collections.emptyMap());
    }

    /**
     * Evaluate condition expression (e.g. "user.isAdmin", "!disabled", "role == 'ADMIN'", "count > 0").
     */
    static boolean evaluateCondition(JssrComponent component, String conditionExpr) {
        return evaluateCondition(component, Collections.emptyMap(), conditionExpr);
    }

    static boolean evaluateCondition(JssrComponent component, Map<String, Object> localScope, String conditionExpr) {
        if (conditionExpr == null || conditionExpr.isBlank()) {
            return false;
        }

        String expr = conditionExpr.trim();
        if (expr.startsWith("${") && expr.endsWith("}")) {
            expr = expr.substring(2, expr.length() - 1).trim();
        }
        if (expr.startsWith("(") && expr.endsWith(")")) {
            expr = expr.substring(1, expr.length() - 1).trim();
        }

        if (expr.contains(" instanceof ")) {
            return evaluateConditionWithBinding(component, localScope, expr).matches();
        }

        if (expr.startsWith("!")) {
            return !evaluateCondition(component, localScope, expr.substring(1).trim());
        }

        if (expr.contains("==")) {
            String[] parts = expr.split("==", 2);
            String leftPath = cleanExprPath(parts[0].trim());
            String rightLit = cleanLiteral(parts[1].trim());
            Object leftVal = getPropertyValueOrNull(component, localScope, leftPath);
            String leftStr = leftVal == null ? "" : leftVal.toString();
            return leftStr.equalsIgnoreCase(rightLit);
        }

        if (expr.contains("!=")) {
            String[] parts = expr.split("!=", 2);
            String leftPath = cleanExprPath(parts[0].trim());
            String rightLit = cleanLiteral(parts[1].trim());
            Object leftVal = getPropertyValueOrNull(component, localScope, leftPath);
            String leftStr = leftVal == null ? "" : leftVal.toString();
            return !leftStr.equalsIgnoreCase(rightLit);
        }

        if (expr.contains(">=")) {
            String[] parts = expr.split(">=", 2);
            double leftNum = getNumericValue(component, localScope, cleanExprPath(parts[0].trim()));
            double rightNum = parseDoubleQuiet(parts[1].trim());
            return leftNum >= rightNum;
        }

        if (expr.contains("<=")) {
            String[] parts = expr.split("<=", 2);
            double leftNum = getNumericValue(component, localScope, cleanExprPath(parts[0].trim()));
            double rightNum = parseDoubleQuiet(parts[1].trim());
            return leftNum <= rightNum;
        }

        if (expr.contains(">")) {
            String[] parts = expr.split(">", 2);
            double leftNum = getNumericValue(component, localScope, cleanExprPath(parts[0].trim()));
            double rightNum = parseDoubleQuiet(parts[1].trim());
            return leftNum > rightNum;
        }

        if (expr.contains("<")) {
            String[] parts = expr.split("<", 2);
            double leftNum = getNumericValue(component, localScope, cleanExprPath(parts[0].trim()));
            double rightNum = parseDoubleQuiet(parts[1].trim());
            return leftNum < rightNum;
        }

        String path = cleanExprPath(expr);
        PropertyResult propRes = resolveProperty(component, localScope, path);
        if (!propRes.found()) {
            throw new IllegalArgumentException("Unknown JSSR control flow property '" + expr 
                    + "' in component " + component.getClass().getSimpleName());
        }

        return isTruthy(propRes.value());
    }

    private static String cleanExprPath(String path) {
        String p = path.trim();
        if (p.startsWith("${") && p.endsWith("}")) {
            p = p.substring(2, p.length() - 1).trim();
        }
        return p;
    }

    private static String cleanLiteral(String lit) {
        String l = lit.trim();
        if (l.startsWith("${") && l.endsWith("}")) {
            l = l.substring(2, l.length() - 1).trim();
        }
        if ((l.startsWith("'") && l.endsWith("'")) || (l.startsWith("\"") && l.endsWith("\""))) {
            return l.substring(1, l.length() - 1);
        }
        return l;
    }

    private static Object getPropertyValueOrNull(JssrComponent component, Map<String, Object> localScope, String path) {
        PropertyResult res = resolveProperty(component, localScope, path);
        return res.found() ? res.value() : null;
    }

    private static double getNumericValue(JssrComponent component, Map<String, Object> localScope, String path) {
        Object val = getPropertyValueOrNull(component, localScope, path);
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        return val == null ? 0.0 : parseDoubleQuiet(val.toString());
    }

    private static double parseDoubleQuiet(String s) {
        try {
            return Double.parseDouble(cleanLiteral(s));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static boolean isTruthy(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean b) return b;
        if (val instanceof BooleanAttribute ba) return ba.present();
        if (val instanceof Optional<?> opt) return opt.isPresent();
        if (val instanceof CharSequence cs) return !cs.toString().isBlank();
        if (val instanceof Number n) return n.doubleValue() != 0.0;
        if (val instanceof Collection<?> c) return !c.isEmpty();
        if (val instanceof Map<?,?> m) return !m.isEmpty();
        return true;
    }

    record PropertyResult(Object value, Class<?> type, boolean found) {}

    class ComponentMetadata {
        final boolean isRecord;
        final RecordComponent[] recordComponents;
        final Map<String, Method> accessors;
        final Map<String, Class<?>> types;
        final Constructor<?> constructor;

        ComponentMetadata(Class<?> clazz) {
            this.isRecord = clazz.isRecord();
            if (isRecord) {
                this.recordComponents = clazz.getRecordComponents();
                this.accessors = new HashMap<>();
                this.types = new HashMap<>();
                for (RecordComponent rc : recordComponents) {
                    Method m = rc.getAccessor();
                    m.setAccessible(true);
                    accessors.put(rc.getName(), m);
                    types.put(rc.getName(), rc.getType());
                }
                Constructor<?> c = null;
                try {
                    Class<?>[] paramTypes = new Class<?>[recordComponents.length];
                    for (int i = 0; i < recordComponents.length; i++) {
                        paramTypes[i] = recordComponents[i].getType();
                    }
                    c = clazz.getDeclaredConstructor(paramTypes);
                    c.setAccessible(true);
                } catch (Exception ignored) {}
                this.constructor = c;
            } else {
                this.recordComponents = new RecordComponent[0];
                this.accessors = Collections.emptyMap();
                this.types = Collections.emptyMap();
                Constructor<?> c = null;
                try {
                    c = clazz.getDeclaredConstructor();
                    c.setAccessible(true);
                } catch (Exception ignored) {}
                this.constructor = c;
            }
        }
    }
}

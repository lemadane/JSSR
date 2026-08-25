package com.jssr.core;

import com.jssr.core.compiler.JssrSecurity;
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
     * Per-render token table preserving object-valued custom-tag attributes across string interpolation.
     */
    ThreadLocal<Map<String, Object>> ATTRIBUTE_OBJECT_BINDINGS = ThreadLocal.withInitial(HashMap::new);
    ThreadLocal<Integer> ATTRIBUTE_OBJECT_BINDING_SEQ = ThreadLocal.withInitial(() -> 0);

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
     * Component HTML render method implemented by JSSR component Records.
     *
     * @return Native Java 17 multiline text block string
     */
    String render();

    /**
     * Default helper method to process and render a template string for this component.
     *
     * @param rawHtml Raw template text block string
     * @return Fully rendered HTML string
     */
    default String render(String rawHtml) {
        return JssrComponent.render(this, Collections.emptyMap(), rawHtml);
    }

    default String renderPrecompiled() {
        return JssrComponent.renderPrecompiled(this, Collections.emptyMap());
    }

    /**
     * Primary entry point. Interpolates ${fieldName} variables in a single pass, renders the template,
     * and automatically processes custom tags with depth recursion protection.
     *
     * @param component Component instance
     * @return Fully rendered HTML string with resolved variables and child tags
     */
    static String render(JssrComponent component) {
        return render(component, Collections.emptyMap());
    }

    static String render(JssrComponent component, Map<String, Object> localScope) {
        return render(component, localScope, null);
    }

    static String render(JssrComponent component, Map<String, Object> localScope, String explicitRawHtml) {
        if (component == null) return "";
        if (com.jssr.core.compiler.JssrPrecompiler.isGlobalPrecompilationEnabled()) {
            return renderPrecompiled(component, localScope);
        }

        int depth = RENDER_DEPTH.get();
        if (depth > MAX_RENDER_DEPTH) {
            throw new IllegalStateException("JSSR component recursion limit exceeded (max depth " 
                    + MAX_RENDER_DEPTH + ") for component: " + component.getClass().getSimpleName());
        }

        if (depth == 0) {
            ATTRIBUTE_OBJECT_BINDINGS.set(new HashMap<>());
            ATTRIBUTE_OBJECT_BINDING_SEQ.set(0);
        }

        RENDER_DEPTH.set(depth + 1);
        try {
            String rawHtml = explicitRawHtml != null ? explicitRawHtml : component.render();
            if (rawHtml == null || rawHtml.isBlank()) {
                return rawHtml == null ? "" : rawHtml;
            }

            Map<String, Object> scope = (localScope == null) ? Collections.emptyMap() : localScope;
            String controlFlowProcessed = processControlFlow(component, scope, rawHtml);
            String interpolatedHtml = interpolateVariables(component, scope, controlFlowProcessed);
            return processCustomTags(interpolatedHtml);
        } finally {
            RENDER_DEPTH.set(depth);
            if (depth == 0) {
                ATTRIBUTE_OBJECT_BINDINGS.remove();
                ATTRIBUTE_OBJECT_BINDING_SEQ.remove();
            }
        }
    }

    private static String bindAttributeObject(Object value) {
        int next = ATTRIBUTE_OBJECT_BINDING_SEQ.get() + 1;
        ATTRIBUTE_OBJECT_BINDING_SEQ.set(next);
        String token = "__JSSR_OBJ_" + next + "__";
        ATTRIBUTE_OBJECT_BINDINGS.get().put(token, value);
        return token;
    }

    private static Object resolveBoundAttributeObject(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return ATTRIBUTE_OBJECT_BINDINGS.get().get(token);
    }

    static String renderPrecompiled(JssrComponent component, Map<String, Object> localScope) {
        if (component == null) return "";
        int depth = RENDER_DEPTH.get();
        if (depth > MAX_RENDER_DEPTH) {
            throw new IllegalStateException("JSSR component recursion limit exceeded (max depth " 
                    + MAX_RENDER_DEPTH + ") for component: " + component.getClass().getSimpleName());
        }

        RENDER_DEPTH.set(depth + 1);
        try {
            return com.jssr.core.compiler.JssrPrecompiler.renderPrecompiled(component, localScope);
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
        boolean inCustomTag = false;
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

                        if (!activeAttr.isEmpty() && val instanceof RawHtml) {
                            throw new IllegalArgumentException("RawHtml cannot be interpolated inside an HTML attribute. Use safe string values, SafeUrl, BooleanAttribute, or HtmlAttribute.");
                        }

                        if (quoteChar == 0 && activeAttr.isEmpty()) {
                            // Free-standing attribute position inside tag, e.g. <input ${extra} />
                            if (val instanceof BooleanAttribute ba) {
                                sb.append(ba.render());
                            } else if (val instanceof HtmlAttribute ha) {
                                sb.append(ha.render());
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
                            JssrSecurity.AttributeContext ctx = JssrSecurity.classifyAttribute(activeAttr);
                            switch (ctx) {
                                case SRCDOC -> throw new IllegalArgumentException("JSSR interpolation ${" + varName 
                                        + "} inside 'srcdoc' attribute is forbidden due to HTML nested decoding risks.");
                                case FRAMEWORK_EXPRESSION -> throw new IllegalArgumentException("JSSR interpolation ${" + varName 
                                        + "} is not allowed inside executable framework attribute '" + activeAttr 
                                        + "'. Use safe server-side state or explicit expression APIs.");
                                case EVENT_HANDLER -> throw new IllegalArgumentException("JSSR interpolation ${" + varName 
                                        + "} is not allowed inside inline event handler attribute '" + activeAttr 
                                        + "'. Use HTMX/Alpine.js attributes or unobtrusive event listeners.");
                                case STYLE -> throw new IllegalArgumentException("JSSR interpolation ${" + varName 
                                        + "} is not allowed inside inline style attribute 'style'. Use CSS custom properties or external stylesheets.");
                                case SRCSET -> {
                                    if (!(val instanceof SafeSrcSet) && valType != SafeSrcSet.class) {
                                        throw new IllegalArgumentException("JSSR interpolation ${" + varName 
                                                + "} inside multi-candidate image attribute '" + activeAttr + "' requires a SafeSrcSet field type instead of " 
                                                + (valType != null ? valType.getSimpleName() : "String") + ".");
                                    }
                                }
                                case URL_LIST -> {
                                    if (!(val instanceof SafeUrlList) && valType != SafeUrlList.class) {
                                        throw new IllegalArgumentException("JSSR interpolation ${" + varName 
                                                + "} inside space-separated URL attribute '" + activeAttr + "' requires a SafeUrlList field type instead of " 
                                                + (valType != null ? valType.getSimpleName() : "String") + ".");
                                    }
                                }
                                case URL -> {
                                    if (!(val instanceof SafeUrl) && valType != SafeUrl.class) {
                                        throw new IllegalArgumentException("JSSR interpolation ${" + varName 
                                                + "} inside URL attribute '" + activeAttr + "' requires a SafeUrl field type instead of " 
                                                + (valType != null ? valType.getSimpleName() : "String") + ".");
                                    }
                                }
                                default -> {}
                            }

                            boolean pureQuotedPlaceholder = i > 0
                                    && end + 1 < len
                                    && html.charAt(i - 1) == quoteChar
                                    && html.charAt(end + 1) == quoteChar;

                            if (inCustomTag && pureQuotedPlaceholder && val != null) {
                                sb.append(bindAttributeObject(val));
                                i = end + 1;
                                continue;
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
                            } else if (val instanceof JssrComponent jc) {
                                formattedVal = escapeHtml(JssrComponent.render(jc));
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
                        } else if (val instanceof SafeSrcSet safeSet) {
                            valStr = escapeHtml(safeSet.render());
                        } else if (val instanceof SafeUrlList safeList) {
                            valStr = escapeHtml(safeList.render());
                        } else if (val instanceof JssrComponent jc) {
                            valStr = JssrComponent.render(jc);
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
                    inTag = false;
                    continue;
                }
            } else if ("script".equals(blockContext)) {
                if (i + 8 < len && html.substring(i, i + 9).toLowerCase(Locale.ROOT).equals("</script>")) {
                    sb.append(html, i, i + 9);
                    i += 9;
                    blockContext = null;
                    inTag = false;
                    continue;
                }
            } else if ("style".equals(blockContext)) {
                if (i + 7 < len && html.substring(i, i + 8).toLowerCase(Locale.ROOT).equals("</style>")) {
                    sb.append(html, i, i + 8);
                    i += 8;
                    blockContext = null;
                    inTag = false;
                    continue;
                }
            } else {
                if (c == '<') {
                    inCustomTag = false;

                    int tagNameStart = i + 1;
                    if (tagNameStart < len && Character.isUpperCase(html.charAt(tagNameStart))) {
                        int tagNameEnd = tagNameStart;
                        while (tagNameEnd < len && Character.isLetterOrDigit(html.charAt(tagNameEnd))) {
                            tagNameEnd++;
                        }

                        if (tagNameEnd < len) {
                            char delim = html.charAt(tagNameEnd);
                            if (delim == ' ' || delim == '\t' || delim == '\n' || delim == '\r' || delim == '/' || delim == '>') {
                                String tagName = html.substring(tagNameStart, tagNameEnd);
                                inCustomTag = REGISTRY.containsKey(tagName);
                            }
                        }
                    }

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
                            inCustomTag = false;
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

    private static ComponentMetadata getMetadata(Class<?> clazz) {
        return METADATA_CACHE.get(clazz);
    }

    private static String instantiateAndRender(Class<? extends JssrComponent> clazz, Map<String, String> attrs, boolean hasPairedBody) {
        try {
            ComponentMetadata meta = getMetadata(clazz);
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
                return JssrComponent.render(instance);
            } else {
                JssrComponent instance = (JssrComponent) meta.constructor.newInstance();
                return JssrComponent.render(instance);
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

        Object boundObject = resolveBoundAttributeObject(rawVal);
        if (boundObject != null) {
            if (targetType == Object.class || targetType.isInstance(boundObject) || targetType.isAssignableFrom(boundObject.getClass())) {
                return boundObject;
            }
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

    public static PropertyResult resolveProperty(Object obj, String propertyPath) {
        return resolveProperty(obj, Collections.emptyMap(), propertyPath);
    }

    public static PropertyResult resolveProperty(Object obj, Map<String, Object> localScope, String propertyPath) {
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

        if (obj instanceof Throwable t && "message".equals(trimmedPath)) {
            String msg = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            return new PropertyResult(msg, String.class, true);
        }
        Object curr = obj;
        Class<?> currType = obj.getClass();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (curr == null) {
                return new PropertyResult(null, Object.class, false);
            }
            ComponentMetadata meta = getMetadata(curr.getClass());
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

    enum LoopSignal { NONE, CONTINUE, BREAK }
    record ControlFlowResult(String content, LoopSignal signal) {}

    /**
     * Parse control flow directives (@if, @for, @continue, @break) in component templates.
     */
    static String processControlFlow(JssrComponent component, String template) {
        return processControlFlow(component, Collections.emptyMap(), template);
    }

    static String processControlFlow(JssrComponent component, Map<String, Object> localScope, String template) {
        if (component == null || template == null || template.isBlank() 
                || (!template.contains("@if") && !template.contains("@for")
                    && !template.contains("@switch") && !template.contains("@try") && !template.contains("@throw")
                    && !template.contains("@continue") && !template.contains("@break"))) {
            return template == null ? "" : template;
        }
        return parseControlFlowBlocks(component, localScope, template).content();
    }

    private static ControlFlowResult parseControlFlowBlocks(JssrComponent component, Map<String, Object> localScope, String text) {
        if (text == null || text.isEmpty() 
                || (!text.contains("@if") && !text.contains("@for")
                    && !text.contains("@switch") && !text.contains("@try") && !text.contains("@throw")
                    && !text.contains("@continue") && !text.contains("@break"))) {
            return new ControlFlowResult(text == null ? "" : text, LoopSignal.NONE);
        }

        StringBuilder result = new StringBuilder();
        int len = text.length();
        int curr = 0;

        while (curr < len) {
            if (curr + 4 <= len && text.startsWith("<!--", curr)) {
                int commentEnd = text.indexOf("-->", curr + 4);
                if (commentEnd != -1) {
                    result.append(text, curr, commentEnd + 3);
                    curr = commentEnd + 3;
                    continue;
                }
            }
            if (curr + 7 <= len && text.substring(curr, Math.min(curr + 8, len)).toLowerCase(Locale.ROOT).startsWith("<script")) {
                int scriptEnd = text.toLowerCase(Locale.ROOT).indexOf("</script>", curr);
                if (scriptEnd != -1) {
                    result.append(text, curr, scriptEnd + 9);
                    curr = scriptEnd + 9;
                    continue;
                }
            }
            if (curr + 6 <= len && text.substring(curr, Math.min(curr + 7, len)).toLowerCase(Locale.ROOT).startsWith("<style")) {
                int styleEnd = text.toLowerCase(Locale.ROOT).indexOf("</style>", curr);
                if (styleEnd != -1) {
                    result.append(text, curr, styleEnd + 8);
                    curr = styleEnd + 8;
                    continue;
                }
            }

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

    private static int findMatchingBrace(String text, int openBraceIdx) {
        if (openBraceIdx == -1 || openBraceIdx >= text.length() || text.charAt(openBraceIdx) != '{') {
            return -1;
        }
        int len = text.length();
        int depth = 1;
        int curr = openBraceIdx + 1;

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

            if (curr + 1 < len && text.charAt(curr) == '$' && text.charAt(curr + 1) == '{') {
                int interpEnd = text.indexOf('}', curr + 2);
                if (interpEnd != -1) {
                    curr = interpEnd + 1;
                    continue;
                }
            }

            char c = text.charAt(curr);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return curr;
                }
            }
            curr++;
        }
        return -1;
    }

    private static int findNextChar(String text, int start, char target) {
        int len = text.length();
        for (int i = start; i < len; i++) {
            char c = text.charAt(i);
            if (c == target) {
                return i;
            }
            if (c == ':') continue;
            if (!Character.isWhitespace(c)) {
                return -1;
            }
        }
        return -1;
    }

    private static int findNextNonWhitespace(String text, int start) {
        int len = text.length();
        for (int i = start; i < len; i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static IfBlockResult parseIfBlockAt(JssrComponent component, String text, int startIdx) {
        int len = text.length();
        List<Branch> branches = new ArrayList<>();

        int condOpen = text.indexOf('(', startIdx + 3);
        int condClose = (condOpen != -1) ? findMatchingParen(text, condOpen) : -1;
        if (condOpen == -1 || condClose == -1) {
            throw new IllegalArgumentException("Malformed '@if' condition in directive starting at index " + startIdx 
                    + " in component " + component.getClass().getSimpleName());
        }
        String firstCond = text.substring(condOpen + 1, condClose).trim();

        int openBrace = findNextChar(text, condClose + 1, '{');
        if (openBrace == -1) {
            throw new IllegalArgumentException("Malformed '@if' block starting at index " + startIdx 
                    + " in component " + component.getClass().getSimpleName() + ". Expected '{'.");
        }

        int closeBrace = findMatchingBrace(text, openBrace);
        if (closeBrace == -1) {
            throw new IllegalArgumentException("Unclosed JSSR control flow directive '@if' in component " 
                    + component.getClass().getSimpleName() + ". Expected matching '}'.");
        }

        String firstBody = text.substring(openBrace + 1, closeBrace);
        branches.add(new Branch(firstCond, false, firstBody));

        int curr = closeBrace + 1;
        while (curr < len) {
            int nextToken = findNextNonWhitespace(text, curr);
            if (nextToken == -1) break;

            if (text.startsWith("@elseif", nextToken) && isValidDirectiveBoundary(text, nextToken, 7)) {
                int elseifCondOpen = text.indexOf('(', nextToken + 7);
                int elseifCondClose = (elseifCondOpen != -1) ? findMatchingParen(text, elseifCondOpen) : -1;
                if (elseifCondOpen == -1 || elseifCondClose == -1) {
                    throw new IllegalArgumentException("Malformed '@elseif' condition near index " + nextToken 
                            + " in component " + component.getClass().getSimpleName());
                }
                String cond = text.substring(elseifCondOpen + 1, elseifCondClose).trim();

                int elseifOpenBrace = findNextChar(text, elseifCondClose + 1, '{');
                if (elseifOpenBrace == -1) {
                    throw new IllegalArgumentException("Malformed '@elseif' block near index " + nextToken 
                            + " in component " + component.getClass().getSimpleName() + ". Expected '{'.");
                }

                int elseifCloseBrace = findMatchingBrace(text, elseifOpenBrace);
                if (elseifCloseBrace == -1) {
                    throw new IllegalArgumentException("Unclosed JSSR control flow directive '@elseif' in component " 
                            + component.getClass().getSimpleName() + ". Expected matching '}'.");
                }

                String body = text.substring(elseifOpenBrace + 1, elseifCloseBrace);
                branches.add(new Branch(cond, false, body));
                curr = elseifCloseBrace + 1;
            } else if (text.startsWith("@else", nextToken) && isValidDirectiveBoundary(text, nextToken, 5)) {
                int elseOpenBrace = findNextChar(text, nextToken + 5, '{');
                if (elseOpenBrace == -1) {
                    throw new IllegalArgumentException("Malformed '@else' block near index " + nextToken 
                            + " in component " + component.getClass().getSimpleName() + ". Expected '{'.");
                }

                int elseCloseBrace = findMatchingBrace(text, elseOpenBrace);
                if (elseCloseBrace == -1) {
                    throw new IllegalArgumentException("Unclosed JSSR control flow directive '@else' in component " 
                            + component.getClass().getSimpleName() + ". Expected matching '}'.");
                }

                String body = text.substring(elseOpenBrace + 1, elseCloseBrace);
                branches.add(new Branch(null, true, body));
                curr = elseCloseBrace + 1;
                break;
            } else {
                break;
            }
        }

        return new IfBlockResult(branches, curr);
    }

    record ForBlockResult(String loopVar, String listExpr, String loopBody, String elseBody, boolean hasElse, int endIndex) {}

    private static ForBlockResult parseForBlockAt(JssrComponent component, String text, int startIdx) {
        int len = text.length();
        int condOpen = text.indexOf('(', startIdx + 4);
        int condClose = (condOpen != -1) ? findMatchingParen(text, condOpen) : -1;
        if (condOpen == -1 || condClose == -1) {
            throw new IllegalArgumentException("Malformed '@for' condition in directive starting at index " + startIdx 
                    + " in component " + component.getClass().getSimpleName());
        }

        String header = text.substring(condOpen + 1, condClose).trim();
        String[] parts = header.split(":", 2);
        if (parts.length != 2) {
            parts = header.split(" in ", 2);
        }
        if (parts.length != 2) {
            throw new IllegalArgumentException("Malformed '@for' header '" + header + "' (expected format: 'item : collection') in component " 
                    + component.getClass().getSimpleName());
        }

        String loopVar = parts[0].trim();
        if (loopVar.contains(" ")) {
            String[] tok = loopVar.split("\\s+");
            loopVar = tok[tok.length - 1];
        }
        String listExpr = parts[1].trim();

        int openBrace = findNextChar(text, condClose + 1, '{');
        if (openBrace == -1) {
            throw new IllegalArgumentException("Malformed '@for' block starting at index " + startIdx 
                    + " in component " + component.getClass().getSimpleName() + ". Expected '{'.");
        }

        int closeBrace = findMatchingBrace(text, openBrace);
        if (closeBrace == -1) {
            throw new IllegalArgumentException("Unclosed JSSR control flow directive '@for' in component " 
                    + component.getClass().getSimpleName() + ". Expected matching '}'.");
        }

        String loopBody = text.substring(openBrace + 1, closeBrace);
        String elseBody = "";
        boolean hasElse = false;
        int curr = closeBrace + 1;

        int nextToken = findNextNonWhitespace(text, curr);
        if (nextToken != -1 && text.startsWith("@else", nextToken) && isValidDirectiveBoundary(text, nextToken, 5)) {
            int elseOpenBrace = findNextChar(text, nextToken + 5, '{');
            if (elseOpenBrace == -1) {
                throw new IllegalArgumentException("Malformed '@else' block in '@for' near index " + nextToken 
                        + " in component " + component.getClass().getSimpleName() + ". Expected '{'.");
            }
            int elseCloseBrace = findMatchingBrace(text, elseOpenBrace);
            if (elseCloseBrace == -1) {
                throw new IllegalArgumentException("Unclosed '@else' block in '@for' in component " 
                        + component.getClass().getSimpleName() + ". Expected matching '}'.");
            }
            elseBody = text.substring(elseOpenBrace + 1, elseCloseBrace);
            hasElse = true;
            curr = elseCloseBrace + 1;
        }

        return new ForBlockResult(loopVar, listExpr, loopBody, elseBody, hasElse, curr);
    }

    record TryBlockResult(String tryBody, String catchVar, String catchBody, boolean hasCatch, String finallyBody, boolean hasFinally, int endIndex) {}

    private static TryBlockResult parseTryBlockAt(JssrComponent component, String text, int startIdx) {
        int len = text.length();

        int openBrace = findNextChar(text, startIdx + 4, '{');
        if (openBrace == -1) {
            throw new IllegalArgumentException("Malformed '@try' block starting at index " + startIdx 
                    + " in component " + component.getClass().getSimpleName() + ". Expected '{'.");
        }

        int closeBrace = findMatchingBrace(text, openBrace);
        if (closeBrace == -1) {
            throw new IllegalArgumentException("Unclosed JSSR control flow directive '@try' in component " 
                    + component.getClass().getSimpleName() + ". Expected matching '}'.");
        }

        String tryBody = text.substring(openBrace + 1, closeBrace);
        String catchVar = null;
        String catchBody = "";
        boolean hasCatch = false;
        String finallyBody = "";
        boolean hasFinally = false;

        int curr = closeBrace + 1;
        while (curr < len) {
            int nextToken = findNextNonWhitespace(text, curr);
            if (nextToken == -1) break;

            if (text.startsWith("@catch", nextToken) && isValidDirectiveBoundary(text, nextToken, 6)) {
                hasCatch = true;
                int afterCatch = nextToken + 6;
                int parenOpen = findNextChar(text, afterCatch, '(');
                int afterParen = afterCatch;
                if (parenOpen != -1) {
                    int parenClose = findMatchingParen(text, parenOpen);
                    if (parenClose != -1) {
                        catchVar = text.substring(parenOpen + 1, parenClose).trim();
                        afterParen = parenClose + 1;
                    }
                }

                int catchOpenBrace = findNextChar(text, afterParen, '{');
                if (catchOpenBrace == -1) {
                    throw new IllegalArgumentException("Malformed '@catch' block near index " + nextToken + ". Expected '{'.");
                }
                int catchCloseBrace = findMatchingBrace(text, catchOpenBrace);
                if (catchCloseBrace == -1) {
                    throw new IllegalArgumentException("Unclosed '@catch' block near index " + nextToken);
                }

                catchBody = text.substring(catchOpenBrace + 1, catchCloseBrace);
                curr = catchCloseBrace + 1;
            } else if (text.startsWith("@finally", nextToken) && isValidDirectiveBoundary(text, nextToken, 8)) {
                hasFinally = true;
                int finallyOpenBrace = findNextChar(text, nextToken + 8, '{');
                if (finallyOpenBrace == -1) {
                    throw new IllegalArgumentException("Malformed '@finally' block near index " + nextToken + ". Expected '{'.");
                }
                int finallyCloseBrace = findMatchingBrace(text, finallyOpenBrace);
                if (finallyCloseBrace == -1) {
                    throw new IllegalArgumentException("Unclosed '@finally' block near index " + nextToken);
                }

                finallyBody = text.substring(finallyOpenBrace + 1, finallyCloseBrace);
                curr = finallyCloseBrace + 1;
            } else {
                break;
            }
        }

        return new TryBlockResult(tryBody, catchVar, catchBody, hasCatch, finallyBody, hasFinally, curr);
    }

    record SwitchBlockResult(String switchExpr, List<SwitchCase> cases, SwitchCase defaultCase, boolean hasDefault, int endIndex) {}
    record SwitchCase(String caseExpr, String body, boolean isDefault) {}

    private static SwitchBlockResult parseSwitchBlockAt(JssrComponent component, String text, int startIdx) {
        int len = text.length();
        int condOpen = text.indexOf('(', startIdx + 7);
        int condClose = (condOpen != -1) ? findMatchingParen(text, condOpen) : -1;
        if (condOpen == -1 || condClose == -1) {
            throw new IllegalArgumentException("Malformed '@switch' condition in directive starting at index " + startIdx 
                    + " in component " + component.getClass().getSimpleName());
        }

        String switchExpr = text.substring(condOpen + 1, condClose).trim();

        int nextCharHeader = findNextNonWhitespace(text, condClose + 1);
        int switchBodyStart;
        int switchBodyEnd;
        int endIdxAfterSwitch;
        if (nextCharHeader != -1 && text.charAt(nextCharHeader) == '{') {
            int closeBrace = findMatchingBrace(text, nextCharHeader);
            if (closeBrace == -1) {
                throw new IllegalArgumentException("Unclosed JSSR control flow directive '@switch' in component " 
                        + component.getClass().getSimpleName() + ". Expected matching '}'.");
            }
            switchBodyStart = nextCharHeader + 1;
            switchBodyEnd = closeBrace;
            endIdxAfterSwitch = closeBrace + 1;
        } else if (nextCharHeader != -1 && text.charAt(nextCharHeader) == ':') {
            switchBodyStart = nextCharHeader + 1;
            int endDirective = findNextControlFlowDirective(text, switchBodyStart);
            while (endDirective != -1 && (text.startsWith("@case", endDirective) || text.startsWith("@default", endDirective))) {
                endDirective = findNextControlFlowDirective(text, endDirective + 5);
            }
            if (endDirective != -1 && text.startsWith("@end", endDirective)) {
                switchBodyEnd = endDirective;
                endIdxAfterSwitch = endDirective + 4;
            } else {
                switchBodyEnd = len;
                endIdxAfterSwitch = len;
            }
        } else {
            throw new IllegalArgumentException("Malformed '@switch' block starting at index " + startIdx 
                    + " in component " + component.getClass().getSimpleName() + ". Expected '{' or ':'.");
        }

        String switchBody = text.substring(switchBodyStart, switchBodyEnd);
        List<SwitchCase> cases = new ArrayList<>();
        SwitchCase defaultCase = null;
        boolean hasDefault = false;

        int curr = 0;
        int bodyLen = switchBody.length();

        while (curr < bodyLen) {
            int nextDirective = findNextControlFlowDirective(switchBody, curr);
            if (nextDirective == -1) break;

            if (switchBody.startsWith("@case", nextDirective) && isValidDirectiveBoundary(switchBody, nextDirective, 5)) {
                int caseCondOpen = switchBody.indexOf('(', nextDirective + 5);
                int caseCondClose = (caseCondOpen != -1) ? findMatchingParen(switchBody, caseCondOpen) : -1;
                if (caseCondOpen == -1 || caseCondClose == -1) {
                    throw new IllegalArgumentException("Malformed '@case' condition near index " + nextDirective 
                            + " in component " + component.getClass().getSimpleName());
                }
                String caseExpr = switchBody.substring(caseCondOpen + 1, caseCondClose).trim();

                int nextChar = findNextNonWhitespace(switchBody, caseCondClose + 1);
                if (nextChar != -1 && switchBody.charAt(nextChar) == '{') {
                    int caseCloseBrace = findMatchingBrace(switchBody, nextChar);
                    if (caseCloseBrace == -1) {
                        throw new IllegalArgumentException("Unclosed '@case' block in '@switch' near index " + nextDirective);
                    }
                    String caseBody = switchBody.substring(nextChar + 1, caseCloseBrace);
                    cases.add(new SwitchCase(caseExpr, caseBody, false));
                    curr = caseCloseBrace + 1;
                } else if (nextChar != -1 && switchBody.charAt(nextChar) == ':') {
                    int startBlock = nextChar + 1;
                    int nextCase = findNextCaseOrDefaultOrEnd(switchBody, startBlock);
                    int endBlock = (nextCase != -1) ? nextCase : bodyLen;
                    String caseBody = switchBody.substring(startBlock, endBlock);
                    cases.add(new SwitchCase(caseExpr, caseBody, false));
                    curr = endBlock;
                } else {
                    throw new IllegalArgumentException("Malformed '@case' block near index " + nextDirective + ". Expected '{' or ':'.");
                }
            } else if (switchBody.startsWith("@default", nextDirective) && isValidDirectiveBoundary(switchBody, nextDirective, 8)) {
                int nextChar = findNextNonWhitespace(switchBody, nextDirective + 8);
                if (nextChar != -1 && switchBody.charAt(nextChar) == '{') {
                    int defaultCloseBrace = findMatchingBrace(switchBody, nextChar);
                    if (defaultCloseBrace == -1) {
                        throw new IllegalArgumentException("Unclosed '@default' block in '@switch'.");
                    }
                    String defaultBody = switchBody.substring(nextChar + 1, defaultCloseBrace);
                    defaultCase = new SwitchCase(null, defaultBody, true);
                    hasDefault = true;
                    curr = defaultCloseBrace + 1;
                } else if (nextChar != -1 && switchBody.charAt(nextChar) == ':') {
                    int startBlock = nextChar + 1;
                    int nextCase = findNextCaseOrDefaultOrEnd(switchBody, startBlock);
                    int endBlock = (nextCase != -1) ? nextCase : bodyLen;
                    String defaultBody = switchBody.substring(startBlock, endBlock);
                    defaultCase = new SwitchCase(null, defaultBody, true);
                    hasDefault = true;
                    curr = endBlock;
                } else {
                    throw new IllegalArgumentException("Malformed '@default' block in '@switch'. Expected '{' or ':'.");
                }
            } else {
                curr = nextDirective + 1;
            }
        }

        return new SwitchBlockResult(switchExpr, cases, defaultCase, hasDefault, endIdxAfterSwitch);
    }

    private static int findNextCaseOrDefaultOrEnd(String text, int fromIdx) {
        int curr = fromIdx;
        int len = text.length();
        while (curr < len) {
            int next = text.indexOf('@', curr);
            if (next == -1) return -1;
            if ((text.startsWith("@case", next) && isValidDirectiveBoundary(text, next, 5))
             || (text.startsWith("@default", next) && isValidDirectiveBoundary(text, next, 8))
             || (text.startsWith("@end", next) && isValidDirectiveBoundary(text, next, 4))) {
                return next;
            }
            curr = next + 1;
        }
        return -1;
    }

    private static ControlFlowResult evaluateSwitchBlockResult(JssrComponent component, Map<String, Object> localScope, SwitchBlockResult switchResult) {
        Object switchVal;
        String expr = switchResult.switchExpr.trim();
        if (expr.startsWith("typeof(") && expr.endsWith(")")) {
            String targetPath = expr.substring(7, expr.length() - 1).trim();
            Object targetObj = getPropertyValueOrNull(component, localScope, targetPath);
            switchVal = targetObj != null ? targetObj.getClass().getSimpleName() : "null";
        } else {
            PropertyResult switchRes = resolveProperty(component, localScope, expr);
            switchVal = switchRes.value();
        }
        String switchValStr = switchVal == null ? "null" : String.valueOf(switchVal);

        StringBuilder sb = new StringBuilder();
        boolean fallthrough = false;
        boolean matchedAny = false;

        for (SwitchCase sc : switchResult.cases) {
            String caseValExpr = sc.caseExpr();
            String caseValClean = cleanCaseLiteral(caseValExpr);

            if (fallthrough || switchValStr.equalsIgnoreCase(caseValClean) || switchValStr.equals(caseValExpr)) {
                fallthrough = true;
                matchedAny = true;
                ControlFlowResult flowRes = parseControlFlowBlocks(component, localScope, sc.body());
                String interpolated = interpolateVariables(component, localScope, flowRes.content());
                sb.append(interpolated);

                if (flowRes.signal() == LoopSignal.BREAK) {
                    return new ControlFlowResult(sb.toString(), LoopSignal.NONE);
                } else if (flowRes.signal() != LoopSignal.NONE) {
                    return new ControlFlowResult(sb.toString(), flowRes.signal());
                }
            }
        }

        if ((fallthrough || !matchedAny) && switchResult.hasDefault()) {
            ControlFlowResult flowRes = parseControlFlowBlocks(component, localScope, switchResult.defaultCase().body());
            String interpolated = interpolateVariables(component, localScope, flowRes.content());
            sb.append(interpolated);
            if (flowRes.signal() == LoopSignal.BREAK) {
                return new ControlFlowResult(sb.toString(), LoopSignal.NONE);
            } else if (flowRes.signal() != LoopSignal.NONE) {
                return new ControlFlowResult(sb.toString(), flowRes.signal());
            }
        }

        return new ControlFlowResult(sb.toString(), LoopSignal.NONE);
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



    @SuppressWarnings("unchecked")
    public static List<?> toList(Object obj) {
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
        boolean validBefore = (idx == 0 || !Character.isLetterOrDigit(text.charAt(idx - 1)));
        int afterIdx = idx + len;
        boolean validAfter = (afterIdx >= text.length() || !Character.isLetterOrDigit(text.charAt(afterIdx)));
        return validBefore && validAfter;
    }

    private static boolean isDirectiveAt(String text, int idx) {
        return (text.startsWith("@if", idx) && isValidDirectiveBoundary(text, idx, 3))
            || (text.startsWith("@for", idx) && isValidDirectiveBoundary(text, idx, 4))
            || (text.startsWith("@switch", idx) && isValidDirectiveBoundary(text, idx, 7))
            || (text.startsWith("@case", idx) && isValidDirectiveBoundary(text, idx, 5))
            || (text.startsWith("@default", idx) && isValidDirectiveBoundary(text, idx, 8))
            || (text.startsWith("@try", idx) && isValidDirectiveBoundary(text, idx, 4))
            || (text.startsWith("@catch", idx) && isValidDirectiveBoundary(text, idx, 6))
            || (text.startsWith("@finally", idx) && isValidDirectiveBoundary(text, idx, 8))
            || (text.startsWith("@elseif", idx) && isValidDirectiveBoundary(text, idx, 7))
            || (text.startsWith("@else", idx) && isValidDirectiveBoundary(text, idx, 5))
            || (text.startsWith("@throw", idx) && isValidDirectiveBoundary(text, idx, 6))
            || (text.startsWith("@continue", idx) && isValidDirectiveBoundary(text, idx, 9))
            || (text.startsWith("@break", idx) && isValidDirectiveBoundary(text, idx, 6));
    }

    private static int findNextControlFlowDirective(String text, int start) {
        int len = text.length();
        int i = start;
        while (i < len) {
            if (i + 4 <= len && text.startsWith("<!--", i)) {
                int commentEnd = text.indexOf("-->", i + 4);
                if (commentEnd != -1) {
                    i = commentEnd + 3;
                    continue;
                }
            }
            if (i + 7 <= len && text.substring(i, Math.min(i + 8, len)).toLowerCase(Locale.ROOT).startsWith("<script")) {
                int scriptEnd = text.toLowerCase(Locale.ROOT).indexOf("</script>", i);
                if (scriptEnd != -1) {
                    i = scriptEnd + 9;
                    continue;
                }
            }
            if (i + 6 <= len && text.substring(i, Math.min(i + 7, len)).toLowerCase(Locale.ROOT).startsWith("<style")) {
                int styleEnd = text.toLowerCase(Locale.ROOT).indexOf("</style>", i);
                if (styleEnd != -1) {
                    i = styleEnd + 8;
                    continue;
                }
            }
            if (text.charAt(i) == '@' && isDirectiveAt(text, i)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private static void executeThrowDirective(JssrComponent component, Map<String, Object> localScope, String throwExpr) {
        if (throwExpr != null && throwExpr.trim().startsWith("new ")) {
            String expr = throwExpr.trim().substring(4).trim();
            int parenOpen = expr.indexOf('(');
            int parenClose = expr.lastIndexOf(')');
            if (parenOpen != -1 && parenClose != -1) {
                String className = expr.substring(0, parenOpen).trim();
                String arg = expr.substring(parenOpen + 1, parenClose).trim();
                if ((arg.startsWith("\"") && arg.endsWith("\"")) || (arg.startsWith("'") && arg.endsWith("'"))) {
                    arg = arg.substring(1, arg.length() - 1);
                }
                try {
                    Class<?> clazz;
                    try {
                        clazz = Class.forName(className);
                    } catch (ClassNotFoundException cnfe) {
                        clazz = Class.forName("java.lang." + className);
                    }
                    Throwable t = (Throwable) clazz.getConstructor(String.class).newInstance(arg);
                    if (t instanceof RuntimeException re) throw re;
                    if (t instanceof Error err) throw err;
                    throw new RuntimeException(t);
                } catch (RuntimeException re) {
                    throw re;
                } catch (Error err) {
                    throw err;
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        }

        String message = "Manual @throw in template execution";
        if (throwExpr != null && !throwExpr.isBlank()) {
            if ((throwExpr.startsWith("\"") && throwExpr.endsWith("\"")) || (throwExpr.startsWith("'") && throwExpr.endsWith("'"))) {
                message = throwExpr.substring(1, throwExpr.length() - 1);
            } else {
                PropertyResult res = resolveProperty(component, localScope, throwExpr);
                if (res.value() != null) {
                    message = String.valueOf(res.value());
                }
            }
        }
        throw new RuntimeException(message);
    }

    private static ControlFlowResult evaluateTryBlockResult(JssrComponent component, Map<String, Object> localScope, TryBlockResult tryResult) {
        StringBuilder sb = new StringBuilder();
        LoopSignal signal = LoopSignal.NONE;
        try {
            ControlFlowResult tryRes = parseControlFlowBlocks(component, localScope, tryResult.tryBody);
            String interpolated = interpolateVariables(component, localScope, tryRes.content());
            sb.append(interpolated);
            signal = tryRes.signal();
        } catch (Throwable e) {
            if (e instanceof OutOfMemoryError || e instanceof StackOverflowError || e instanceof LinkageError) {
                throw e;
            }
            if (tryResult.hasCatch) {
                Map<String, Object> catchScope = new HashMap<>(localScope);
                String catchVar = tryResult.catchVar != null && !tryResult.catchVar.isBlank() ? tryResult.catchVar : "e";
                String rawMsg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                String safeMsg = rawMsg.replace("${", "&#36;{");
                catchScope.put(catchVar, e);
                catchScope.put(catchVar + ".message", safeMsg);
                catchScope.put("err", e);
                catchScope.put("err.message", safeMsg);

                ControlFlowResult catchRes = parseControlFlowBlocks(component, catchScope, tryResult.catchBody);
                String interpolated = interpolateVariables(component, catchScope, catchRes.content());
                sb.append(interpolated);
                signal = catchRes.signal();
            }
        } finally {
            if (tryResult.hasFinally) {
                ControlFlowResult finallyRes = parseControlFlowBlocks(component, localScope, tryResult.finallyBody);
                String interpolated = interpolateVariables(component, localScope, finallyRes.content());
                sb.append(interpolated);
                if (finallyRes.signal() != LoopSignal.NONE) {
                    signal = finallyRes.signal();
                }
            }
        }
        return new ControlFlowResult(sb.toString(), signal);
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

    public record ConditionResult(boolean matches, Map<String, Object> bindings) {}

    public static ConditionResult evaluateConditionWithBinding(JssrComponent component, Map<String, Object> localScope, String conditionExpr) {
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
    public static boolean evaluateCondition(JssrComponent component, String conditionExpr) {
        return evaluateCondition(component, Collections.emptyMap(), conditionExpr);
    }

    public static boolean evaluateCondition(JssrComponent component, Map<String, Object> localScope, String conditionExpr) {
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

    public static boolean isTruthy(Object val) {
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

    public static String renderInterpolatedExpression(JssrComponent component, Map<String, Object> localScope, String expr) {
        return renderInterpolatedExpression(component, localScope, expr, false, "", (char) 0);
    }

    public static String renderInterpolatedExpression(JssrComponent component, Map<String, Object> localScope, String expr, boolean inTag, String activeAttr, char quoteChar) {
        if (expr == null || expr.isBlank()) return "";
        PropertyResult res = resolveProperty(component, localScope, expr);
        if (!res.found()) {
            throw new IllegalArgumentException("Unknown JSSR interpolation property '${" + expr + "}' in component " 
                    + (component != null ? component.getClass().getSimpleName() : "null"));
        }
        Object val = res.value();
        Class<?> valType = res.type();

        if (inTag) {
            if (!activeAttr.isEmpty() && val instanceof RawHtml) {
                throw new IllegalArgumentException("RawHtml cannot be interpolated inside an HTML attribute. Use safe string values, SafeUrl, BooleanAttribute, or HtmlAttribute.");
            }

            if (quoteChar == 0 && activeAttr.isEmpty()) {
                if (val instanceof BooleanAttribute ba) {
                    return ba.render();
                } else if (val instanceof HtmlAttribute ha) {
                    return ha.render();
                } else if (valType == boolean.class || valType == Boolean.class || val instanceof Boolean) {
                    boolean boolVal = val != null && (Boolean) val;
                    String attrName = expr.contains(".") ? expr.substring(expr.lastIndexOf('.') + 1) : expr;
                    return boolVal ? attrName : "";
                } else {
                    throw new IllegalArgumentException("JSSR interpolation ${" + expr
                            + "} of type " + (valType != null ? valType.getSimpleName() : "unknown")
                            + " in free-standing HTML attribute position is forbidden. Use boolean fields, BooleanAttribute, or HtmlAttribute for dynamic attributes.");
                }
            } else if (quoteChar == 0 && !activeAttr.isEmpty()) {
                throw new IllegalArgumentException("JSSR interpolation in an unquoted HTML attribute is forbidden. Quote the attribute value: " 
                        + activeAttr + "=\"${" + expr + "}\"");
            } else {
                JssrSecurity.AttributeContext ctx = JssrSecurity.classifyAttribute(activeAttr);
                switch (ctx) {
                    case SRCDOC -> throw new IllegalArgumentException("JSSR interpolation ${" + expr + "} inside 'srcdoc' attribute is forbidden due to HTML nested decoding risks.");
                    case FRAMEWORK_EXPRESSION -> throw new IllegalArgumentException("JSSR interpolation ${" + expr + "} is not allowed inside executable framework attribute '" + activeAttr + "'. Use safe server-side state or explicit expression APIs.");
                    case EVENT_HANDLER -> throw new IllegalArgumentException("JSSR interpolation ${" + expr + "} is not allowed inside inline event handler attribute '" + activeAttr + "'. Use HTMX/Alpine.js attributes or unobtrusive event listeners.");
                    case STYLE -> throw new IllegalArgumentException("JSSR interpolation ${" + expr + "} is not allowed inside inline style attribute 'style'. Use CSS custom properties or external stylesheets.");
                    case SRCSET -> {
                        if (!(val instanceof SafeSrcSet) && valType != SafeSrcSet.class) {
                            throw new IllegalArgumentException("JSSR interpolation ${" + expr + "} inside multi-candidate image attribute '" + activeAttr + "' requires a SafeSrcSet field type.");
                        }
                    }
                    case URL_LIST -> {
                        if (!(val instanceof SafeUrlList) && valType != SafeUrlList.class) {
                            throw new IllegalArgumentException("JSSR interpolation ${" + expr + "} inside space-separated URL attribute '" + activeAttr + "' requires a SafeUrlList field type.");
                        }
                    }
                    case URL -> {
                        if (!(val instanceof SafeUrl) && valType != SafeUrl.class) {
                            throw new IllegalArgumentException("JSSR interpolation ${" + expr + "} inside URL attribute '" + activeAttr + "' requires a SafeUrl field type.");
                        }
                    }
                    default -> {}
                }

                if (val == null) return "";
                if (val instanceof SafeUrl safe) return escapeHtml(safe.render());
                if (val instanceof SafeSrcSet safeSet) return escapeHtml(safeSet.render());
                if (val instanceof SafeUrlList safeList) return escapeHtml(safeList.render());
                if (val instanceof JssrComponent jc) return escapeHtml(com.jssr.core.compiler.JssrPrecompiler.renderPrecompiled(jc));
                if (val instanceof Optional<?> opt) return opt.map(o -> escapeHtml(o.toString())).orElse("");
                return escapeHtml(val.toString());
            }
        } else {
            if (val == null) return "";
            if (val instanceof RawHtml raw) return raw.value() == null ? "" : raw.value();
            if (val instanceof SafeUrl safe) return escapeHtml(safe.render());
            if (val instanceof SafeSrcSet safeSet) return escapeHtml(safeSet.render());
            if (val instanceof SafeUrlList safeList) return escapeHtml(safeList.render());
            if (val instanceof JssrComponent jc) return com.jssr.core.compiler.JssrPrecompiler.renderPrecompiled(jc);
            if (val instanceof Optional<?> opt) return opt.map(o -> escapeHtml(o.toString())).orElse("");
            return escapeHtml(val.toString());
        }
    }

    public static String renderCustomTag(JssrComponent component, Map<String, Object> localScope, String tagName, Map<String, String> attributes) {
        if (!REGISTRY.containsKey(tagName)) return "";
        Class<? extends JssrComponent> clazz = REGISTRY.get(tagName);
        return instantiateAndRender(clazz, attributes != null ? attributes : Collections.emptyMap(), false);
    }

    public record PropertyResult(Object value, Class<?> type, boolean found) {}

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

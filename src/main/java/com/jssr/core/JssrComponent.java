package com.jssr.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
     * Primary entry point. Interpolates ${fieldName} variables, renders the template,
     * and automatically processes custom tags.
     *
     * @return Fully rendered HTML string with resolved variables and child tags
     */
    default String render() {
        String rawHtml = template();
        if (rawHtml == null || rawHtml.isBlank()) {
            return rawHtml == null ? "" : rawHtml;
        }

        String interpolatedHtml = interpolateVariables(this, rawHtml);
        return processCustomTags(interpolatedHtml);
    }

    /**
     * Interpolate ${fieldName} placeholders in HTML templates using Record field values.
     *
     * @param component The component instance
     * @param html HTML template string containing ${fieldName} placeholders
     * @return HTML string with interpolated variable values
     */
    static String interpolateVariables(JssrComponent component, String html) {
        if (component == null || html == null || html.isBlank()) {
            return html == null ? "" : html;
        }

        Class<?> clazz = component.getClass();
        if (clazz.isRecord()) {
            RecordComponent[] recordComponents = clazz.getRecordComponents();
            for (RecordComponent rc : recordComponents) {
                try {
                    Object val = rc.getAccessor().invoke(component);
                    String valStr = val == null ? "" : val.toString();
                    String placeholder = "${" + rc.getName() + "}";
                    html = html.replace(placeholder, valStr);
                } catch (Exception ignored) {
                }
            }
        }
        return html;
    }

    /**
     * Process custom JSX-like child tags inside rendered HTML strings.
     *
     * @param html HTML input string containing custom tags
     * @return Rendered HTML string with custom tags replaced by component HTML
     */
    static String processCustomTags(String html) {
        if (REGISTRY.isEmpty() || html == null || html.isBlank()) {
            return html == null ? "" : html;
        }

        Pattern pattern = Pattern.compile("<([A-Z][a-zA-Z0-9]*)\\s*([^/>]*)/?>");
        Matcher matcher = pattern.matcher(html);

        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String tagName = matcher.group(1);
            String attrString = matcher.group(2);

            if (REGISTRY.containsKey(tagName)) {
                Class<? extends JssrComponent> clazz = REGISTRY.get(tagName);
                Map<String, String> attrs = parseAttributes(attrString);
                String renderedChild = instantiateAndRender(clazz, attrs);
                matcher.appendReplacement(sb, Matcher.quoteReplacement(renderedChild));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static Map<String, String> parseAttributes(String attrString) {
        Map<String, String> attrs = new HashMap<>();
        if (attrString == null || attrString.isBlank()) {
            return attrs;
        }
        Pattern attrPattern = Pattern.compile("([a-zA-Z0-9-]+)=\"([^\"]*)\"");
        Matcher matcher = attrPattern.matcher(attrString);
        while (matcher.find()) {
            attrs.put(matcher.group(1), matcher.group(2));
        }
        return attrs;
    }

    private static String instantiateAndRender(Class<? extends JssrComponent> clazz, Map<String, String> attrs) {
        try {
            if (clazz.isRecord()) {
                RecordComponent[] recordComponents = clazz.getRecordComponents();
                Object[] args = new Object[recordComponents.length];
                Class<?>[] paramTypes = new Class<?>[recordComponents.length];

                for (int i = 0; i < recordComponents.length; i++) {
                    RecordComponent rc = recordComponents[i];
                    paramTypes[i] = rc.getType();
                    String rawVal = attrs.get(rc.getName());
                    args[i] = convertStringValue(rawVal, rc.getType());
                }

                Constructor<? extends JssrComponent> ctor = clazz.getDeclaredConstructor(paramTypes);
                JssrComponent instance = ctor.newInstance(args);
                return instance.render();
            } else {
                Constructor<?>[] ctors = clazz.getConstructors();
                Constructor<?> ctor = ctors[0];
                if (ctor.getParameterCount() == 0) {
                    JssrComponent instance = (JssrComponent) ctor.newInstance();
                    return instance.render();
                }
                throw new IllegalStateException("Non-record component <" + clazz.getSimpleName() + "> must be a Record or have a no-arg constructor.");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error rendering JSSR component tag <" + clazz.getSimpleName() + ">: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object convertStringValue(String rawVal, Class<?> targetType) {
        if (rawVal == null) {
            if (targetType == boolean.class) return false;
            if (targetType == int.class) return 0;
            if (targetType == long.class) return 0L;
            if (targetType == double.class) return 0.0;
            return null;
        }

        if (targetType == String.class || targetType == Object.class) {
            return rawVal;
        }
        if (targetType == Long.class || targetType == long.class) {
            return rawVal.isEmpty() ? 0L : Long.parseLong(rawVal);
        }
        if (targetType == Integer.class || targetType == int.class) {
            return rawVal.isEmpty() ? 0 : Integer.parseInt(rawVal);
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.parseBoolean(rawVal);
        }
        if (targetType.isEnum()) {
            return Enum.valueOf((Class<Enum>) targetType, rawVal.toUpperCase());
        }

        return rawVal;
    }
}

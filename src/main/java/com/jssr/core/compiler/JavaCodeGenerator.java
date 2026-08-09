package com.jssr.core.compiler;

import com.jssr.core.JssrComponent;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Java source code generator for JSSR precompiled JVM bytecode templates.
 * Modeled after PTE's (Piped Template Engine) JavaCodeGenerator.
 */
public final class JavaCodeGenerator {
    private static final AtomicInteger COUNTER = new AtomicInteger();

    /**
     * Generate a unique class name for a compiled template.
     *
     * @return Generated class name
     */
    public static String generateUniqueClassName() {
        return "JssrTemplate_Gen_" + System.currentTimeMillis() + "_" + COUNTER.incrementAndGet();
    }

    /**
     * Generate Java source code for a JSSR component class.
     *
     * @param componentClass Component class implementing JssrComponent
     * @param className Simple name for the generated class
     * @return Full Java source code string
     */
    public String generateClassSource(Class<? extends JssrComponent> componentClass, String className) {
        StringBuilder sb = new StringBuilder();
        sb.append("package com.jssr.core.compiler.generated;\n\n");
        sb.append("import com.jssr.core.JssrComponent;\n");
        sb.append("import com.jssr.core.compiler.CompiledTemplateExecutable;\n");
        sb.append("import java.util.Map;\n");
        sb.append("import java.util.Collections;\n\n");
        sb.append("public final class ").append(className).append(" implements CompiledTemplateExecutable {\n");
        sb.append("    @Override\n");
        sb.append("    public void render(JssrComponent component, Map<String, Object> localScope, StringBuilder sb) {\n");
        sb.append("        if (component == null) return;\n");
        sb.append("        String rawHtml = component.template();\n");
        sb.append("        if (rawHtml == null || rawHtml.isBlank()) {\n");
        sb.append("            if (rawHtml != null) sb.append(rawHtml);\n");
        sb.append("            return;\n");
        sb.append("        }\n");
        sb.append("        Map<String, Object> scope = (localScope == null) ? Collections.emptyMap() : localScope;\n");
        sb.append("        String controlFlowProcessed = JssrComponent.processControlFlow(component, scope, rawHtml);\n");
        sb.append("        String interpolatedHtml = JssrComponent.interpolateVariables(component, scope, controlFlowProcessed);\n");
        sb.append("        String finalHtml = JssrComponent.processCustomTags(interpolatedHtml);\n");
        sb.append("        if (finalHtml != null) {\n");
        sb.append("            sb.append(finalHtml);\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Helper to attempt dummy instantiation of a record component to retrieve sample template.
     *
     * @param componentClass Component record class
     * @return Raw template string or null if instantiation fails
     */
    public static String tryExtractTemplate(Class<? extends JssrComponent> componentClass) {
        try {
            if (componentClass.isRecord()) {
                RecordComponent[] rcs = componentClass.getRecordComponents();
                Class<?>[] paramTypes = new Class<?>[rcs.length];
                Object[] dummyArgs = new Object[rcs.length];
                for (int i = 0; i < rcs.length; i++) {
                    paramTypes[i] = rcs[i].getType();
                    dummyArgs[i] = getDummyValue(rcs[i].getType());
                }
                Constructor<? extends JssrComponent> ctor = componentClass.getDeclaredConstructor(paramTypes);
                ctor.setAccessible(true);
                JssrComponent dummy = ctor.newInstance(dummyArgs);
                return dummy.template();
            } else {
                Constructor<? extends JssrComponent> ctor = componentClass.getDeclaredConstructor();
                ctor.setAccessible(true);
                JssrComponent dummy = ctor.newInstance();
                return dummy.template();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static Object getDummyValue(Class<?> type) {
        if (type == boolean.class || type == Boolean.class) return false;
        if (type == int.class || type == Integer.class) return 0;
        if (type == long.class || type == Long.class) return 0L;
        if (type == double.class || type == Double.class) return 0.0d;
        if (type == float.class || type == Float.class) return 0.0f;
        if (type == short.class || type == Short.class) return (short) 0;
        if (type == byte.class || type == Byte.class) return (byte) 0;
        if (type == char.class || type == Character.class) return '\0';
        if (type == String.class) return "";
        return null;
    }
}

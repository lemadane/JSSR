package com.jssr.core.compiler;

import com.jssr.core.JssrComponent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main entry point for JSSR precompilation and template bytecode cache management.
 * Modeled after PTE's (Piped Template Engine) precompiler and template cache.
 */
public final class JssrPrecompiler {

    private static final AtomicBoolean GLOBAL_PRECOMPILATION_ENABLED = new AtomicBoolean(false);
    private static final Map<Class<?>, CompiledTemplateExecutable> COMPILED_CACHE = new ConcurrentHashMap<>();

    private static final InMemoryBytecodeCompiler COMPILER = new InMemoryBytecodeCompiler();
    private static final JavaCodeGenerator CODE_GENERATOR = new JavaCodeGenerator();

    private JssrPrecompiler() {}

    /**
     * Enable or disable global precompiled JVM bytecode rendering for all JssrComponent.render() calls.
     *
     * @param enable true to enable precompiled rendering by default, false to use standard execution
     */
    public static void enableGlobalPrecompilation(boolean enable) {
        GLOBAL_PRECOMPILATION_ENABLED.set(enable);
    }

    /**
     * Check if global precompiled JVM bytecode rendering is enabled.
     *
     * @return true if enabled, false otherwise
     */
    public static boolean isGlobalPrecompilationEnabled() {
        return GLOBAL_PRECOMPILATION_ENABLED.get();
    }

    /**
     * Check if dynamic in-memory bytecode compilation is supported in the current environment.
     *
     * @return true if JavaCompiler is available, false otherwise
     */
    public static boolean isCompilerAvailable() {
        return InMemoryBytecodeCompiler.isAvailable();
    }

    /**
     * Compile or retrieve cached precompiled JVM bytecode executable for a component class.
     *
     * @param componentClass Component class implementing JssrComponent
     * @return CompiledTemplateExecutable instance
     */
    public static CompiledTemplateExecutable compile(Class<? extends JssrComponent> componentClass) {
        if (componentClass == null) {
            throw new IllegalArgumentException("Component class cannot be null");
        }

        return COMPILED_CACHE.computeIfAbsent(componentClass, clazz -> {
            if (!InMemoryBytecodeCompiler.isAvailable()) {
                return createFallbackExecutable();
            }

            try {
                @SuppressWarnings("unchecked")
                Class<? extends JssrComponent> jcClass = (Class<? extends JssrComponent>) clazz;
                String className = JavaCodeGenerator.generateUniqueClassName();
                String sourceCode = CODE_GENERATOR.generateClassSource(jcClass, className);

                Class<?> compiledClass = COMPILER.compile(className, sourceCode);
                return (CompiledTemplateExecutable) compiledClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                // Fallback to interpreted execution wrapper if compilation fails
                return createFallbackExecutable();
            }
        });
    }

    /**
     * Precompile a collection of JSSR component classes ahead of time.
     *
     * @param componentClasses Collection of component classes to precompile
     */
    public static void precompileAll(Collection<Class<? extends JssrComponent>> componentClasses) {
        if (componentClasses == null || componentClasses.isEmpty()) {
            return;
        }
        for (Class<? extends JssrComponent> clazz : componentClasses) {
            if (clazz != null) {
                compile(clazz);
            }
        }
    }

    /**
     * Clear all cached precompiled template executables.
     */
    public static void clearCache() {
        COMPILED_CACHE.clear();
    }

    /**
     * Execute precompiled rendering for a component instance.
     *
     * @param component Component instance
     * @return Rendered HTML string
     */
    public static String renderPrecompiled(JssrComponent component) {
        return renderPrecompiled(component, Collections.emptyMap());
    }

    /**
     * Execute precompiled rendering for a component instance with local scope.
     *
     * @param component Component instance
     * @param localScope Local scope map
     * @return Rendered HTML string
     */
    public static String renderPrecompiled(JssrComponent component, Map<String, Object> localScope) {
        if (component == null) {
            return "";
        }
        CompiledTemplateExecutable executable = compile(component.getClass());
        StringBuilder sb = new StringBuilder();
        executable.render(component, localScope, sb);
        return sb.toString();
    }

    private static CompiledTemplateExecutable createFallbackExecutable() {
        return (component, localScope, sb) -> {
            if (component == null) return;
            String rawHtml = component.template();
            if (rawHtml == null || rawHtml.isBlank()) {
                if (rawHtml != null) sb.append(rawHtml);
                return;
            }
            Map<String, Object> scope = (localScope == null) ? Collections.emptyMap() : localScope;
            String controlFlowProcessed = JssrComponent.processControlFlow(component, scope, rawHtml);
            String interpolatedHtml = JssrComponent.interpolateVariables(component, scope, controlFlowProcessed);
            String finalHtml = JssrComponent.processCustomTags(interpolatedHtml);
            if (finalHtml != null) {
                sb.append(finalHtml);
            }
        };
    }
}

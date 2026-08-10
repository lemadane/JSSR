package com.jssr.core.compiler;

import com.jssr.core.JssrComponent;
import java.lang.reflect.RecordComponent;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Main entry point for JSSR precompilation, failure observability, and template bytecode cache management.
 */
public final class JssrPrecompiler {

    private static final Logger LOGGER = System.getLogger(JssrPrecompiler.class.getName());

    private static final AtomicBoolean GLOBAL_PRECOMPILATION_ENABLED = new AtomicBoolean(false);
    private static final AtomicReference<CompilationFailureMode> FAILURE_MODE =
            new AtomicReference<>(CompilationFailureMode.WARN_AND_FALLBACK);

    private static final Map<Class<?>, CompiledTemplateExecutable> COMPILED_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, CompilationStatus> STATUS_MAP = new ConcurrentHashMap<>();

    private static final InMemoryBytecodeCompiler COMPILER = new InMemoryBytecodeCompiler();
    private static final JavaCodeGenerator CODE_GENERATOR = new JavaCodeGenerator();

    private JssrPrecompiler() {}

    /**
     * Set the compilation failure handling mode.
     *
     * @param mode Desired CompilationFailureMode strategy
     */
    public static void setFailureMode(CompilationFailureMode mode) {
        if (mode != null) {
            FAILURE_MODE.set(mode);
        }
    }

    /**
     * Get the current compilation failure handling mode.
     *
     * @return Current CompilationFailureMode
     */
    public static CompilationFailureMode getFailureMode() {
        return FAILURE_MODE.get();
    }

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
     * Get the compilation status for a component class.
     *
     * @param componentClass Component class implementing JssrComponent
     * @return Current CompilationStatus (NOT_COMPILED, COMPILED, FALLBACK, FAILED)
     */
    public static CompilationStatus status(Class<? extends JssrComponent> componentClass) {
        if (componentClass == null) {
            return CompilationStatus.NOT_COMPILED;
        }
        return STATUS_MAP.getOrDefault(componentClass, CompilationStatus.NOT_COMPILED);
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
            @SuppressWarnings("unchecked")
            Class<? extends JssrComponent> jcClass = (Class<? extends JssrComponent>) clazz;

            if (!InMemoryBytecodeCompiler.isAvailable()) {
                handleFailure(jcClass, "JavaCompiler tool is unavailable in current JVM environment.", null);
                STATUS_MAP.put(clazz, CompilationStatus.FALLBACK);
                return createFallbackExecutable();
            }

            try {
                if (jcClass.isRecord()) {
                    for (RecordComponent rc : jcClass.getRecordComponents()) {
                        if (JssrComponent.class.isAssignableFrom(rc.getType())) {
                            @SuppressWarnings("unchecked")
                            Class<? extends JssrComponent> childClass = (Class<? extends JssrComponent>) rc.getType();
                            compile(childClass);
                        }
                    }
                }

                String className = JavaCodeGenerator.generateUniqueClassName();
                String sourceCode = CODE_GENERATOR.generateClassSource(jcClass, className);

                Class<?> compiledClass = COMPILER.compile(className, sourceCode);
                CompiledTemplateExecutable executable =
                        (CompiledTemplateExecutable) compiledClass.getDeclaredConstructor().newInstance();

                STATUS_MAP.put(clazz, CompilationStatus.COMPILED);
                return executable;
            } catch (Exception e) {
                STATUS_MAP.put(clazz, CompilationStatus.FALLBACK);
                handleFailure(jcClass, "Failed to precompile component template bytecode.", e);
                return createFallbackExecutable();
            }
        });
    }

    /**
     * Precompile a collection of JSSR component classes ahead of time and generate a compilation report.
     *
     * @param componentClasses Collection of component classes to precompile
     * @return CompilationReport summarizing compilation diagnostics
     */
    public static CompilationReport precompileAll(Collection<Class<? extends JssrComponent>> componentClasses) {
        long startTime = System.currentTimeMillis();
        if (componentClasses == null || componentClasses.isEmpty()) {
            return new CompilationReport(0, 0, 0, 0, 0, Collections.emptyMap());
        }

        Map<Class<? extends JssrComponent>, CompilationStatus> details = new HashMap<>();
        int compiled = 0;
        int fallback = 0;
        int failed = 0;

        for (Class<? extends JssrComponent> clazz : componentClasses) {
            if (clazz != null) {
                compile(clazz);
                CompilationStatus st = status(clazz);
                details.put(clazz, st);
                switch (st) {
                    case COMPILED -> compiled++;
                    case FALLBACK -> fallback++;
                    case FAILED -> failed++;
                    default -> {}
                }
            }
        }

        long elapsedTime = System.currentTimeMillis() - startTime;
        return new CompilationReport(componentClasses.size(), compiled, fallback, failed, elapsedTime, details);
    }

    /**
     * Clear all cached precompiled template executables and reset status tracking.
     */
    public static void clearCache() {
        COMPILED_CACHE.clear();
        STATUS_MAP.clear();
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

    private static void handleFailure(Class<? extends JssrComponent> componentClass, String message, Throwable cause) {
        CompilationFailureMode mode = FAILURE_MODE.get();
        String fullMsg = "JSSR precompilation failure for " + componentClass.getName() + ": " + message;

        if (mode == CompilationFailureMode.FAIL_FAST) {
            STATUS_MAP.put(componentClass, CompilationStatus.FAILED);
            throw new IllegalStateException(fullMsg, cause);
        } else if (mode == CompilationFailureMode.WARN_AND_FALLBACK) {
            if (cause != null) {
                LOGGER.log(Level.WARNING, fullMsg + " Falling back to interpreted rendering.", cause);
            } else {
                LOGGER.log(Level.WARNING, fullMsg + " Falling back to interpreted rendering.");
            }
        }
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

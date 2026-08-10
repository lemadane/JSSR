package com.jssr.core.compiler;

/**
 * Status of precompiled JVM bytecode compilation for a JSSR component.
 */
public enum CompilationStatus {
    /**
     * Component has not been compiled yet.
     */
    NOT_COMPILED,

    /**
     * Component has been successfully compiled into JVM bytecode.
     */
    COMPILED,

    /**
     * Component compilation failed and fell back to interpreted rendering.
     */
    FALLBACK,

    /**
     * Component compilation failed in FAIL_FAST mode.
     */
    FAILED
}

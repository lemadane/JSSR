package com.jssr.core.compiler;

/**
 * Strategy for handling template precompilation failures.
 */
public enum CompilationFailureMode {

    /**
     * Fail immediately by throwing an IllegalStateException when compilation fails.
     */
    FAIL_FAST,

    /**
     * Log a warning via System.Logger and fall back to interpreted rendering (default).
     */
    WARN_AND_FALLBACK,

    /**
     * Silently fall back to interpreted rendering without logging warnings.
     */
    SILENT_FALLBACK
}

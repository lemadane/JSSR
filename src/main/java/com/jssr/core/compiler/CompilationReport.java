package com.jssr.core.compiler;

import com.jssr.core.JssrComponent;
import java.util.Collections;
import java.util.Map;

/**
 * Diagnostics report returned after precompiling a batch of JSSR components.
 */
public record CompilationReport(
    int totalDiscovered,
    int compiledCount,
    int fallbackCount,
    int failedCount,
    long elapsedTimeMs,
    Map<Class<? extends JssrComponent>, CompilationStatus> details
) {
    public CompilationReport {
        details = (details == null) ? Collections.emptyMap() : Collections.unmodifiableMap(details);
    }
}

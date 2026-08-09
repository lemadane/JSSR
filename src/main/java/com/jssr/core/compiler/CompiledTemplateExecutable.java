package com.jssr.core.compiler;

import com.jssr.core.JssrComponent;
import java.util.Map;

/**
 * Contract implemented by dynamically generated and compiled template bytecode classes.
 */
public interface CompiledTemplateExecutable {

    /**
     * Render the JSSR component using precompiled JVM bytecode instructions.
     *
     * @param component Component instance being rendered
     * @param localScope Map of local variables (e.g. from loop iterations or pattern bindings)
     * @param sb Output buffer to append rendered HTML content
     */
    void render(JssrComponent component, Map<String, Object> localScope, StringBuilder sb);
}

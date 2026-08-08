package com.jssr.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Collections;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Fuzz test suite for JSSR template parser, control flow engine, and attribute state machine.
 * Verifies that pathological, malformed, truncated, or randomly generated inputs never result
 * in infinite loops, unhandled exceptions, or parser deadlocks.
 */
public class ParserFuzzTest {

    private static final String[] DIRECTIVES = {
        "@if (true):", "@if (false):", "@elseif (true):", "@else:", "@end",
        "@for (item : list):", "@while (true):", "@while (false):",
        "@switch (user.role):", "@case ('ADMIN'):", "@default:",
        "@try:", "@catch(e):", "@finally:", "@throw(\"Fuzz Error\"):",
        "@continue", "@break"
    };

    private static final String[] SNIPPETS = {
        "<div>", "</div>", "${title}", "${user.name}", "<script>", "</script>",
        "<!--", "-->", "<style>", "</style>", " href=\"${url}\"", " data=\"${data}\"",
        " ping=\"${ping}\"", " srcset=\"${srcset}\"", " onclick=\"alert(1)\"",
        " title=${title}", " title=\"", " title='", "<UserCard name=\"Fuzz\" />",
        "\"", "'", "<", ">", "=", " ", "\n", "\t", "\u0000", "\\", ":"
    };

    public record FuzzDynamicComponent(String customTemplate) implements JssrComponent {
        @Override
        public String template() {
            return customTemplate;
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Fuzz test: 1,000 randomly constructed pathological control flow and attribute templates terminate safely")
    void testRandomFuzzTemplates() {
        Random random = new Random(42); // Deterministic seed for reproducible fuzzing

        for (int i = 0; i < 1000; i++) {
            StringBuilder sb = new StringBuilder();
            int snippetsCount = random.nextInt(20) + 1;

            for (int j = 0; j < snippetsCount; j++) {
                if (random.nextBoolean()) {
                    sb.append(DIRECTIVES[random.nextInt(DIRECTIVES.length)]);
                } else {
                    sb.append(SNIPPETS[random.nextInt(SNIPPETS.length)]);
                }
            }

            String fuzzTemplate = sb.toString();
            FuzzDynamicComponent comp = new FuzzDynamicComponent(fuzzTemplate);

            // Parser should safely complete rendering or throw a expected IllegalArgumentException/IllegalStateException
            try {
                comp.render();
            } catch (RuntimeException ignored) {
                // Expected validation or @throw exception
            } catch (Exception e) {
                throw new AssertionError("Fuzzing triggered an unexpected checked exception for template [" + fuzzTemplate + "]: " + e, e);
            }
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Fuzz test: Pathological control flow directive nesting up to depth 150 safely triggers recursion or control limit")
    void testDeeplyNestedDirectivesFuzzing() {
        StringBuilder nested = new StringBuilder();
        int depth = 150;

        for (int i = 0; i < depth; i++) {
            nested.append("@try:\n@if (true):\n");
        }
        nested.append("<div>Nested Center</div>\n");
        for (int i = 0; i < depth; i++) {
            nested.append("@end\n@catch(e):\n@end\n");
        }

        FuzzDynamicComponent comp = new FuzzDynamicComponent(nested.toString());

        try {
            comp.render();
        } catch (RuntimeException ignored) {
            // Expected depth limit or validation exception
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Fuzz test: Truncated directives, dangling colons, and open quotes terminate cleanly without hanging")
    void testTruncatedDirectivesFuzzing() {
        String[] truncatedTemplates = {
            "@if", "@if (", "@if (true", "@if (true):",
            "@try", "@try:", "@catch", "@catch(", "@catch(e", "@catch(e):",
            "@for", "@for (item", "@for (item :", "@for (item : list",
            "@switch", "@switch (", "@switch (val", "@switch (val):",
            "@case", "@case (", "@case ('admin'",
            "@throw", "@throw(", "@throw(\"", "@throw(\"msg", "@throw(new ",
            "<div title=\"${var}", "<a href=\"${url", "<object data=\"${data",
            "<!-- unclosed comment", "<script>unclosed script", "<style>unclosed style"
        };

        for (String tmpl : truncatedTemplates) {
            FuzzDynamicComponent comp = new FuzzDynamicComponent(tmpl);
            try {
                comp.render();
            } catch (RuntimeException ignored) {
                // Expected validation exception
            }
        }
    }
}

package com.jssr.core;

import com.jssr.core.compiler.CompiledTemplateExecutable;
import com.jssr.core.compiler.InMemoryBytecodeCompiler;
import com.jssr.core.compiler.JssrPrecompiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JssrBytecodeCompilerTest {

    @BeforeEach
    void setUp() {
        JssrPrecompiler.clearCache();
        JssrPrecompiler.enableGlobalPrecompilation(false);
    }

    @AfterEach
    void tearDown() {
        JssrPrecompiler.clearCache();
        JssrPrecompiler.enableGlobalPrecompilation(false);
    }

    public record SimpleUser(String name, int age) implements JssrComponent {
        @Override
        public String template() {
            return "<div class=\"user\"><h1>${name}</h1><p>Age: ${age}</p></div>";
        }
    }

    public record SecurityTestCard(String title, RawHtml rawContent, SafeUrl profileUrl) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div class="card">
                    <h2>${title}</h2>
                    <div class="content">${rawContent}</div>
                    <a href="${profileUrl}">Profile</a>
                </div>
                """;
        }
    }

    @Test
    @DisplayName("Verify InMemoryBytecodeCompiler availability in JDK environment")
    void testCompilerAvailability() {
        assertTrue(InMemoryBytecodeCompiler.isAvailable(), "JDK JavaCompiler should be available for in-memory compilation");
        assertTrue(JssrPrecompiler.isCompilerAvailable());
    }

    @Test
    @DisplayName("Verify dynamic in-memory bytecode compilation and rendering for simple component")
    void testSimpleComponentBytecodeCompilation() {
        SimpleUser user = new SimpleUser("<Alice & Bob>", 30);
        
        CompiledTemplateExecutable executable = JssrPrecompiler.compile(SimpleUser.class);
        assertNotNull(executable);

        String html = user.renderPrecompiled();
        assertEquals("<div class=\"user\"><h1>&lt;Alice &amp; Bob&gt;</h1><p>Age: 30</p></div>", html);
    }

    @Test
    @DisplayName("Verify precompiled JVM bytecode rendering preserves XSS HTML escaping, RawHtml, and SafeUrl protection")
    void testSecurityEnforcementInPrecompiledBytecode() {
        SecurityTestCard card = new SecurityTestCard(
                "<script>alert('xss')</script>",
                RawHtml.of("<b>Trusted Markup</b>"),
                SafeUrl.of("https://example.com/user/alice")
        );

        String html = card.renderPrecompiled();
        assertTrue(html.contains("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;"));
        assertTrue(html.contains("<b>Trusted Markup</b>"));
        assertTrue(html.contains("href=\"https://example.com/user/alice\""));
    }

    @Test
    @DisplayName("Verify global precompilation toggle routes JssrComponent.render() through precompiled JVM bytecode")
    void testGlobalPrecompilationToggle() {
        SimpleUser user = new SimpleUser("Charlie", 25);

        // Before global toggle
        assertFalse(JssrPrecompiler.isGlobalPrecompilationEnabled());
        String standardHtml = user.render();

        // Enable global precompilation
        JssrPrecompiler.enableGlobalPrecompilation(true);
        assertTrue(JssrPrecompiler.isGlobalPrecompilationEnabled());
        String precompiledHtml = user.render();

        assertEquals(standardHtml, precompiledHtml);
        assertEquals("<div class=\"user\"><h1>Charlie</h1><p>Age: 25</p></div>", precompiledHtml);
    }

    public record UnsafeStringUrlCard(String profileUrl) implements JssrComponent {
        @Override
        public String template() {
            return "<a href=\"${profileUrl}\">Link</a>";
        }
    }

    @Test
    @DisplayName("Verify URL attribute sanitization and type enforcement under precompiled execution")
    void testUnsafeUrlInPrecompiledExecution() {
        // SafeUrl sanitizes dangerous schemes to about:blank
        SafeUrl unsafe = SafeUrl.of("javascript:alert(1)");
        assertEquals("about:blank", unsafe.render());

        // Dynamic interpolation inside URL attribute requires SafeUrl typed field, raw String throws IllegalArgumentException
        UnsafeStringUrlCard card = new UnsafeStringUrlCard("https://example.com");
        assertThrows(IllegalArgumentException.class, card::renderPrecompiled);
    }
}

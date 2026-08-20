package com.jssr.e2e;

import com.jssr.core.compiler.CompilationStatus;
import com.jssr.core.compiler.JssrPrecompiler;
import com.jssr.e2e.app.TestApplication;
import com.jssr.e2e.app.components.UserForm;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = TestApplication.class)
class ExecutableJarTest {

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

    @Test
    @DisplayName("Verify Spring Boot component precompilation returns COMPILED status without falling back")
    void verifySpringBootPrecompilationStatus() {
        JssrPrecompiler.clearCache();
        JssrPrecompiler.enableGlobalPrecompilation(true);
        try {
            UserForm form = new UserForm(1L, "Lemuel", "lem@example.com", "Admin", "ACTIVE", true);
            String rendered = form.renderPrecompiled();

            assertNotNull(rendered);
            assertTrue(rendered.contains("Lemuel"));
            assertTrue(rendered.contains("Admin"));

            // Assert JssrPrecompiler status is strictly COMPILED (not FALLBACK or NOT_COMPILED)
            CompilationStatus status = JssrPrecompiler.status(UserForm.class);
            assertEquals(CompilationStatus.COMPILED, status, "JSSR component under Spring Boot container must be COMPILED");
        } finally {
            JssrPrecompiler.enableGlobalPrecompilation(false);
        }
    }
}

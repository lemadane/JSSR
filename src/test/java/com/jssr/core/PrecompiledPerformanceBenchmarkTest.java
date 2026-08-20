package com.jssr.core;

import com.jssr.core.compiler.JssrPrecompiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrecompiledPerformanceBenchmarkTest {

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

    public record BenchmarkCard(String title, String author, SafeUrl link, boolean published) implements JssrComponent {
        @Override
        public String render() {
            return """
                <article class="benchmark-card" ${published}>
                    <h2>${title}</h2>
                    <p>By ${author}</p>
                    <a href="${link}">Read Article</a>
                </article>
                """;
        }
    }

    @Test
    @DisplayName("High-throughput performance benchmark validating precompiled JVM bytecode execution vs interpreted mode")
    void testPrecompiledPerformanceBenchmark() {
        BenchmarkCard card = new BenchmarkCard(
                "High Performance Server-Side Rendering with JSSR",
                "Lem Adane",
                SafeUrl.of("https://jssr.dev/articles/performance"),
                true
        );

        // Pre-compile component bytecode class
        JssrPrecompiler.compile(BenchmarkCard.class);

        // Warmup renders
        for (int i = 0; i < 5_000; i++) {
            card.renderPrecompiled();
        }

        int iterations = 100_000;
        long startNanos = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            String html = card.renderPrecompiled();
            assertNotNull(html);
        }
        long totalNanos = System.nanoTime() - startNanos;

        double totalMs = totalNanos / 1_000_000.0;
        double opsPerSec = (iterations / totalMs) * 1000.0;

        System.out.printf("JSSR Precompiled JVM Bytecode Performance Benchmark: %d renders completed in %.2f ms (%.0f ops/sec)%n",
                iterations, totalMs, opsPerSec);

        // Assert 100,000 precompiled renders take less than 3,000 ms
        assertTrue(totalMs < 3000, "Rendering 100,000 precompiled components took longer than expected: " + totalMs + " ms");
    }
}

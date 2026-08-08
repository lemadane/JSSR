package com.jssr.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceBenchmarkTest {

    public record UserCard(String name, String role, SafeUrl profileUrl, boolean active) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div class="user-card" ${active}>
                    <h2>${name}</h2>
                    <p>${role}</p>
                    <a href="${profileUrl}">Profile</a>
                </div>
                """;
        }
    }

    @Test
    @DisplayName("High-throughput rendering benchmark validating reflection metadata cache speed")
    void testHighThroughputRenderPerformance() {
        UserCard card = new UserCard("Alice Smith", "Senior Engineer", SafeUrl.of("/users/alice"), true);

        // Warm up cache
        for (int i = 0; i < 1_000; i++) {
            card.render();
        }

        long startNanos = System.nanoTime();
        int iterations = 100_000;
        for (int i = 0; i < iterations; i++) {
            String html = card.render();
            assertNotNull(html);
        }
        long totalNanos = System.nanoTime() - startNanos;

        double totalMs = totalNanos / 1_000_000.0;
        double opsPerSec = (iterations / totalMs) * 1000.0;

        System.out.printf("JSSR Performance Benchmark: %d renders completed in %.2f ms (%.0f ops/sec)%n",
                iterations, totalMs, opsPerSec);

        // Assert 100,000 renders take less than 3,000 ms (high performance execution threshold)
        assertTrue(totalMs < 3000, "Rendering 100,000 components took longer than expected: " + totalMs + " ms");
    }
}

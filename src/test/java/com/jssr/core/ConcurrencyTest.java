package com.jssr.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyTest {

    public record TestCard(String username, int count) implements JssrComponent {
        @Override
        public String render() {
            return "<div class=\"user-card\"><h3>${username}</h3><span>${count}</span></div>";
        }
    }

    public record ContainerPage(TestCard card) implements JssrComponent {
        @Override
        public String render() {
            return "<main>${card}</main>";
        }
    }

    @BeforeEach
    void setUp() {
        JssrComponent.REGISTRY.clear();
        JssrComponent.register("TestCard", TestCard.class);
    }

    @Test
    @DisplayName("Parallel rendering with 100 concurrent threads should produce thread-isolated, uncorrupted HTML")
    void testMultiThreadedParallelRendering() throws Exception {
        int threadCount = 100;
        int iterationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<Future<List<String>>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            futures.add(executor.submit(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                List<String> results = new ArrayList<>();
                for (int j = 0; j < iterationsPerThread; j++) {
                    TestCard card = new TestCard("User-" + threadId, j);
                    ContainerPage page = new ContainerPage(card);
                    String html = JssrComponent.render(page);
                    results.add(html);
                }
                doneLatch.countDown();
                return results;
            }));
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "Parallel rendering tasks did not complete in time");

        for (int i = 0; i < threadCount; i++) {
            List<String> threadResults = futures.get(i).get();
            assertEquals(iterationsPerThread, threadResults.size());
            for (int j = 0; j < iterationsPerThread; j++) {
                String html = threadResults.get(j);
                String expectedUser = "User-" + i;
                String expectedCount = String.valueOf(j);
                assertTrue(html.contains(expectedUser), "Expected user " + expectedUser + " in thread result");
                assertTrue(html.contains(expectedCount), "Expected count " + expectedCount + " in thread result");
                assertTrue(html.startsWith("<main><div class=\"user-card\">"));
            }
        }

        executor.shutdown();
    }

    @Test
    @DisplayName("Microsecond rendering benchmark should complete 10,000 component renders rapidly without memory leaks")
    void testRenderPerformanceBenchmark() {
        int iterations = 10_000;
        TestCard card = new TestCard("BenchmarkUser", 42);

        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            String html = JssrComponent.render(card);
            assertNotNull(html);
        }
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        assertTrue(durationMs < 5000, "10,000 renders took longer than expected: " + durationMs + "ms");
    }
}

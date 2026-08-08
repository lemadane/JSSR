package com.jssr.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class WhileLoopTest {

    public record SimpleCounterCursor(AtomicInteger count, int max) {
        public boolean hasMore() {
            return count.getAndIncrement() < max;
        }
    }

    public record SimpleWhileComp(SimpleCounterCursor cursor) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div class="counter">
                @while (cursor.hasMore)
                    <span class="step">Step #${cursor.count}</span>
                @end
                </div>
                """;
        }
    }

    public record ItemRecord(String name, boolean skip, boolean stop) {}

    public record ContinueBreakComp(List<ItemRecord> items) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div class="items">
                @for (item : items)
                    @if (item.skip)
                        @continue
                    @end
                    @if (item.stop)
                        @break
                    @end
                    <div class="item-name">${item.name}</div>
                @end
                </div>
                """;
        }
    }

    public record InfiniteWhileComp() implements JssrComponent {
        public boolean alwaysTrue() {
            return true;
        }

        @Override
        public String template() {
            return """
                @while (alwaysTrue)
                    <div>Infinite loop test</div>
                @end
                """;
        }
    }

    @Test
    @DisplayName("@while (condition) iterates until condition evaluates false")
    void testSimpleWhileLoop() {
        SimpleWhileComp comp = new SimpleWhileComp(new SimpleCounterCursor(new AtomicInteger(0), 3));
        String html = comp.render();

        assertTrue(html.contains("Step #1"));
        assertTrue(html.contains("Step #2"));
        assertTrue(html.contains("Step #3"));
        assertFalse(html.contains("Step #4"));
    }

    @Test
    @DisplayName("@continue skips current iteration and @break terminates loop early")
    void testContinueAndBreakInForLoop() {
        List<ItemRecord> items = List.of(
            new ItemRecord("Item 1", false, false),
            new ItemRecord("Item 2 (Ignored)", true, false),
            new ItemRecord("Item 3", false, false),
            new ItemRecord("Item 4 (Stop)", false, true),
            new ItemRecord("Item 5 (Unreachable)", false, false)
        );

        ContinueBreakComp comp = new ContinueBreakComp(items);
        String html = comp.render();

        assertTrue(html.contains("Item 1"));
        assertFalse(html.contains("Item 2 (Ignored)")); // skipped via @continue
        assertTrue(html.contains("Item 3"));
        assertFalse(html.contains("Item 4 (Stop)")); // terminated via @break
        assertFalse(html.contains("Item 5 (Unreachable)")); // after @break
    }

    @Test
    @DisplayName("Infinite @while loop throws IllegalStateException when MAX_WHILE_ITERATIONS exceeded")
    void testInfiniteWhileLoopSafetyGuard() {
        InfiniteWhileComp comp = new InfiniteWhileComp();
        Exception ex = assertThrows(IllegalStateException.class, comp::render);
        assertTrue(ex.getMessage().contains("JSSR @while loop iteration limit exceeded (max 1000 iterations)"));
    }
}

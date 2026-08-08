package com.jssr.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class ForLoopTest {

    public record SimpleStringLoopComp(List<String> items) implements JssrComponent {
        @Override
        public String template() {
            return """
                <ul>
                @for (item : items)
                    <li>${item}</li>
                @end
                </ul>
                """;
        }
    }

    public record ItemRecord(String name, String category, int price, boolean active) {}

    public record ItemLoopComp(List<ItemRecord> items) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div class="item-list">
                @for (item : items)
                    <div class="item-card">
                        <h3>${item.name}</h3>
                        @if (item.category == 'ELECTRONICS')
                            <span class="badge-tech">Tech</span>
                        @elseif (item.category == 'BOOKS')
                            <span class="badge-book">Book</span>
                        @else
                            <span class="badge-other">Other</span>
                        @end

                        @if (item.active)
                            <span class="status-active">Available</span>
                        @else
                            <span class="status-out">Out of Stock</span>
                        @end
                    </div>
                @else
                    <div class="empty-state">No items available.</div>
                @end
                </div>
                """;
        }
    }

    public record Department(String name, List<String> employees) {}

    public record NestedForComp(List<Department> departments) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div class="org-chart">
                @for (dept : departments)
                    <div class="dept">
                        <h2>Department: ${dept.name}</h2>
                        <ul>
                        @for (emp : dept.employees)
                            <li>${emp} (${dept.name})</li>
                        @else
                            <li class="no-emp">No employees</li>
                        @end
                        </ul>
                    </div>
                @end
                </div>
                """;
        }
    }

    public record OptionalLoopComp(Optional<List<String>> tags) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div class="tags">
                @for (tag : tags)
                    <span class="tag">#${tag}</span>
                @else
                    <span class="no-tags">No tags</span>
                @end
                </div>
                """;
        }
    }

    @Test
    @DisplayName("@for (item : items) iterates collection cleanly")
    void testSimpleStringLoop() {
        SimpleStringLoopComp comp = new SimpleStringLoopComp(List.of("Apple", "Banana", "Cherry"));
        String html = comp.render();

        assertTrue(html.contains("<li>Apple</li>"));
        assertTrue(html.contains("<li>Banana</li>"));
        assertTrue(html.contains("<li>Cherry</li>"));
    }

    @Test
    @DisplayName("@for with nested @if / @elseif / @else and @else fallback branch")
    void testItemLoopWithNestedIfAndElseFallback() {
        ItemLoopComp filledComp = new ItemLoopComp(List.of(
            new ItemRecord("Laptop", "ELECTRONICS", 1200, true),
            new ItemRecord("Clean Code", "BOOKS", 45, true),
            new ItemRecord("Coffee Mug", "HOME", 15, false)
        ));
        String htmlFilled = filledComp.render();

        assertTrue(htmlFilled.contains("<h3>Laptop</h3>"));
        assertTrue(htmlFilled.contains("badge-tech"));
        assertTrue(htmlFilled.contains("status-active"));

        assertTrue(htmlFilled.contains("<h3>Clean Code</h3>"));
        assertTrue(htmlFilled.contains("badge-book"));

        assertTrue(htmlFilled.contains("<h3>Coffee Mug</h3>"));
        assertTrue(htmlFilled.contains("badge-other"));
        assertTrue(htmlFilled.contains("status-out"));
        assertFalse(htmlFilled.contains("No items available"));

        // Test Empty List @else fallback
        ItemLoopComp emptyComp = new ItemLoopComp(List.of());
        String htmlEmpty = emptyComp.render();
        assertTrue(htmlEmpty.contains("No items available."));
        assertFalse(htmlEmpty.contains("item-card"));
    }

    @Test
    @DisplayName("Nested @for directives inside outer @for directives render correctly")
    void testNestedForLoops() {
        NestedForComp comp = new NestedForComp(List.of(
            new Department("Engineering", List.of("Alice", "Bob")),
            new Department("Marketing", List.of()),
            new Department("Design", List.of("Charlie"))
        ));
        String html = comp.render();

        assertTrue(html.contains("Department: Engineering"));
        assertTrue(html.contains("<li>Alice (Engineering)</li>"));
        assertTrue(html.contains("<li>Bob (Engineering)</li>"));

        assertTrue(html.contains("Department: Marketing"));
        assertTrue(html.contains("<li class=\"no-emp\">No employees</li>"));

        assertTrue(html.contains("Department: Design"));
        assertTrue(html.contains("<li>Charlie (Design)</li>"));
    }

    @Test
    @DisplayName("@for loop handles Optional<Collection<?>> correctly")
    void testOptionalCollectionLoop() {
        OptionalLoopComp presentComp = new OptionalLoopComp(Optional.of(List.of("java", "jssr")));
        String htmlPresent = presentComp.render();
        assertTrue(htmlPresent.contains("#java"));
        assertTrue(htmlPresent.contains("#jssr"));

        OptionalLoopComp emptyComp = new OptionalLoopComp(Optional.empty());
        String htmlEmpty = emptyComp.render();
        assertTrue(htmlEmpty.contains("No tags"));
    }
}

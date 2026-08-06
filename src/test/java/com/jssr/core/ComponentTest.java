package com.jssr.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComponentTest {

    public record CardComponent(String title, int count, boolean active) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div class="card" hx-get="/card" x-data="{ open: true }">
                    <h2>%s</h2>
                    <span>Count: %d</span>
                    <span>Status: %s</span>
                </div>
                """.formatted(title, count, active ? "ACTIVE" : "INACTIVE");
        }
    }

    public record ContainerComponent() implements JssrComponent {
        static {
            JssrComponent.register("CardComponent", CardComponent.class);
        }

        @Override
        public String template() {
            return """
                <main>
                    <CardComponent title="Users" count="42" active="true" />
                    <CardComponent title="Tasks" count="10" active="false" />
                </main>
                """;
        }
    }

    @Test
    @DisplayName("Single Record-based JssrComponent template & render should return formatted HTML text block")
    void testSingleComponentRender() {
        CardComponent card = new CardComponent("Dashboard", 5, true);
        String html = card.render();

        assertTrue(html.contains("hx-get=\"/card\""));
        assertTrue(html.contains("x-data=\"{ open: true }\""));
        assertTrue(html.contains("<h2>Dashboard</h2>"));
        assertTrue(html.contains("Count: 5"));
        assertTrue(html.contains("Status: ACTIVE"));
    }

    @Test
    @DisplayName("Parent JssrComponent render() should automatically process custom child tags without manual processCustomTags call")
    void testCustomTagParsing() {
        ContainerComponent container = new ContainerComponent();
        String html = container.render();

        assertFalse(html.contains("<CardComponent"));
        assertTrue(html.contains("<h2>Users</h2>"));
        assertTrue(html.contains("Count: 42"));
        assertTrue(html.contains("Status: ACTIVE"));

        assertTrue(html.contains("<h2>Tasks</h2>"));
        assertTrue(html.contains("Count: 10"));
        assertTrue(html.contains("Status: INACTIVE"));
    }
}

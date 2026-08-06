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
                    <h2>${title}</h2>
                    <span>Count: ${count}</span>
                    <span>Status: ${active}</span>
                </div>
                """;
        }
    }

    public record ContainerComponent(String appName) implements JssrComponent {
        static {
            JssrComponent.register("CardComponent", CardComponent.class);
        }

        @Override
        public String template() {
            return """
                <main>
                    <h1>${appName}</h1>
                    <CardComponent title="Users" count="42" active="true" />
                    <CardComponent title="Tasks" count="10" active="false" />
                </main>
                """;
        }
    }

    @Test
    @DisplayName("Single Record-based JssrComponent should interpolate ${fieldName} variables")
    void testVariableInterpolation() {
        CardComponent card = new CardComponent("Dashboard", 5, true);
        String html = card.render();

        assertTrue(html.contains("hx-get=\"/card\""));
        assertTrue(html.contains("x-data=\"{ open: true }\""));
        assertTrue(html.contains("<h2>Dashboard</h2>"));
        assertTrue(html.contains("Count: 5"));
        assertTrue(html.contains("Status: true"));
    }

    @Test
    @DisplayName("Parent JssrComponent render() should interpolate parent ${appName} and resolve custom child tags")
    void testCustomTagParsingAndInterpolation() {
        ContainerComponent container = new ContainerComponent("JSSR Control Panel");
        String html = container.render();

        assertTrue(html.contains("<h1>JSSR Control Panel</h1>"));
        assertFalse(html.contains("<CardComponent"));
        assertTrue(html.contains("<h2>Users</h2>"));
        assertTrue(html.contains("Count: 42"));
        assertTrue(html.contains("Status: true"));

        assertTrue(html.contains("<h2>Tasks</h2>"));
        assertTrue(html.contains("Count: 10"));
        assertTrue(html.contains("Status: false"));
    }
}

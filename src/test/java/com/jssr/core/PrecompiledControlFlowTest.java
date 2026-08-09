package com.jssr.core;

import com.jssr.core.compiler.JssrPrecompiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrecompiledControlFlowTest {

    @BeforeEach
    void setUp() {
        JssrPrecompiler.clearCache();
        JssrPrecompiler.enableGlobalPrecompilation(true);
    }

    @AfterEach
    void tearDown() {
        JssrPrecompiler.clearCache();
        JssrPrecompiler.enableGlobalPrecompilation(false);
    }

    public record AdminUser(String name, String role) {}
    public record RegularUser(String name) {}

    public record UserBadgeCard(Object user) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div class="badge">
                    @if (user instanceof com.jssr.core.PrecompiledControlFlowTest$AdminUser admin):
                        <span class="admin">Admin: ${admin.name} (${admin.role})</span>
                    @elseif (user instanceof com.jssr.core.PrecompiledControlFlowTest$RegularUser reg):
                        <span class="user">User: ${reg.name}</span>
                    @else:
                        <span class="guest">Guest</span>
                    @end
                </div>
                """;
        }
    }

    public record TaskListCard(List<String> tasks) implements JssrComponent {
        @Override
        public String template() {
            return """
                <ul>
                    @for (t : tasks):
                        <li>${t}</li>
                    @else:
                        <li class="empty">No tasks</li>
                    @end
                </ul>
                """;
        }
    }

    public record LoopControlCard(List<Integer> numbers) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div>
                    @for (n : numbers):
                        @if (n < 0):
                            @continue
                        @end
                        @if (n > 10):
                            @break
                        @end
                        <span>${n}</span>
                    @end
                </div>
                """;
        }
    }

    public record ErrorBoundaryCard(boolean triggerFault) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div>
                    @try:
                        @if (triggerFault):
                            @throw("Intentional template error")
                        @else:
                            <p>All normal</p>
                        @end
                    @catch(err):
                        <p class="error">Caught: ${err.message}</p>
                    @finally:
                        <p class="footer">Done</p>
                    @end
                </div>
                """;
        }
    }

    @Test
    @DisplayName("Verify @if instanceof pattern matching under precompiled JVM bytecode rendering")
    void testPrecompiledIfInstanceof() {
        UserBadgeCard adminCard = new UserBadgeCard(new AdminUser("Alice", "SuperAdmin"));
        String adminHtml = adminCard.render();
        assertTrue(adminHtml.contains("Admin: Alice (SuperAdmin)"));

        UserBadgeCard userCard = new UserBadgeCard(new RegularUser("Bob"));
        String userHtml = userCard.render();
        assertTrue(userHtml.contains("User: Bob"));

        UserBadgeCard guestCard = new UserBadgeCard("UnknownGuest");
        String guestHtml = guestCard.render();
        assertTrue(guestHtml.contains("Guest"));
    }

    @Test
    @DisplayName("Verify @for loop iteration and @else fallbacks under precompiled JVM bytecode rendering")
    void testPrecompiledForLoop() {
        TaskListCard activeTasks = new TaskListCard(List.of("Deploy App", "Run Security Scan"));
        String activeHtml = activeTasks.render();
        assertTrue(activeHtml.contains("<li>Deploy App</li>"));
        assertTrue(activeHtml.contains("<li>Run Security Scan</li>"));

        TaskListCard emptyTasks = new TaskListCard(List.of());
        String emptyHtml = emptyTasks.render();
        assertTrue(emptyHtml.contains("<li class=\"empty\">No tasks</li>"));
    }

    @Test
    @DisplayName("Verify @continue and @break directives under precompiled JVM bytecode rendering")
    void testPrecompiledLoopControlDirectives() {
        LoopControlCard card = new LoopControlCard(List.of(-1, 2, -5, 4, 15, 6));
        String html = card.render();
        assertFalse(html.contains("<span>-1</span>"));
        assertTrue(html.contains("<span>2</span>"));
        assertFalse(html.contains("<span>-5</span>"));
        assertTrue(html.contains("<span>4</span>"));
        assertFalse(html.contains("<span>15</span>"));
        assertFalse(html.contains("<span>6</span>"));
    }

    @Test
    @DisplayName("Verify @try/@catch/@finally error boundaries under precompiled JVM bytecode rendering")
    void testPrecompiledErrorBoundary() {
        ErrorBoundaryCard normal = new ErrorBoundaryCard(false);
        String normalHtml = normal.render();
        assertTrue(normalHtml.contains("<p>All normal</p>"));
        assertTrue(normalHtml.contains("<p class=\"footer\">Done</p>"));

        ErrorBoundaryCard fault = new ErrorBoundaryCard(true);
        String faultHtml = fault.render();
        assertTrue(faultHtml.contains("Caught: Intentional template error"));
        assertTrue(faultHtml.contains("<p class=\"footer\">Done</p>"));
    }
}

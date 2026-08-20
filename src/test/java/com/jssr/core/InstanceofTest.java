package com.jssr.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class InstanceofTest {

    public record AdminUser(String name, String permissions) {}
    public record DeveloperUser(String name, String githubHandle, String primaryLanguage) {}
    public record StandardUser(String name, String planType) {}

    public record PatternMatchComp(Object user) implements JssrComponent {
        @Override
        public String render() {
            return """
                <div class="user-card">
                @if (user instanceof AdminUser admin) {
                    <span class="badge-admin">Admin: ${admin.name} (${admin.permissions})</span>
                } @elseif (user instanceof DeveloperUser dev) {
                    <span class="badge-dev">Dev: ${dev.name} (${dev.githubHandle} - ${dev.primaryLanguage})</span>
                } @elseif (user instanceof StandardUser sUser) {
                    <span class="badge-user">User: ${sUser.name} (${sUser.planType})</span>
                } @else {
                    <span class="badge-guest">Guest</span>
                }
                </div>
                """;
        }
    }

    public record PatternMatchInForComp(List<Object> users) implements JssrComponent {
        @Override
        public String render() {
            return """
                <div class="user-list">
                @for (u : users) {
                    @if (u instanceof AdminUser a) {
                        <div class="admin-item">Admin ${a.name}</div>
                    } @elseif (u instanceof StandardUser s) {
                        <div class="user-item">User ${s.name}</div>
                    }
                }
                </div>
                """;
        }
    }

    @Test
    @DisplayName("@if (user instanceof Type varName) matches type and binds pattern variable")
    void testInstanceofPatternMatchingAndVariableBinding() {
        PatternMatchComp adminComp = new PatternMatchComp(new AdminUser("Elena", "ALL_PERMISSIONS"));
        String adminHtml = JssrComponent.render(adminComp);
        assertTrue(adminHtml.contains("badge-admin"));
        assertTrue(adminHtml.contains("Admin: Elena (ALL_PERMISSIONS)"));

        PatternMatchComp devComp = new PatternMatchComp(new DeveloperUser("Marcus", "@mvance", "Java"));
        String devHtml = JssrComponent.render(devComp);
        assertTrue(devHtml.contains("badge-dev"));
        assertTrue(devHtml.contains("Dev: Marcus (@mvance - Java)"));

        PatternMatchComp userComp = new PatternMatchComp(new StandardUser("Sophia", "PRO"));
        String userHtml = JssrComponent.render(userComp);
        assertTrue(userHtml.contains("badge-user"));
        assertTrue(userHtml.contains("User: Sophia (PRO)"));

        PatternMatchComp guestComp = new PatternMatchComp(null);
        String guestHtml = JssrComponent.render(guestComp);
        assertTrue(guestHtml.contains("badge-guest"));
        assertTrue(guestHtml.contains("Guest"));
    }

    @Test
    @DisplayName("Pattern matching instanceof works seamlessly inside @for loops")
    void testInstanceofPatternMatchingInsideForLoop() {
        PatternMatchInForComp comp = new PatternMatchInForComp(List.of(
            new AdminUser("Alice", "SUPERUSER"),
            new StandardUser("Bob", "BASIC")
        ));

        String html = JssrComponent.render(comp);
        assertTrue(html.contains("admin-item"));
        assertTrue(html.contains("Admin Alice"));
        assertTrue(html.contains("user-item"));
        assertTrue(html.contains("User Bob"));
    }
}

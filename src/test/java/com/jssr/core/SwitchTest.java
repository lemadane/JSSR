package com.jssr.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SwitchTest {

    public record AdminUser(String name) {}
    public record DeveloperUser(String name, String githubHandle) {}
    public record StandardUser(String name) {}

    public record PolymorphicUserComp(Object user) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div class="user-card">
                @switch (typeof(user))
                    @case ('AdminUser')
                        <span class="badge-admin">Admin: ${user.name}</span>
                        @break
                    @case ('DeveloperUser')
                        <span class="badge-dev">Developer: ${user.name} (${user.githubHandle})</span>
                        @break
                    @case ('StandardUser')
                        <span class="badge-user">User: ${user.name}</span>
                        @break
                    @default
                        <span class="badge-guest">Guest User</span>
                        @break
                @end
                </div>
                """;
        }
    }

    public record StatusSwitchComp(String status) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div class="status-box">
                @switch (status)
                    @case ('ACTIVE')
                        <span class="status-active font-bold">Account Active</span>
                        @break
                    @case ('PENDING')
                        <span class="status-pending">Verification Pending</span>
                        @break
                    @case ('SUSPENDED')
                        <span class="status-suspended text-rose-500">Account Suspended</span>
                        @break
                    @default
                        <span class="status-unknown">Status Unknown</span>
                        @break
                @end
                </div>
                """;
        }
    }

    public record NestedSwitchComp(List<Object> users) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div class="user-list">
                @for (u : users)
                    @switch (typeof(u))
                        @case ('AdminUser')
                            <div class="role-admin">Admin ${u.name}</div>
                            @break
                        @case ('StandardUser')
                            <div class="role-user">User ${u.name}</div>
                            @break
                        @default
                            <div class="role-unknown">Unknown Type</div>
                            @break
                    @end
                @end
                </div>
                """;
        }
    }

    @Test
    @DisplayName("@switch (typeof(user)) dispatches correctly on record types")
    void testTypeOfSwitchDispatch() {
        PolymorphicUserComp adminComp = new PolymorphicUserComp(new AdminUser("Elena"));
        String adminHtml = adminComp.render();
        assertTrue(adminHtml.contains("badge-admin"));
        assertTrue(adminHtml.contains("Admin: Elena"));

        PolymorphicUserComp devComp = new PolymorphicUserComp(new DeveloperUser("Marcus", "@mvance"));
        String devHtml = devComp.render();
        assertTrue(devHtml.contains("badge-dev"));
        assertTrue(devHtml.contains("Developer: Marcus (@mvance)"));

        PolymorphicUserComp userComp = new PolymorphicUserComp(new StandardUser("Sophia"));
        String userHtml = userComp.render();
        assertTrue(userHtml.contains("badge-user"));
        assertTrue(userHtml.contains("User: Sophia"));

        PolymorphicUserComp nullComp = new PolymorphicUserComp(null);
        String nullHtml = nullComp.render();
        assertTrue(nullHtml.contains("badge-guest"));
        assertTrue(nullHtml.contains("Guest User"));
    }

    @Test
    @DisplayName("@switch (status) matches string literals and executes @default fallback")
    void testStatusSwitchAndDefault() {
        StatusSwitchComp activeComp = new StatusSwitchComp("ACTIVE");
        assertTrue(activeComp.render().contains("status-active"));

        StatusSwitchComp suspendedComp = new StatusSwitchComp("SUSPENDED");
        assertTrue(suspendedComp.render().contains("status-suspended"));

        StatusSwitchComp unknownComp = new StatusSwitchComp("UNKNOWN_CODE");
        assertTrue(unknownComp.render().contains("status-unknown"));
    }

    @Test
    @DisplayName("Nested @switch inside @for loop iterates correctly")
    void testNestedSwitchInsideForLoop() {
        NestedSwitchComp comp = new NestedSwitchComp(List.of(
            new AdminUser("Alice"),
            new StandardUser("Bob")
        ));

        String html = comp.render();
        assertTrue(html.contains("role-admin"));
        assertTrue(html.contains("Admin Alice"));
        assertTrue(html.contains("role-user"));
        assertTrue(html.contains("User Bob"));
    }
}

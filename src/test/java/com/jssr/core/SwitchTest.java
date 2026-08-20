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
        public String render() {
            return """
                <div class="user-card">
                @switch (typeof(user)) {
                    @case ('AdminUser') {
                        <span class="badge-admin">Admin: ${user.name}</span>
                        @break
                    }
                    @case ('DeveloperUser') {
                        <span class="badge-dev">Developer: ${user.name} (${user.githubHandle})</span>
                        @break
                    }
                    @case ('StandardUser') {
                        <span class="badge-user">User: ${user.name}</span>
                        @break
                    }
                    @default {
                        <span class="badge-guest">Guest User</span>
                    }
                }
                </div>
                """;
        }
    }

    public record StatusSwitchComp(String status) implements JssrComponent {
        @Override
        public String render() {
            return """
                <div class="status-box">
                @switch (status) {
                    @case ('ACTIVE') {
                        <span class="status-active font-bold">Account Active</span>
                        @break
                    }
                    @case ('PENDING') {
                        <span class="status-pending">Verification Pending</span>
                        @break
                    }
                    @case ('SUSPENDED') {
                        <span class="status-suspended text-rose-500">Account Suspended</span>
                        @break
                    }
                    @default {
                        <span class="status-unknown">Status Unknown</span>
                    }
                }
                </div>
                """;
        }
    }

    public record NestedSwitchComp(List<Object> users) implements JssrComponent {
        @Override
        public String render() {
            return """
                <div class="user-list">
                @for (u : users) {
                    @switch (typeof(u)) {
                        @case ('AdminUser') {
                            <div class="role-admin">Admin ${u.name}</div>
                            @break
                        }
                        @case ('StandardUser') {
                            <div class="role-user">User ${u.name}</div>
                            @break
                        }
                        @default {
                            <div class="role-unknown">Unknown Type</div>
                        }
                    }
                }
                </div>
                """;
        }
    }

    public record FallthroughSwitchComp(String tier) implements JssrComponent {
        @Override
        public String render() {
            return """
                <div class="tier-box">
                @switch (tier) {
                    @case ('PLATINUM') {
                        <span>Platinum Perks</span>
                    }
                    @case ('GOLD') {
                        <span>Gold Perks</span>
                        @break
                    }
                    @default {
                        <span>Basic Perks</span>
                    }
                }
                </div>
                """;
        }
    }

    public record NestedBreakSwitchComp(String role, boolean stopEarly) implements JssrComponent {
        @Override
        public String render() {
            return """
                <div>
                @switch (role) {
                    @case ('ADMIN') {
                        <span>Admin Access</span>
                        @if (stopEarly) {
                            @break
                        }
                        <span>Full Audit Access</span>
                    }
                    @default {
                        <span>Guest Access</span>
                    }
                }
                </div>
                """;
        }
    }

    public record ColonSwitchComp(String mode) implements JssrComponent {
        @Override
        public String render() {
            return """
                <div>
                @switch (mode):
                    @case ('DEV'):
                        <span>Developer Mode</span>
                        @break
                    @case ('PROD'):
                        <span>Production Mode</span>
                        @break
                    @default:
                        <span>Default Mode</span>
                </div>
                """;
        }
    }

    @Test
    @DisplayName("@switch (typeof(user)) dispatches correctly on record types")
    void testTypeOfSwitchDispatch() {
        PolymorphicUserComp adminComp = new PolymorphicUserComp(new AdminUser("Elena"));
        String adminHtml = JssrComponent.render(adminComp);
        assertTrue(adminHtml.contains("badge-admin"));
        assertTrue(adminHtml.contains("Admin: Elena"));

        PolymorphicUserComp devComp = new PolymorphicUserComp(new DeveloperUser("Marcus", "@mvance"));
        String devHtml = JssrComponent.render(devComp);
        assertTrue(devHtml.contains("badge-dev"));
        assertTrue(devHtml.contains("Developer: Marcus (@mvance)"));

        PolymorphicUserComp userComp = new PolymorphicUserComp(new StandardUser("Sophia"));
        String userHtml = JssrComponent.render(userComp);
        assertTrue(userHtml.contains("badge-user"));
        assertTrue(userHtml.contains("User: Sophia"));

        PolymorphicUserComp nullComp = new PolymorphicUserComp(null);
        String nullHtml = JssrComponent.render(nullComp);
        assertTrue(nullHtml.contains("badge-guest"));
        assertTrue(nullHtml.contains("Guest User"));
    }

    @Test
    @DisplayName("@switch (status) matches string literals and executes @default fallback")
    void testStatusSwitchAndDefault() {
        StatusSwitchComp activeComp = new StatusSwitchComp("ACTIVE");
        assertTrue(JssrComponent.render(activeComp).contains("status-active"));

        StatusSwitchComp suspendedComp = new StatusSwitchComp("SUSPENDED");
        assertTrue(JssrComponent.render(suspendedComp).contains("status-suspended"));

        StatusSwitchComp unknownComp = new StatusSwitchComp("UNKNOWN_CODE");
        assertTrue(JssrComponent.render(unknownComp).contains("status-unknown"));
    }

    @Test
    @DisplayName("Nested @switch inside @for loop iterates correctly")
    void testNestedSwitchInsideForLoop() {
        NestedSwitchComp comp = new NestedSwitchComp(List.of(
            new AdminUser("Alice"),
            new StandardUser("Bob")
        ));

        String html = JssrComponent.render(comp);
        assertTrue(html.contains("role-admin"));
        assertTrue(html.contains("Admin Alice"));
        assertTrue(html.contains("role-user"));
        assertTrue(html.contains("User Bob"));
    }

    @Test
    @DisplayName("@case without @break falls through to subsequent @case")
    void testCaseFallthrough() {
        FallthroughSwitchComp plat = new FallthroughSwitchComp("PLATINUM");
        String html = JssrComponent.render(plat);
        assertTrue(html.contains("Platinum Perks"));
        assertTrue(html.contains("Gold Perks"));
        assertFalse(html.contains("Basic Perks"));

        FallthroughSwitchComp gold = new FallthroughSwitchComp("GOLD");
        String goldHtml = JssrComponent.render(gold);
        assertFalse(goldHtml.contains("Platinum Perks"));
        assertTrue(goldHtml.contains("Gold Perks"));
        assertFalse(goldHtml.contains("Basic Perks"));
    }

    @Test
    @DisplayName("@break nested inside @if inside @case breaks out of @switch")
    void testNestedBreakInsideIf() {
        NestedBreakSwitchComp stopComp = new NestedBreakSwitchComp("ADMIN", true);
        String stopHtml = JssrComponent.render(stopComp);
        assertTrue(stopHtml.contains("Admin Access"));
        assertFalse(stopHtml.contains("Full Audit Access"));
        assertFalse(stopHtml.contains("Guest Access"));

        NestedBreakSwitchComp continueComp = new NestedBreakSwitchComp("ADMIN", false);
        String continueHtml = JssrComponent.render(continueComp);
        assertTrue(continueHtml.contains("Admin Access"));
        assertTrue(continueHtml.contains("Full Audit Access"));
        assertTrue(continueHtml.contains("Guest Access"));
    }

    public record MixedBraceAndColonComp(String value) implements JssrComponent {
        @Override
        public String render() {
            return """
                <div>
                @switch (value) {
                    @case ('val1'):
                        <!-- val1 content -->
                    @case ('val2'):
                        <!-- val2 content -->
                        @break
                }
                </div>
                """;
        }
    }

    @Test
    @DisplayName("Colon syntax @case(expr): ... @break works seamlessly")
    void testColonSwitchSyntax() {
        ColonSwitchComp dev = new ColonSwitchComp("DEV");
        String devHtml = JssrComponent.render(dev);
        assertTrue(devHtml.contains("Developer Mode"));
        assertFalse(devHtml.contains("Production Mode"));

        ColonSwitchComp defaultComp = new ColonSwitchComp("UNKNOWN");
        String defHtml = JssrComponent.render(defaultComp);
        assertTrue(defHtml.contains("Default Mode"));
    }

    @Test
    @DisplayName("@switch(value) { @case(val1): ... @case(val2): @break } mixed brace/colon syntax works and falls through")
    void testMixedBraceAndColonSwitch() {
        MixedBraceAndColonComp val1Comp = new MixedBraceAndColonComp("val1");
        String val1Html = JssrComponent.render(val1Comp);
        assertTrue(val1Html.contains("val1 content"));
        assertTrue(val1Html.contains("val2 content"));

        MixedBraceAndColonComp val2Comp = new MixedBraceAndColonComp("val2");
        String val2Html = JssrComponent.render(val2Comp);
        assertFalse(val2Html.contains("val1 content"));
        assertTrue(val2Html.contains("val2 content"));
    }
}

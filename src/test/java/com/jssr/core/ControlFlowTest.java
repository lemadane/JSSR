package com.jssr.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class ControlFlowTest {

    public record SimpleIfComp(boolean active) implements JssrComponent {
        @Override
        public String template() {
            return """
                @if (active)
                    <span class="active">Active Account</span>
                @end
                """;
        }
    }

    public record IfElseComp(boolean active) implements JssrComponent {
        @Override
        public String template() {
            return """
                @if (active)
                    <span class="status">Online</span>
                @else
                    <span class="status">Offline</span>
                @end
                """;
        }
    }

    public record MultiBranchComp(String role) implements JssrComponent {
        @Override
        public String template() {
            return """
                @if (role == 'ADMIN')
                    <span class="badge">Admin</span>
                @elseif (role == 'DEV')
                    <span class="badge">Developer</span>
                @else
                    <span class="badge">User</span>
                @end
                """;
        }
    }

    public record UserProfile(String name, boolean isAdmin) {}

    public record PropertyPathComp(UserProfile user) implements JssrComponent {
        @Override
        public String template() {
            return """
                @if (user.isAdmin)
                    <span class="admin">Admin Profile for ${user.name}</span>
                @else
                    <span class="user">User Profile for ${user.name}</span>
                @end
                """;
        }
    }

    public record NegatedComp(boolean disabled) implements JssrComponent {
        @Override
        public String template() {
            return """
                @if (!disabled)
                    <button>Submit</button>
                @end
                """;
        }
    }

    public record TruthinessComp(
        BooleanAttribute checkedAttr,
        Optional<String> nickname,
        String title,
        int count,
        List<String> items
    ) implements JssrComponent {
        @Override
        public String template() {
            return """
                @if (checkedAttr)
                    <p>Checked</p>
                @end
                @if (nickname)
                    <p>Nick: ${nickname}</p>
                @end
                @if (title)
                    <p>Title: ${title}</p>
                @end
                @if (count)
                    <p>Count: ${count}</p>
                @end
                @if (items)
                    <p>Has Items</p>
                @end
                """;
        }
    }

    public record RelationalComp(int count, int level) implements JssrComponent {
        @Override
        public String template() {
            return """
                @if (count > 0)
                    <p>Positive Count</p>
                @end
                @if (level >= 5)
                    <p>High Level</p>
                @else
                    <p>Low Level</p>
                @end
                """;
        }
    }

    public record NestedIfComp(boolean outer, boolean inner) implements JssrComponent {
        @Override
        public String template() {
            return """
                @if (outer)
                    <div class="outer">
                        @if (inner)
                            <span class="inner">Inner Content</span>
                        @else
                            <span class="inner">Inner Fallback</span>
                        @end
                    </div>
                @else
                    <div class="outer-fallback">Disabled</div>
                @end
                """;
        }
    }

    public record UnclosedIfComp(boolean active) implements JssrComponent {
        @Override
        public String template() {
            return """
                @if (active)
                    <div>Missing end</div>
                """;
        }
    }

    public record UnknownVarIfComp(String name) implements JssrComponent {
        @Override
        public String template() {
            return """
                @if (unknownField)
                    <div>Text</div>
                @end
                """;
        }
    }

    @Test
    @DisplayName("@if (condition) evaluates true and false branches correctly")
    void testSimpleIfTrueAndFalse() {
        SimpleIfComp compTrue = new SimpleIfComp(true);
        assertTrue(compTrue.render().contains("<span class=\"active\">Active Account</span>"));

        SimpleIfComp compFalse = new SimpleIfComp(false);
        assertFalse(compFalse.render().contains("Active Account"));
    }

    @Test
    @DisplayName("@if ... @else ... @end renders appropriate branch")
    void testIfElse() {
        IfElseComp online = new IfElseComp(true);
        assertTrue(online.render().contains("<span class=\"status\">Online</span>"));
        assertFalse(online.render().contains("Offline"));

        IfElseComp offline = new IfElseComp(false);
        assertTrue(offline.render().contains("<span class=\"status\">Offline</span>"));
        assertFalse(offline.render().contains("Online"));
    }

    @Test
    @DisplayName("@if ... @elseif ... @else ... @end handles multiple conditions")
    void testIfElseIfElse() {
        MultiBranchComp admin = new MultiBranchComp("ADMIN");
        assertTrue(admin.render().contains("<span class=\"badge\">Admin</span>"));

        MultiBranchComp dev = new MultiBranchComp("DEV");
        assertTrue(dev.render().contains("<span class=\"badge\">Developer</span>"));

        MultiBranchComp user = new MultiBranchComp("GUEST");
        assertTrue(user.render().contains("<span class=\"badge\">User</span>"));
    }

    @Test
    @DisplayName("Nested property paths in @if (user.isAdmin) resolve correctly")
    void testPropertyPathCondition() {
        PropertyPathComp adminComp = new PropertyPathComp(new UserProfile("Alice", true));
        assertTrue(adminComp.render().contains("Admin Profile for Alice"));

        PropertyPathComp userComp = new PropertyPathComp(new UserProfile("Bob", false));
        assertTrue(userComp.render().contains("User Profile for Bob"));
    }

    @Test
    @DisplayName("Negated condition @if (!disabled) works correctly")
    void testNegatedCondition() {
        NegatedComp enabled = new NegatedComp(false);
        assertTrue(enabled.render().contains("<button>Submit</button>"));

        NegatedComp disabled = new NegatedComp(true);
        assertFalse(disabled.render().contains("<button>Submit</button>"));
    }

    @Test
    @DisplayName("Truthiness checks for BooleanAttribute, Optional, String, Number, and Collection")
    void testTruthiness() {
        TruthinessComp truthy = new TruthinessComp(
            BooleanAttribute.present("checked"),
            Optional.of("Speedy"),
            "Hello World",
            42,
            List.of("A", "B")
        );
        String renderedTruthy = truthy.render();
        assertTrue(renderedTruthy.contains("<p>Checked</p>"));
        assertTrue(renderedTruthy.contains("<p>Nick: Speedy</p>"));
        assertTrue(renderedTruthy.contains("<p>Title: Hello World</p>"));
        assertTrue(renderedTruthy.contains("<p>Count: 42</p>"));
        assertTrue(renderedTruthy.contains("<p>Has Items</p>"));

        TruthinessComp falsy = new TruthinessComp(
            BooleanAttribute.absent("checked"),
            Optional.empty(),
            "",
            0,
            List.of()
        );
        String renderedFalsy = falsy.render();
        assertFalse(renderedFalsy.contains("<p>Checked</p>"));
        assertFalse(renderedFalsy.contains("<p>Nick:"));
        assertFalse(renderedFalsy.contains("<p>Title:"));
        assertFalse(renderedFalsy.contains("<p>Count:"));
        assertFalse(renderedFalsy.contains("<p>Has Items</p>"));
    }

    @Test
    @DisplayName("Relational operators (>, >=, <, <=) in @if conditions evaluate correctly")
    void testRelationalOperators() {
        RelationalComp high = new RelationalComp(5, 10);
        String resHigh = high.render();
        assertTrue(resHigh.contains("<p>Positive Count</p>"));
        assertTrue(resHigh.contains("<p>High Level</p>"));

        RelationalComp low = new RelationalComp(0, 2);
        String resLow = low.render();
        assertFalse(resLow.contains("<p>Positive Count</p>"));
        assertTrue(resLow.contains("<p>Low Level</p>"));
    }

    @Test
    @DisplayName("Nested @if directive blocks evaluate properly")
    void testNestedIfBlocks() {
        NestedIfComp bothTrue = new NestedIfComp(true, true);
        String res1 = bothTrue.render();
        assertTrue(res1.contains("class=\"outer\""));
        assertTrue(res1.contains("Inner Content"));

        NestedIfComp outerTrueInnerFalse = new NestedIfComp(true, false);
        String res2 = outerTrueInnerFalse.render();
        assertTrue(res2.contains("class=\"outer\""));
        assertTrue(res2.contains("Inner Fallback"));

        NestedIfComp outerFalse = new NestedIfComp(false, true);
        String res3 = outerFalse.render();
        assertTrue(res3.contains("Disabled"));
        assertFalse(res3.contains("class=\"outer\""));
    }

    @Test
    @DisplayName("Unclosed @if directive should fail fast with explicit IllegalArgumentException")
    void testUnclosedIfDirectiveFailFast() {
        UnclosedIfComp comp = new UnclosedIfComp(true);
        Exception ex = assertThrows(IllegalArgumentException.class, comp::render);
        assertTrue(ex.getMessage().contains("Unclosed JSSR control flow directive '@if'"));
        assertTrue(ex.getMessage().contains("Expected matching '@end'"));
    }

    @Test
    @DisplayName("Unknown control flow condition property should fail fast with explicit IllegalArgumentException")
    void testUnknownVariableFailFast() {
        UnknownVarIfComp comp = new UnknownVarIfComp("Alice");
        Exception ex = assertThrows(IllegalArgumentException.class, comp::render);
        assertTrue(ex.getMessage().contains("Unknown JSSR control flow property 'unknownField'"));
    }
}

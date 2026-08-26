package com.jssr.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class ControlFlowTest {

    public record SimpleIfComp(boolean active) implements JssrComponent {
        @Override
        public String render() {
            return """
                @if (active) {
                    <span class="active">Active Account</span>
                }
                """;
        }
    }

    public record IfElseComp(boolean active) implements JssrComponent {
        @Override
        public String render() {
            return """
                @if (active) {
                    <span class="status">Online</span>
                } @else {
                    <span class="status">Offline</span>
                }
                """;
        }
    }

    public record MultiBranchComp(String role) implements JssrComponent {
        @Override
        public String render() {
            return """
                @if (role == 'ADMIN') {
                    <span class="badge">Admin</span>
                } @elseif (role == 'DEV') {
                    <span class="badge">Developer</span>
                } @else {
                    <span class="badge">User</span>
                }
                """;
        }
    }

    public record UserProfile(String name, boolean isAdmin) {}

    public record PropertyPathComp(UserProfile user) implements JssrComponent {
        @Override
        public String render() {
            return """
                @if (user.isAdmin) {
                    <span class="admin">Admin Profile for ${user.name}</span>
                } @else {
                    <span class="user">User Profile for ${user.name}</span>
                }
                """;
        }
    }

    public record NegatedComp(boolean disabled) implements JssrComponent {
        @Override
        public String render() {
            return """
                @if (!disabled) {
                    <button>Submit</button>
                }
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
        public String render() {
            return """
                @if (checkedAttr) {
                    <p>Checked</p>
                }
                @if (nickname) {
                    <p>Nick: ${nickname}</p>
                }
                @if (title) {
                    <p>Title: ${title}</p>
                }
                @if (count) {
                    <p>Count: ${count}</p>
                }
                @if (items) {
                    <p>Has Items</p>
                }
                """;
        }
    }

    public record RelationalComp(int count, int level) implements JssrComponent {
        @Override
        public String render() {
            return """
                @if (count > 0) {
                    <p>Positive Count</p>
                }
                @if (level >= 5) {
                    <p>High Level</p>
                } @else {
                    <p>Low Level</p>
                }
                """;
        }
    }

    public record NestedIfComp(boolean outer, boolean inner) implements JssrComponent {
        @Override
        public String render() {
            return """
                @if (outer) {
                    <div class="outer">
                        @if (inner) {
                            <span class="inner">Inner Content</span>
                        } @else {
                            <span class="inner">Inner Fallback</span>
                        }
                    </div>
                } @else {
                    <div class="outer-fallback">Disabled</div>
                }
                """;
        }
    }

    public record UnclosedIfComp(boolean active) implements JssrComponent {
        @Override
        public String render() {
            return """
                @if (active) {
                    <div>Missing end</div>
                """;
        }
    }

    public record UnknownVarIfComp(String name) implements JssrComponent {
        @Override
        public String render() {
            return """
                @if (unknownField) {
                    <div>Text</div>
                }
                """;
        }
    }

    @Test
    @DisplayName("@if (condition) evaluates true and false branches correctly")
    void testSimpleIfTrueAndFalse() {
        SimpleIfComp compTrue = new SimpleIfComp(true);
        assertTrue(JssrComponent.render(compTrue).contains("<span class=\"active\">Active Account</span>"));

        SimpleIfComp compFalse = new SimpleIfComp(false);
        assertFalse(JssrComponent.render(compFalse).contains("Active Account"));
    }

    @Test
    @DisplayName("@if ... @else ... renders appropriate branch")
    void testIfElse() {
        IfElseComp online = new IfElseComp(true);
        assertTrue(JssrComponent.render(online).contains("<span class=\"status\">Online</span>"));
        assertFalse(JssrComponent.render(online).contains("Offline"));

        IfElseComp offline = new IfElseComp(false);
        assertTrue(JssrComponent.render(offline).contains("<span class=\"status\">Offline</span>"));
        assertFalse(JssrComponent.render(offline).contains("Online"));
    }

    @Test
    @DisplayName("@if ... @elseif ... @else ... handles multiple conditions")
    void testIfElseIfElse() {
        MultiBranchComp admin = new MultiBranchComp("ADMIN");
        assertTrue(JssrComponent.render(admin).contains("<span class=\"badge\">Admin</span>"));

        MultiBranchComp dev = new MultiBranchComp("DEV");
        assertTrue(JssrComponent.render(dev).contains("<span class=\"badge\">Developer</span>"));

        MultiBranchComp user = new MultiBranchComp("GUEST");
        assertTrue(JssrComponent.render(user).contains("<span class=\"badge\">User</span>"));
    }

    @Test
    @DisplayName("Nested property paths in @if (user.isAdmin) resolve correctly")
    void testPropertyPathCondition() {
        PropertyPathComp adminComp = new PropertyPathComp(new UserProfile("Alice", true));
        assertTrue(JssrComponent.render(adminComp).contains("Admin Profile for Alice"));

        PropertyPathComp userComp = new PropertyPathComp(new UserProfile("Bob", false));
        assertTrue(JssrComponent.render(userComp).contains("User Profile for Bob"));
    }

    @Test
    @DisplayName("Negated condition @if (!disabled) works correctly")
    void testNegatedCondition() {
        NegatedComp enabled = new NegatedComp(false);
        assertTrue(JssrComponent.render(enabled).contains("<button>Submit</button>"));

        NegatedComp disabled = new NegatedComp(true);
        assertFalse(JssrComponent.render(disabled).contains("<button>Submit</button>"));
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
        String renderedTruthy = JssrComponent.render(truthy);
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
        String renderedFalsy = JssrComponent.render(falsy);
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
        String resHigh = JssrComponent.render(high);
        assertTrue(resHigh.contains("<p>Positive Count</p>"));
        assertTrue(resHigh.contains("<p>High Level</p>"));

        RelationalComp low = new RelationalComp(0, 2);
        String resLow = JssrComponent.render(low);
        assertFalse(resLow.contains("<p>Positive Count</p>"));
        assertTrue(resLow.contains("<p>Low Level</p>"));
    }

    @Test
    @DisplayName("Nested @if directive blocks evaluate properly")
    void testNestedIfBlocks() {
        NestedIfComp bothTrue = new NestedIfComp(true, true);
        String res1 = JssrComponent.render(bothTrue);
        assertTrue(res1.contains("class=\"outer\""));
        assertTrue(res1.contains("Inner Content"));

        NestedIfComp outerTrueInnerFalse = new NestedIfComp(true, false);
        String res2 = JssrComponent.render(outerTrueInnerFalse);
        assertTrue(res2.contains("class=\"outer\""));
        assertTrue(res2.contains("Inner Fallback"));

        NestedIfComp outerFalse = new NestedIfComp(false, true);
        String res3 = JssrComponent.render(outerFalse);
        assertTrue(res3.contains("Disabled"));
        assertFalse(res3.contains("class=\"outer\""));
    }

    @Test
    @DisplayName("Unclosed @if directive should fail fast with explicit IllegalArgumentException")
    void testUnclosedIfDirectiveFailFast() {
        UnclosedIfComp comp = new UnclosedIfComp(true);
        Exception ex = assertThrows(IllegalArgumentException.class, () -> JssrComponent.render(comp));
        assertTrue(ex.getMessage().contains("Unclosed JSSR control flow directive '@if'"));
        assertTrue(ex.getMessage().contains("Expected matching '}'"));
    }

    @Test
    @DisplayName("Unknown control flow condition property should fail fast with explicit IllegalArgumentException")
    void testUnknownVariableFailFast() {
        UnknownVarIfComp comp = new UnknownVarIfComp("Alice");
        Exception ex = assertThrows(IllegalArgumentException.class, () -> JssrComponent.render(comp));
        assertTrue(ex.getMessage().contains("Unknown JSSR control flow property 'unknownField'"));
    }

    public record EarlyReturnComp(boolean stopEarly) implements JssrComponent {
        @Override
        public String render() {
            return """
                <div>
                    <h1>Header</h1>
                    @if (stopEarly) {
                        <p>Stopping early</p>
                        @return
                    }
                    <p>Footer content</p>
                </div>
                """;
        }
    }

    public record LoopReturnComp(List<String> items) implements JssrComponent {
        @Override
        public String render() {
            return """
                <ul>
                    @for (item : items) {
                        @if (item == 'STOP') {
                            @return
                        }
                        <li>${item}</li>
                    }
                </ul>
                """;
        }
    }

    @Test
    @DisplayName("@return directive inside @if block early returns execution from caller component")
    void testEarlyReturnInIfBlock() {
        EarlyReturnComp stopComp = new EarlyReturnComp(true);
        String stopHtml = JssrComponent.render(stopComp);
        assertTrue(stopHtml.contains("<h1>Header</h1>"));
        assertTrue(stopHtml.contains("<p>Stopping early</p>"));
        assertFalse(stopHtml.contains("<p>Footer content</p>"));

        EarlyReturnComp continueComp = new EarlyReturnComp(false);
        String continueHtml = JssrComponent.render(continueComp);
        assertTrue(continueHtml.contains("<h1>Header</h1>"));
        assertFalse(continueHtml.contains("<p>Stopping early</p>"));
        assertTrue(continueHtml.contains("<p>Footer content</p>"));
    }

    @Test
    @DisplayName("@return directive inside @for loop halts component execution immediately")
    void testEarlyReturnInForLoop() {
        LoopReturnComp comp = new LoopReturnComp(List.of("Alpha", "STOP", "Beta"));
        String html = JssrComponent.render(comp);
        assertTrue(html.contains("<li>Alpha</li>"));
        assertFalse(html.contains("<li>STOP</li>"));
        assertFalse(html.contains("<li>Beta</li>"));
        assertFalse(html.contains("</ul>"));
    }
}

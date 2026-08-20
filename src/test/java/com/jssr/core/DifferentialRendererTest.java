package com.jssr.core;

import com.jssr.core.compiler.CompilationFailureMode;
import com.jssr.core.compiler.CompilationStatus;
import com.jssr.core.compiler.JssrPrecompiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Differential test suite asserting 100% output equality between interpreted
 * rendering (component.render()) and precompiled AST rendering (component.renderPrecompiled()).
 */
public class DifferentialRendererTest {

    @BeforeEach
    void setUp() {
        JssrPrecompiler.clearCache();
        JssrPrecompiler.setFailureMode(CompilationFailureMode.FAIL_FAST);
    }

    @AfterEach
    void tearDown() {
        JssrPrecompiler.clearCache();
        JssrPrecompiler.enableGlobalPrecompilation(false);
        JssrPrecompiler.setFailureMode(CompilationFailureMode.WARN_AND_FALLBACK);
    }

    private void assertDifferentialParity(JssrComponent component, Class<? extends JssrComponent> clazz) {
        JssrPrecompiler.enableGlobalPrecompilation(false);
        String interpreted = JssrComponent.render(component);

        JssrPrecompiler.enableGlobalPrecompilation(true);
        String precompiled = component.renderPrecompiled();

        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(clazz));
        assertEquals(interpreted, precompiled, "Interpreted and precompiled output must match exactly for " + clazz.getSimpleName());
    }

    public record SimpleTextCard(String name, int age) implements JssrComponent {
        @Override
        public String render() {
            return "<div class=\"user\"><h1>${name}</h1><p>Age: ${age}</p></div>";
        }
    }

    public record QuotedAttrCard(String title, String cls) implements JssrComponent {
        @Override
        public String render() {
            return "<div class=\"${cls}\" title=\"${title}\">Card</div>";
        }
    }

    public record SafeUrlCard(SafeUrl url) implements JssrComponent {
        @Override
        public String render() {
            return "<a href=\"${url}\">Link</a>";
        }
    }

    public record SafeSrcSetCard(SafeSrcSet srcset) implements JssrComponent {
        @Override
        public String render() {
            return "<img srcset=\"${srcset}\">";
        }
    }

    public record SafeUrlListCard(SafeUrlList ping) implements JssrComponent {
        @Override
        public String render() {
            return "<a ping=\"${ping}\">Track</a>";
        }
    }

    public record FreeStandingBoolCard(boolean checked) implements JssrComponent {
        @Override
        public String render() {
            return "<input ${checked} />";
        }
    }

    public record UserDetail(boolean active) {}
    public record FreeStandingNestedBoolCard(UserDetail user) implements JssrComponent {
        @Override
        public String render() {
            return "<div ${user.active}>User</div>";
        }
    }

    public record FreeStandingBoolAttrCard(BooleanAttribute disabled) implements JssrComponent {
        @Override
        public String render() {
            return "<input ${disabled} />";
        }
    }

    public record FreeStandingHtmlAttrCard(HtmlAttribute custom) implements JssrComponent {
        @Override
        public String render() {
            return "<input ${custom} />";
        }
    }

    public record ChildComponentCard(String value) implements JssrComponent {
        @Override
        public String render() {
            return "<span>${value}</span>";
        }
    }

    public record ParentComponentAttrCard(ChildComponentCard child) implements JssrComponent {
        @Override
        public String render() {
            return "<div title=\"${child}\">Test</div>";
        }
    }

    public record TreeNode(String name, TreeNode child) implements JssrComponent {
        @Override
        public String render() {
            return """
                <div>
                    <span>${name}</span>
                    @if (child != null) {
                        ${child}
                    }
                </div>
                """;
        }
    }

    public record BoxedPrimitiveCard(
        Boolean flag,
        Integer num,
        Long longNum,
        Double doubleNum,
        Float floatNum,
        Short shortNum,
        Byte byteNum,
        Character charVal
    ) implements JssrComponent {
        @Override
        public String render() {
            return "<div>${flag},${num},${longNum},${doubleNum},${floatNum},${shortNum},${byteNum},${charVal}</div>";
        }
    }

    public record BoxedFreeStandingCard(Boolean checked) implements JssrComponent {
        @Override
        public String render() {
            return "<input ${checked} />";
        }
    }

    public record PrimitiveTypesCard(short s, byte b, char c) implements JssrComponent {
        @Override
        public String render() {
            return "<div>${s}-${b}-${c}</div>";
        }
    }

    public record RawHtmlBodyCard(RawHtml raw) implements JssrComponent {
        @Override
        public String render() {
            return "<div class=\"content\">${raw}</div>";
        }
    }

    public record OptionalTextCard(Optional<String> name) implements JssrComponent {
        @Override
        public String render() {
            return "<div>${name}</div>";
        }
    }

    public record ControlFlowIfCard(int status) implements JssrComponent {
        @Override
        public String render() {
            return """
                <div>
                    @if (status == 200) {
                        <span class="ok">Success</span>
                    } @elseif (status == 404) {
                        <span class="warn">Not Found</span>
                    } @else {
                        <span class="err">Error</span>
                    }
                </div>
                """;
        }
    }

    public record ControlFlowForCard(List<String> items) implements JssrComponent {
        @Override
        public String render() {
            return """
                <ul>
                    @for (item : items) {
                        <li>${item}</li>
                    } @else {
                        <li class="empty">Empty</li>
                    }
                </ul>
                """;
        }
    }

    public record ControlFlowSwitchCard(String role) implements JssrComponent {
        @Override
        public String render() {
            return """
                <div>
                    @switch (role) {
                        @case ("admin") {
                            <p>Admin Access</p>
                        }
                        @case ("user") {
                            <p>User Access</p>
                        }
                        @default {
                            <p>Guest Access</p>
                        }
                    }
                </div>
                """;
        }
    }

    public record ControlFlowTryCard() implements JssrComponent {
        @Override
        public String render() {
            return """
                <div>
                    @try {
                        <p>OK</p>
                    } @catch(err) {
                        <p class="err">Caught: ${err.message}</p>
                    } @finally {
                        <p class="foot">Done</p>
                    }
                </div>
                """;
        }
    }

    public record ControlFlowThrowCard() implements JssrComponent {
        @Override
        public String render() {
            return "<div>@try { @throw(\"Simulated failure\") } @catch(err) { <p class=\"err\">Caught: ${err.message}</p> }</div>";
        }
    }

    public record ControlFlowLoopControlCard(List<Integer> nums) implements JssrComponent {
        @Override
        public String render() {
            return """
                <div>
                    @for (n : nums) {
                        @if (n < 0) {
                            @continue
                        }
                        @if (n > 10) {
                            @break
                        }
                        <span>${n}</span>
                    }
                </div>
                """;
        }
    }

    @Test
    @DisplayName("Differential test: simple text interpolation")
    void testSimpleTextDifferential() {
        assertDifferentialParity(new SimpleTextCard("<Alice & Bob>", 30), SimpleTextCard.class);
    }

    @Test
    @DisplayName("Differential test: quoted attribute values")
    void testQuotedAttrDifferential() {
        assertDifferentialParity(new QuotedAttrCard("User \"Title\"", "card-box"), QuotedAttrCard.class);
    }

    @Test
    @DisplayName("Differential test: SafeUrl in URL attribute")
    void testSafeUrlDifferential() {
        assertDifferentialParity(new SafeUrlCard(SafeUrl.of("https://example.com/path?a=1&b=2")), SafeUrlCard.class);
    }

    @Test
    @DisplayName("Differential test: SafeSrcSet in srcset attribute")
    void testSafeSrcSetDifferential() {
        assertDifferentialParity(new SafeSrcSetCard(SafeSrcSet.of("image.jpg\" onerror=\"alert(1) 1x, /img/b.png 2x")), SafeSrcSetCard.class);
    }

    @Test
    @DisplayName("Differential test: SafeUrlList in ping attribute")
    void testSafeUrlListDifferential() {
        assertDifferentialParity(new SafeUrlListCard(SafeUrlList.of("https://a.com\" onerror=\"alert(1) https://b.com")), SafeUrlListCard.class);
    }

    @Test
    @DisplayName("Differential test: free-standing boolean attribute")
    void testFreeStandingBoolDifferential() {
        assertDifferentialParity(new FreeStandingBoolCard(true), FreeStandingBoolCard.class);
        assertDifferentialParity(new FreeStandingBoolCard(false), FreeStandingBoolCard.class);
    }

    @Test
    @DisplayName("Differential test: free-standing nested boolean property")
    void testFreeStandingNestedBoolDifferential() {
        assertDifferentialParity(new FreeStandingNestedBoolCard(new UserDetail(true)), FreeStandingNestedBoolCard.class);
        assertDifferentialParity(new FreeStandingNestedBoolCard(new UserDetail(false)), FreeStandingNestedBoolCard.class);
    }

    @Test
    @DisplayName("Differential test: free-standing BooleanAttribute")
    void testFreeStandingBoolAttrDifferential() {
        assertDifferentialParity(new FreeStandingBoolAttrCard(BooleanAttribute.of("disabled", true)), FreeStandingBoolAttrCard.class);
        assertDifferentialParity(new FreeStandingBoolAttrCard(BooleanAttribute.of("disabled", false)), FreeStandingBoolAttrCard.class);
    }

    @Test
    @DisplayName("Differential test: free-standing HtmlAttribute")
    void testFreeStandingHtmlAttrDifferential() {
        assertDifferentialParity(new FreeStandingHtmlAttrCard(HtmlAttribute.of("data-widget-id", "12345")), FreeStandingHtmlAttrCard.class);
    }

    @Test
    @DisplayName("Differential test: RawHtml in body content")
    void testRawHtmlBodyDifferential() {
        assertDifferentialParity(new RawHtmlBodyCard(RawHtml.of("<b>Bold Content</b>")), RawHtmlBodyCard.class);
        assertDifferentialParity(new RawHtmlBodyCard(RawHtml.of(null)), RawHtmlBodyCard.class);
    }

    @Test
    @DisplayName("Differential test: JssrComponent in quoted attribute must HTML-escape output")
    void testChildComponentInQuotedAttrDifferential() {
        assertDifferentialParity(new ChildComponentCard("\" onmouseover=\"alert(1)"), ChildComponentCard.class);
        ParentComponentAttrCard card = new ParentComponentAttrCard(new ChildComponentCard("\" onmouseover=\"alert(1)"));
        assertDifferentialParity(card, ParentComponentAttrCard.class);

        JssrPrecompiler.enableGlobalPrecompilation(true);
        String compiled = card.renderPrecompiled();
        assertFalse(compiled.contains("\" onmouseover=\""), "Quotes inside child component rendered in attribute must be HTML-escaped");
        assertTrue(compiled.contains("&amp;quot;"), "Quotes inside child component rendered in attribute must be HTML-escaped");
    }

    public record ForSimpleLoopCard(java.util.List<String> items) implements JssrComponent {
        @Override
        public String render() {
            return "@for (item : items) { <li>${item}</li> }";
        }
    }

    public record ForVarLoopCard(java.util.List<String> items) implements JssrComponent {
        @Override
        public String render() {
            return "@for (var item : items) { <li>${item}</li> }";
        }
    }

    @Test
    @DisplayName("Differential test: for loop syntax variants (@for(item : list) and @for(var item : list))")
    void testForLoopSyntaxVariantsDifferential() {
        java.util.List<String> list = java.util.List.of("Alpha", "Beta", "Gamma");
        assertDifferentialParity(new ForSimpleLoopCard(list), ForSimpleLoopCard.class);
        assertDifferentialParity(new ForVarLoopCard(list), ForVarLoopCard.class);
        
        String simpleOutput = new ForSimpleLoopCard(list).renderPrecompiled();
        String varOutput = new ForVarLoopCard(list).renderPrecompiled();
        assertEquals(simpleOutput, varOutput, "Output for @for(item : list) and @for(var item : list) must be identical");
    }

    @Test
    @DisplayName("Differential test: recursive self-referencing component (TreeNode)")
    void testRecursiveTreeNodeDifferential() {
        TreeNode tree = new TreeNode("Root", new TreeNode("Child 1", new TreeNode("Child 2", null)));
        assertDifferentialParity(tree, TreeNode.class);
        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(TreeNode.class));
    }

    @Test
    @DisplayName("Differential test: null boxed primitives output empty string instead of 'null'")
    void testNullBoxedPrimitivesDifferential() {
        BoxedPrimitiveCard nullCard = new BoxedPrimitiveCard(null, null, null, null, null, null, null, null);
        assertDifferentialParity(nullCard, BoxedPrimitiveCard.class);

        JssrPrecompiler.enableGlobalPrecompilation(true);
        String compiled = nullCard.renderPrecompiled();
        assertFalse(compiled.contains("null"), "Precompiled output must not render string 'null' for null boxed primitives");

        BoxedPrimitiveCard nonNullCard = new BoxedPrimitiveCard(true, 1, 2L, 3.5d, 4.5f, (short) 5, (byte) 6, 'A');
        assertDifferentialParity(nonNullCard, BoxedPrimitiveCard.class);

        BoxedFreeStandingCard nullFreeStanding = new BoxedFreeStandingCard(null);
        assertDifferentialParity(nullFreeStanding, BoxedFreeStandingCard.class);
    }

    @Test
    @DisplayName("Differential test: short, byte, and char primitive types")
    void testPrimitiveTypesDifferential() {
        assertDifferentialParity(new PrimitiveTypesCard((short) 42, (byte) 7, '<'), PrimitiveTypesCard.class);
    }

    @Test
    @DisplayName("Differential test: Optional value rendering")
    void testOptionalTextDifferential() {
        assertDifferentialParity(new OptionalTextCard(Optional.of("Alice")), OptionalTextCard.class);
        assertDifferentialParity(new OptionalTextCard(Optional.empty()), OptionalTextCard.class);
    }

    @Test
    @DisplayName("Differential test: @if / @elseif / @else control flow")
    void testControlFlowIfDifferential() {
        assertDifferentialParity(new ControlFlowIfCard(200), ControlFlowIfCard.class);
        assertDifferentialParity(new ControlFlowIfCard(404), ControlFlowIfCard.class);
        assertDifferentialParity(new ControlFlowIfCard(500), ControlFlowIfCard.class);
    }

    @Test
    @DisplayName("Differential test: @for / @else control flow")
    void testControlFlowForDifferential() {
        assertDifferentialParity(new ControlFlowForCard(List.of("Item A", "Item B")), ControlFlowForCard.class);
        assertDifferentialParity(new ControlFlowForCard(List.of()), ControlFlowForCard.class);
    }

    @Test
    @DisplayName("Differential test: @switch / @case / @default control flow")
    void testControlFlowSwitchDifferential() {
        assertDifferentialParity(new ControlFlowSwitchCard("admin"), ControlFlowSwitchCard.class);
        assertDifferentialParity(new ControlFlowSwitchCard("user"), ControlFlowSwitchCard.class);
        assertDifferentialParity(new ControlFlowSwitchCard("unknown"), ControlFlowSwitchCard.class);
    }

    @Test
    @DisplayName("Differential test: @try / @catch / @finally control flow")
    void testControlFlowTryDifferential() {
        try {
            assertDifferentialParity(new ControlFlowTryCard(), ControlFlowTryCard.class);
            assertDifferentialParity(new ControlFlowThrowCard(), ControlFlowThrowCard.class);
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Test
    @DisplayName("Differential test: @continue and @break directives")
    void testControlFlowLoopControlDifferential() {
        assertDifferentialParity(new ControlFlowLoopControlCard(List.of(-1, 2, -5, 4, 15, 6)), ControlFlowLoopControlCard.class);
    }
}

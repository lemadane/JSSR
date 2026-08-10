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
        String interpreted = component.render();

        JssrPrecompiler.enableGlobalPrecompilation(true);
        String precompiled = component.renderPrecompiled();

        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(clazz));
        assertEquals(interpreted, precompiled, "Interpreted and precompiled output must match exactly for " + clazz.getSimpleName());
    }

    public record SimpleTextCard(String name, int age) implements JssrComponent {
        @Override
        public String template() {
            return "<div class=\"user\"><h1>${name}</h1><p>Age: ${age}</p></div>";
        }
    }

    public record QuotedAttrCard(String title, String cls) implements JssrComponent {
        @Override
        public String template() {
            return "<div class=\"${cls}\" title=\"${title}\">Card</div>";
        }
    }

    public record SafeUrlCard(SafeUrl url) implements JssrComponent {
        @Override
        public String template() {
            return "<a href=\"${url}\">Link</a>";
        }
    }

    public record SafeSrcSetCard(SafeSrcSet srcset) implements JssrComponent {
        @Override
        public String template() {
            return "<img srcset=\"${srcset}\">";
        }
    }

    public record SafeUrlListCard(SafeUrlList ping) implements JssrComponent {
        @Override
        public String template() {
            return "<a ping=\"${ping}\">Track</a>";
        }
    }

    public record FreeStandingBoolCard(boolean checked) implements JssrComponent {
        @Override
        public String template() {
            return "<input ${checked} />";
        }
    }

    public record UserDetail(boolean active) {}
    public record FreeStandingNestedBoolCard(UserDetail user) implements JssrComponent {
        @Override
        public String template() {
            return "<div ${user.active}>User</div>";
        }
    }

    public record FreeStandingBoolAttrCard(BooleanAttribute disabled) implements JssrComponent {
        @Override
        public String template() {
            return "<input ${disabled} />";
        }
    }

    public record FreeStandingHtmlAttrCard(HtmlAttribute custom) implements JssrComponent {
        @Override
        public String template() {
            return "<input ${custom} />";
        }
    }

    public record RawHtmlBodyCard(RawHtml raw) implements JssrComponent {
        @Override
        public String template() {
            return "<div class=\"content\">${raw}</div>";
        }
    }

    public record OptionalTextCard(Optional<String> name) implements JssrComponent {
        @Override
        public String template() {
            return "<div>${name}</div>";
        }
    }

    public record ControlFlowIfCard(int status) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div>
                    @if (status == 200)
                        <span class="ok">Success</span>
                    @elseif (status == 404)
                        <span class="warn">Not Found</span>
                    @else
                        <span class="err">Error</span>
                    @end
                </div>
                """;
        }
    }

    public record ControlFlowForCard(List<String> items) implements JssrComponent {
        @Override
        public String template() {
            return """
                <ul>
                    @for (item : items)
                        <li>${item}</li>
                    @else
                        <li class="empty">Empty</li>
                    @end
                </ul>
                """;
        }
    }

    public record ControlFlowSwitchCard(String role) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div>
                    @switch (role)
                        @case ("admin")
                            <p>Admin Access</p>
                        @case ("user")
                            <p>User Access</p>
                        @default
                            <p>Guest Access</p>
                    @end
                </div>
                """;
        }
    }

    public record ControlFlowTryCard() implements JssrComponent {
        @Override
        public String template() {
            return """
                <div>
                    @try
                        <p>OK</p>
                    @catch(err)
                        <p class="err">Caught: ${err.message}</p>
                    @finally
                        <p class="foot">Done</p>
                    @end
                </div>
                """;
        }
    }

    public record ControlFlowThrowCard() implements JssrComponent {
        @Override
        public String template() {
            return "<div>@try@throw(\"Simulated failure\")@catch(err)<p class=\"err\">Caught: ${err.message}</p>@end</div>";
        }
    }

    public record ControlFlowLoopControlCard(List<Integer> nums) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div>
                    @for (n : nums)
                        @if (n < 0)
                            @continue
                        @end
                        @if (n > 10)
                            @break
                        @end
                        <span>${n}</span>
                    @end
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

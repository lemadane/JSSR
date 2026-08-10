package com.jssr.core;

import com.jssr.core.compiler.CompilationFailureMode;
import com.jssr.core.compiler.CompilationStatus;
import com.jssr.core.compiler.JssrPrecompiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PrecompiledSecurityParityTest {

    @BeforeEach
    void setUp() {
        JssrPrecompiler.clearCache();
        JssrPrecompiler.enableGlobalPrecompilation(true);
        JssrPrecompiler.setFailureMode(CompilationFailureMode.FAIL_FAST);
    }

    @AfterEach
    void tearDown() {
        JssrPrecompiler.clearCache();
        JssrPrecompiler.enableGlobalPrecompilation(false);
        JssrPrecompiler.setFailureMode(CompilationFailureMode.WARN_AND_FALLBACK);
    }

    public record BadOnClickCard(String handler) implements JssrComponent {
        @Override
        public String template() {
            return "<button onclick=\"${handler}\">Click</button>";
        }
    }

    public record BadStyleAttrCard(String style) implements JssrComponent {
        @Override
        public String template() {
            return "<div style=\"${style}\">Text</div>";
        }
    }

    public record BadScriptCard(String code) implements JssrComponent {
        @Override
        public String template() {
            return "<script>${code}</script>";
        }
    }

    public record BadStyleBlockCard(String css) implements JssrComponent {
        @Override
        public String template() {
            return "<style>${css}</style>";
        }
    }

    public record BadCommentCard(String text) implements JssrComponent {
        @Override
        public String template() {
            return "<!-- ${text} -->";
        }
    }

    public record BadSrcDocCard(String html) implements JssrComponent {
        @Override
        public String template() {
            return "<iframe srcdoc=\"${html}\"></iframe>";
        }
    }

    public record BadFrameworkAttrCard(String alpine) implements JssrComponent {
        @Override
        public String template() {
            return "<div x-data=\"${alpine}\">Widget</div>";
        }
    }

    public record BadUnquotedAttrCard(String title) implements JssrComponent {
        @Override
        public String template() {
            return "<div title=${title}>Header</div>";
        }
    }

    public record RawHtmlInAttrCard(RawHtml rawHtml) implements JssrComponent {
        @Override
        public String template() {
            return "<div title=\"${rawHtml}\">Box</div>";
        }
    }

    public record PrimitiveUrlCard(int url) implements JssrComponent {
        @Override
        public String template() {
            return "<a href=\"${url}\">Link</a>";
        }
    }

    public record NullStringUrlCard(String url) implements JssrComponent {
        @Override
        public String template() {
            return "<a href=\"${url}\">Link</a>";
        }
    }

    public record StringSrcSetCard(String srcset) implements JssrComponent {
        @Override
        public String template() {
            return "<img srcset=\"${srcset}\">";
        }
    }

    public record StringPingCard(String ping) implements JssrComponent {
        @Override
        public String template() {
            return "<a ping=\"${ping}\">Track</a>";
        }
    }

    public record InvalidBreakOutsideLoopCard(String name) implements JssrComponent {
        @Override
        public String template() {
            return "<div>Hello</div>@break";
        }
    }

    @Test
    @DisplayName("Verify precompiled and interpreted parity: inline event handlers (onclick) are rejected")
    void testRejectsOnClick() {
        BadOnClickCard card = new BadOnClickCard("alert(1)");
        assertThrows(IllegalArgumentException.class, card::renderPrecompiled);
        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(BadOnClickCard.class));
    }

    @Test
    @DisplayName("Verify precompiled and interpreted parity: inline style attributes are rejected")
    void testRejectsInlineStyleAttr() {
        BadStyleAttrCard card = new BadStyleAttrCard("color:red");
        assertThrows(IllegalArgumentException.class, card::renderPrecompiled);
        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(BadStyleAttrCard.class));
    }

    @Test
    @DisplayName("Verify precompiled and interpreted parity: script block interpolation is rejected")
    void testRejectsScriptBlock() {
        BadScriptCard card = new BadScriptCard("console.log(1)");
        assertThrows(IllegalArgumentException.class, card::renderPrecompiled);
        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(BadScriptCard.class));
    }

    @Test
    @DisplayName("Verify precompiled and interpreted parity: style block interpolation is rejected")
    void testRejectsStyleBlock() {
        BadStyleBlockCard card = new BadStyleBlockCard("body { color: red; }");
        assertThrows(IllegalArgumentException.class, card::renderPrecompiled);
        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(BadStyleBlockCard.class));
    }

    @Test
    @DisplayName("Verify precompiled and interpreted parity: HTML comment interpolation is rejected")
    void testRejectsCommentInterpolation() {
        BadCommentCard card = new BadCommentCard("secret comment");
        assertThrows(IllegalArgumentException.class, card::renderPrecompiled);
        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(BadCommentCard.class));
    }

    @Test
    @DisplayName("Verify precompiled and interpreted parity: srcdoc attribute is rejected")
    void testRejectsSrcDoc() {
        BadSrcDocCard card = new BadSrcDocCard("<b>Inner HTML</b>");
        assertThrows(IllegalArgumentException.class, card::renderPrecompiled);
        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(BadSrcDocCard.class));
    }

    @Test
    @DisplayName("Verify precompiled and interpreted parity: framework x-* attributes are rejected")
    void testRejectsFrameworkAttr() {
        BadFrameworkAttrCard card = new BadFrameworkAttrCard("{ open: true }");
        assertThrows(IllegalArgumentException.class, card::renderPrecompiled);
        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(BadFrameworkAttrCard.class));
    }

    @Test
    @DisplayName("Verify precompiled and interpreted parity: unquoted attribute value interpolation is rejected")
    void testRejectsUnquotedAttr() {
        BadUnquotedAttrCard card = new BadUnquotedAttrCard("main");
        assertThrows(IllegalArgumentException.class, card::renderPrecompiled);
        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(BadUnquotedAttrCard.class));
    }

    @Test
    @DisplayName("Verify precompiled and interpreted parity: RawHtml in attribute is rejected")
    void testRejectsRawHtmlInAttr() {
        RawHtmlInAttrCard card = new RawHtmlInAttrCard(RawHtml.of("<b>Bold</b>"));
        assertThrows(IllegalArgumentException.class, card::renderPrecompiled);
        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(RawHtmlInAttrCard.class));
    }

    @Test
    @DisplayName("Verify precompiled and interpreted parity: primitive int field in URL attribute is rejected")
    void testRejectsPrimitiveIntInUrlAttr() {
        PrimitiveUrlCard card = new PrimitiveUrlCard(123);
        assertThrows(IllegalArgumentException.class, card::renderPrecompiled);
        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(PrimitiveUrlCard.class));
    }

    @Test
    @DisplayName("Verify precompiled and interpreted parity: null String field in URL attribute is rejected by type")
    void testRejectsNullStringInUrlAttr() {
        NullStringUrlCard card = new NullStringUrlCard(null);
        assertThrows(IllegalArgumentException.class, card::renderPrecompiled);
        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(NullStringUrlCard.class));
    }

    @Test
    @DisplayName("Verify precompiled and interpreted parity: String field in srcset attribute requires SafeSrcSet")
    void testRejectsStringInSrcSet() {
        StringSrcSetCard card = new StringSrcSetCard("img.jpg 1x");
        assertThrows(IllegalArgumentException.class, card::renderPrecompiled);
        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(StringSrcSetCard.class));
    }

    @Test
    @DisplayName("Verify precompiled and interpreted parity: String field in ping attribute requires SafeUrlList")
    void testRejectsStringInPing() {
        StringPingCard card = new StringPingCard("https://analytics.example.com");
        assertThrows(IllegalArgumentException.class, card::renderPrecompiled);
        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(StringPingCard.class));
    }

    @Test
    @DisplayName("Verify @break outside @for loop produces clear parse-time JSSR syntax error")
    void testRejectsBreakOutsideLoop() {
        InvalidBreakOutsideLoopCard card = new InvalidBreakOutsideLoopCard("Alice");
        assertThrows(Exception.class, card::renderPrecompiled);
    }
}

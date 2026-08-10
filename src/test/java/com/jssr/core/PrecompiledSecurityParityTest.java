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

    public record XlinkHrefCard(String url) implements JssrComponent {
        @Override
        public String template() {
            return "<svg><a xlink:href=\"${url}\">Link</a></svg>";
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

    public record StringIconCard(String icon) implements JssrComponent {
        @Override
        public String template() {
            return "<link rel=\"icon\" icon=\"${icon}\">";
        }
    }

    public record StringManifestCard(String manifest) implements JssrComponent {
        @Override
        public String template() {
            return "<link rel=\"manifest\" manifest=\"${manifest}\">";
        }
    }

    public record StringImageSrcSetCard(String imagesrcset) implements JssrComponent {
        @Override
        public String template() {
            return "<link rel=\"preload\" imagesrcset=\"${imagesrcset}\">";
        }
    }

    public record InvalidBreakOutsideLoopCard(String name) implements JssrComponent {
        @Override
        public String template() {
            return "<div>Hello</div>@break";
        }
    }

    public record FreestandingInvalidCard(String extra) implements JssrComponent {
        @Override
        public String template() {
            return "<input ${extra} />";
        }
    }

    public record FreestandingValidBoolCard(boolean disabled) implements JssrComponent {
        @Override
        public String template() {
            return "<input ${disabled} />";
        }
    }

    public record FreestandingBoolAttrCard(BooleanAttribute disabled) implements JssrComponent {
        @Override
        public String template() {
            return "<input ${disabled} />";
        }
    }

    public record FreestandingHtmlAttrCard(HtmlAttribute custom) implements JssrComponent {
        @Override
        public String template() {
            return "<input ${custom} />";
        }
    }

    public record SrcSetEscapingCard(SafeSrcSet srcset) implements JssrComponent {
        @Override
        public String template() {
            return "<img srcset=\"${srcset}\">";
        }
    }

    public record UrlListEscapingCard(SafeUrlList ping) implements JssrComponent {
        @Override
        public String template() {
            return "<a ping=\"${ping}\">Track</a>";
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
    @DisplayName("Verify precompiled and interpreted parity: xlink:href requires SafeUrl")
    void testRejectsXlinkHrefString() {
        XlinkHrefCard card = new XlinkHrefCard("https://example.com");
        assertThrows(IllegalArgumentException.class, card::renderPrecompiled);
        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(XlinkHrefCard.class));
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
    @DisplayName("Verify precompiled and interpreted parity: String field in icon attribute requires SafeUrl")
    void testRejectsStringInIcon() {
        StringIconCard card = new StringIconCard("https://example.com/favicon.ico");
        assertThrows(IllegalArgumentException.class, card::renderPrecompiled);
        assertThrows(IllegalArgumentException.class, card::render);
        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(StringIconCard.class));
    }

    @Test
    @DisplayName("Verify precompiled and interpreted parity: String field in manifest attribute requires SafeUrl")
    void testRejectsStringInManifest() {
        StringManifestCard card = new StringManifestCard("https://example.com/manifest.json");
        assertThrows(IllegalArgumentException.class, card::renderPrecompiled);
        assertThrows(IllegalArgumentException.class, card::render);
        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(StringManifestCard.class));
    }

    @Test
    @DisplayName("Verify precompiled and interpreted parity: String field in imagesrcset attribute requires SafeSrcSet")
    void testRejectsStringInImageSrcSet() {
        StringImageSrcSetCard card = new StringImageSrcSetCard("img.jpg 1x");
        assertThrows(IllegalArgumentException.class, card::renderPrecompiled);
        assertThrows(IllegalArgumentException.class, card::render);
        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(StringImageSrcSetCard.class));
    }

    @Test
    @DisplayName("Verify precompiled and interpreted parity: SafeSrcSet output is HTML-escaped")
    void testSafeSrcSetOutputEscapingParity() {
        SafeSrcSet safeSet = SafeSrcSet.of("image.jpg\" onerror=\"alert(1) 1x");
        SrcSetEscapingCard card = new SrcSetEscapingCard(safeSet);

        JssrPrecompiler.enableGlobalPrecompilation(false);
        String interpreted = card.render();

        JssrPrecompiler.enableGlobalPrecompilation(true);
        String compiled = card.renderPrecompiled();

        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(SrcSetEscapingCard.class));
        assertEquals(interpreted, compiled, "Precompiled output must match interpreted output for SafeSrcSet");
        assertTrue(compiled.contains("&quot; onerror=&quot;"), "Precompiled output must HTML-escape attribute-breaking quotes in SafeSrcSet");
        assertFalse(compiled.contains("image.jpg\" onerror="), "Precompiled output must not leave raw unescaped quotes in attribute");
    }

    @Test
    @DisplayName("Verify precompiled and interpreted parity: SafeUrlList output is HTML-escaped")
    void testSafeUrlListOutputEscapingParity() {
        SafeUrlList safeList = SafeUrlList.of("https://example.com\" onerror=\"alert(1)");
        UrlListEscapingCard card = new UrlListEscapingCard(safeList);

        JssrPrecompiler.enableGlobalPrecompilation(false);
        String interpreted = card.render();

        JssrPrecompiler.enableGlobalPrecompilation(true);
        String compiled = card.renderPrecompiled();

        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(UrlListEscapingCard.class));
        assertEquals(interpreted, compiled, "Precompiled output must match interpreted output for SafeUrlList");
        assertTrue(compiled.contains("&quot; onerror=&quot;"), "Precompiled output must HTML-escape attribute-breaking quotes in SafeUrlList");
        assertFalse(compiled.contains("example.com\" onerror="), "Precompiled output must not leave raw unescaped quotes in attribute");
    }

    @Test
    @DisplayName("Verify precompiled and interpreted parity: String in free-standing attribute position is rejected")
    void testRejectsFreestandingStringAttr() {
        FreestandingInvalidCard card = new FreestandingInvalidCard("autofocus");
        assertThrows(IllegalArgumentException.class, card::renderPrecompiled);
        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(FreestandingInvalidCard.class));
    }

    @Test
    @DisplayName("Verify precompiled rendering supports boolean in free-standing attribute position with 100% parity")
    void testValidFreestandingBooleanAttr() {
        FreestandingValidBoolCard active = new FreestandingValidBoolCard(true);
        JssrPrecompiler.enableGlobalPrecompilation(false);
        String activeInterpreted = active.render();
        JssrPrecompiler.enableGlobalPrecompilation(true);
        String activeCompiled = active.renderPrecompiled();

        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(FreestandingValidBoolCard.class));
        assertEquals(activeInterpreted, activeCompiled);
        assertTrue(activeCompiled.contains("disabled"));

        FreestandingValidBoolCard inactive = new FreestandingValidBoolCard(false);
        JssrPrecompiler.enableGlobalPrecompilation(false);
        String inactiveInterpreted = inactive.render();
        JssrPrecompiler.enableGlobalPrecompilation(true);
        String inactiveCompiled = inactive.renderPrecompiled();

        assertEquals(inactiveInterpreted, inactiveCompiled);
        assertFalse(inactiveCompiled.contains("disabled"));
    }

    @Test
    @DisplayName("Verify precompiled rendering supports BooleanAttribute in free-standing attribute position with parity")
    void testValidFreestandingBooleanAttribute() {
        FreestandingBoolAttrCard active = new FreestandingBoolAttrCard(BooleanAttribute.of("disabled", true));
        JssrPrecompiler.enableGlobalPrecompilation(false);
        String interpreted = active.render();
        JssrPrecompiler.enableGlobalPrecompilation(true);
        String compiled = active.renderPrecompiled();

        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(FreestandingBoolAttrCard.class));
        assertEquals(interpreted, compiled);
    }

    @Test
    @DisplayName("Verify precompiled rendering supports HtmlAttribute in free-standing attribute position with parity")
    void testValidFreestandingHtmlAttribute() {
        FreestandingHtmlAttrCard active = new FreestandingHtmlAttrCard(HtmlAttribute.of("data-test", "val"));
        JssrPrecompiler.enableGlobalPrecompilation(false);
        String interpreted = active.render();
        JssrPrecompiler.enableGlobalPrecompilation(true);
        String compiled = active.renderPrecompiled();

        assertEquals(CompilationStatus.COMPILED, JssrPrecompiler.status(FreestandingHtmlAttrCard.class));
        assertEquals(interpreted, compiled);
    }

    @Test
    @DisplayName("Verify @break outside @for loop produces clear parse-time JSSR syntax error")
    void testRejectsBreakOutsideLoop() {
        InvalidBreakOutsideLoopCard card = new InvalidBreakOutsideLoopCard("Alice");
        assertThrows(Exception.class, card::renderPrecompiled);
    }
}

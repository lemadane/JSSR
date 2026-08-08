package com.jssr.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TryCatchTest {

    public record UnsafeDataComp(String validField, String missingField) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div class="try-card">
                @try
                    <div class="valid-box">${validField}</div>
                    <div class="invalid-box">${nonExistentProp}</div>
                @catch (err)
                    <div class="fallback-box text-rose-500">
                        Caught rendering error: ${err.message}
                    </div>
                @end
                </div>
                """;
        }
    }

    public record SafeTryComp(String text) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div class="safe-card">
                @try
                    <span class="content">${text}</span>
                @catch
                    <span class="fallback">Error Occurred</span>
                @end
                </div>
                """;
        }
    }

    public record TryInForComp(List<String> items) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div class="item-list">
                @for (item : items)
                    @try
                        <div class="item">${item}</div>
                    @catch
                        <div class="item-error">Item Load Failed</div>
                    @end
                @end
                </div>
                """;
        }
    }

    @Test
    @DisplayName("@try executes normally when no exceptions are thrown")
    void testSafeTryExecution() {
        SafeTryComp comp = new SafeTryComp("Hello World");
        String html = comp.render();
        assertTrue(html.contains("safe-card"));
        assertTrue(html.contains("Hello World"));
        assertFalse(html.contains("fallback"));
    }

    @Test
    @DisplayName("@try ... @catch (err) captures rendering exceptions and renders fallback HTML with ${err.message}")
    void testTryCatchFallbackRendering() {
        UnsafeDataComp comp = new UnsafeDataComp("Valid Data", null);
        String html = comp.render();
        assertTrue(html.contains("fallback-box"));
        assertTrue(html.contains("Caught rendering error:"));
        assertTrue(html.contains("Unknown JSSR interpolation property"));
        assertTrue(html.contains("nonExistentProp"));
        assertFalse(html.contains("invalid-box"));
    }

    @Test
    @DisplayName("@try works seamlessly inside @for loops")
    void testTryCatchInsideForLoop() {
        TryInForComp comp = new TryInForComp(List.of("Alpha", "Beta", "Gamma"));
        String html = comp.render();
        assertTrue(html.contains("Alpha"));
        assertTrue(html.contains("Beta"));
        assertTrue(html.contains("Gamma"));
        assertFalse(html.contains("item-error"));
    }

    public record TryFinallyComp(String text, boolean throwError) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div class="finally-card">
                @try
                    @if (throwError)
                        <div>${nonExistentProperty}</div>
                    @else
                        <div>Data: ${text}</div>
                    @end
                @catch
                    <div class="error-msg">Caught Error</div>
                @finally
                    <div class="cleanup-footer">Always Rendered Footer</div>
                @end
                </div>
                """;
        }
    }

    @Test
    @DisplayName("@finally block always executes whether try succeeds or throws error")
    void testTryCatchFinallyExecution() {
        TryFinallyComp successComp = new TryFinallyComp("Success Data", false);
        String successHtml = successComp.render();
        assertTrue(successHtml.contains("Data: Success Data"));
        assertFalse(successHtml.contains("error-msg"));
        assertTrue(successHtml.contains("Always Rendered Footer"));

        TryFinallyComp errorComp = new TryFinallyComp("Success Data", true);
        String errorHtml = errorComp.render();
        assertTrue(errorHtml.contains("error-msg"));
        assertTrue(errorHtml.contains("Always Rendered Footer"));
    }

    public record ColonSyntaxComp(boolean triggerFault) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div class="colon-card">
                @try:
                    @if (triggerFault):
                        <div>${missingProp}</div>
                    @else:
                        <div>Clean Render</div>
                    @end
                @catch(e):
                    <div class="colon-catch">Colon Catch</div>
                @finally:
                    <div class="colon-finally">Colon Finally</div>
                @end
                </div>
                """;
        }
    }

    @Test
    @DisplayName("@try:, @catch(e):, and @finally: with colons parse and render correctly")
    void testTryCatchFinallyWithColons() {
        ColonSyntaxComp cleanComp = new ColonSyntaxComp(false);
        String cleanHtml = cleanComp.render();
        assertTrue(cleanHtml.contains("Clean Render"));
        assertFalse(cleanHtml.contains("Colon Catch"));
        assertTrue(cleanHtml.contains("Colon Finally"));

        ColonSyntaxComp faultComp = new ColonSyntaxComp(true);
        String faultHtml = faultComp.render();
        assertTrue(faultHtml.contains("Colon Catch"));
        assertTrue(faultHtml.contains("Colon Finally"));
    }
}

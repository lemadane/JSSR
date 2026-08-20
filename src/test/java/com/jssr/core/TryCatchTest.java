package com.jssr.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TryCatchTest {

    public record UnsafeDataComp(String validField, String missingField) implements JssrComponent {
        @Override
        public String render() {
            return """
                <div class="try-card">
                @try {
                    <div class="valid-box">${validField}</div>
                    <div class="invalid-box">${nonExistentProp}</div>
                } @catch (err) {
                    <div class="fallback-box text-rose-500">
                        Caught rendering error: ${err.message}
                    </div>
                }
                </div>
                """;
        }
    }

    public record SafeTryComp(String text) implements JssrComponent {
        @Override
        public String render() {
            return """
                <div class="safe-card">
                @try {
                    <span class="content">${text}</span>
                } @catch {
                    <span class="fallback">Error Occurred</span>
                }
                </div>
                """;
        }
    }

    public record TryInForComp(List<String> items) implements JssrComponent {
        @Override
        public String render() {
            return """
                <div class="item-list">
                @for (item : items) {
                    @try {
                        <div class="item">${item}</div>
                    } @catch {
                        <div class="item-error">Item Load Failed</div>
                    }
                }
                </div>
                """;
        }
    }

    @Test
    @DisplayName("@try executes normally when no exceptions are thrown")
    void testSafeTryExecution() {
        SafeTryComp comp = new SafeTryComp("Hello World");
        String html = JssrComponent.render(comp);
        assertTrue(html.contains("safe-card"));
        assertTrue(html.contains("Hello World"));
        assertFalse(html.contains("fallback"));
    }

    @Test
    @DisplayName("@try ... @catch (err) captures rendering exceptions and renders fallback HTML with ${err.message}")
    void testTryCatchFallbackRendering() {
        UnsafeDataComp comp = new UnsafeDataComp("Valid Data", null);
        String html = JssrComponent.render(comp);
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
        String html = JssrComponent.render(comp);
        assertTrue(html.contains("Alpha"));
        assertTrue(html.contains("Beta"));
        assertTrue(html.contains("Gamma"));
        assertFalse(html.contains("item-error"));
    }

    public record TryFinallyComp(String text, boolean throwError) implements JssrComponent {
        @Override
        public String render() {
            return """
                <div class="finally-card">
                @try {
                    @if (throwError) {
                        <div>${nonExistentProperty}</div>
                    } @else {
                        <div>Data: ${text}</div>
                    }
                } @catch {
                    <div class="error-msg">Caught Error</div>
                } @finally {
                    <div class="cleanup-footer">Always Rendered Footer</div>
                }
                </div>
                """;
        }
    }

    @Test
    @DisplayName("@finally block always executes whether try succeeds or throws error")
    void testTryCatchFinallyExecution() {
        TryFinallyComp successComp = new TryFinallyComp("Success Data", false);
        String successHtml = JssrComponent.render(successComp);
        assertTrue(successHtml.contains("Data: Success Data"));
        assertFalse(successHtml.contains("error-msg"));
        assertTrue(successHtml.contains("Always Rendered Footer"));

        TryFinallyComp errorComp = new TryFinallyComp("Success Data", true);
        String errorHtml = JssrComponent.render(errorComp);
        assertTrue(errorHtml.contains("error-msg"));
        assertTrue(errorHtml.contains("Always Rendered Footer"));
    }

    public record ColonSyntaxComp(boolean triggerFault) implements JssrComponent {
        @Override
        public String render() {
            return """
                <div class="colon-card">
                @try {
                    @if (triggerFault) {
                        <div>${missingProp}</div>
                    } @else {
                        <div>Clean Render</div>
                    }
                } @catch(e) {
                    <div class="colon-catch">Colon Catch</div>
                } @finally {
                    <div class="colon-finally">Colon Finally</div>
                }
                </div>
                """;
        }
    }

    @Test
    @DisplayName("@try, @catch(e), and @finally parse and render correctly")
    void testTryCatchFinallyWithColons() {
        ColonSyntaxComp cleanComp = new ColonSyntaxComp(false);
        String cleanHtml = JssrComponent.render(cleanComp);
        assertTrue(cleanHtml.contains("Clean Render"));
        assertFalse(cleanHtml.contains("Colon Catch"));
        assertTrue(cleanHtml.contains("Colon Finally"));

        ColonSyntaxComp faultComp = new ColonSyntaxComp(true);
        String faultHtml = JssrComponent.render(faultComp);
        assertTrue(faultHtml.contains("Colon Catch"));
        assertTrue(faultHtml.contains("Colon Finally"));
    }

    public record ThrowInTryComp(boolean triggerThrow) implements JssrComponent {
        @Override
        public String render() {
            return """
                <div class="throw-card">
                @try {
                    @if (triggerThrow) {
                        @throw("Database Connection Failed")
                    } @else {
                        <div>Normal Processing</div>
                    }
                } @catch(err) {
                    <div class="throw-catch">Caught: ${err.message}</div>
                } @finally {
                    <div class="throw-finally">Cleanup Done</div>
                }
                </div>
                """;
        }
    }

    @Test
    @DisplayName("@throw inside @try block triggers @catch(err) with custom error message")
    void testThrowDirectiveInsideTryCatch() {
        ThrowInTryComp cleanComp = new ThrowInTryComp(false);
        String cleanHtml = JssrComponent.render(cleanComp);
        assertTrue(cleanHtml.contains("Normal Processing"));
        assertFalse(cleanHtml.contains("throw-catch"));
        assertTrue(cleanHtml.contains("Cleanup Done"));

        ThrowInTryComp throwComp = new ThrowInTryComp(true);
        String throwHtml = JssrComponent.render(throwComp);
        assertTrue(throwHtml.contains("throw-catch"));
        assertTrue(throwHtml.contains("Caught: Database Connection Failed"));
        assertTrue(throwHtml.contains("Cleanup Done"));
    }

    public record StandaloneThrowComp() implements JssrComponent {
        @Override
        public String render() {
            return """
                <div>
                    @throw("Uncaught Template Exception")
                </div>
                """;
        }
    }

    @Test
    @DisplayName("@throw outside @try block throws RuntimeException aborting render")
    void testStandaloneThrowAbortsRender() {
        StandaloneThrowComp comp = new StandaloneThrowComp();
        RuntimeException ex = assertThrows(RuntimeException.class, () -> JssrComponent.render(comp));
        assertTrue(ex.getMessage().contains("Uncaught Template Exception"));
    }

    public record NewExceptionThrowComp() implements JssrComponent {
        @Override
        public String render() {
            return """
                <div>
                    @throw(new java.lang.IllegalArgumentException("Invalid Arguments Provided"))
                </div>
                """;
        }
    }

    @Test
    @DisplayName("@throw(new IllegalArgumentException(...)) instantiates and throws exact exception type")
    void testThrowNewExceptionInstantiation() {
        NewExceptionThrowComp comp = new NewExceptionThrowComp();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> JssrComponent.render(comp));
        assertEquals("Invalid Arguments Provided", ex.getMessage());
    }

    public record TryCatchNewExceptionComp() implements JssrComponent {
        @Override
        public String render() {
            return """
                <div class="try-new-ex">
                @try {
                    @throw(new IllegalStateException("Cluster Quorum Lost"))
                } @catch(e) {
                    <div class="caught-ex">Type: ${typeof(e)} | Message: ${e.message}</div>
                }
                </div>
                """;
        }
    }

    @Test
    @DisplayName("@try captures @throw(new IllegalStateException(...)) and renders typeof(e) and ${e.message}")
    void testTryCatchCapturesNewExceptionInstantiation() {
        TryCatchNewExceptionComp comp = new TryCatchNewExceptionComp();
        String html = JssrComponent.render(comp);
        System.out.println("RENDERED HTML: [" + html + "]");
        assertTrue(html.contains("Type: IllegalStateException"));
        assertTrue(html.contains("Message: Cluster Quorum Lost"));
    }

    public record JvmErrorInTryComp(String errorType) implements JssrComponent {
        @Override
        public String render() {
            return """
                <div class="try-jvm-error">
                @try {
                    @if (errorType == 'OOM') {
                        @throw(new java.lang.OutOfMemoryError("Java heap space simulation"))
                    } @elseif (errorType == 'SOE') {
                        @throw(new java.lang.StackOverflowError("Stack depth limit simulation"))
                    } @elseif (errorType == 'LINK') {
                        @throw(new java.lang.LinkageError("Class linkage failure simulation"))
                    } @else {
                        @throw(new java.lang.IllegalArgumentException("Ordinary caught exception"))
                    }
                } @catch(e) {
                    <div class="caught-msg">Caught: ${e.message}</div>
                }
                </div>
                """;
        }
    }

    @Test
    @DisplayName("@try: catches Exception but allows serious JVM Error instances (OutOfMemoryError, StackOverflowError, LinkageError) to escape unintercepted")
    void testJvmErrorsEscapeTryCatch() {
        // 1. Fatal JVM Errors MUST escape @try blocks
        JvmErrorInTryComp oomComp = new JvmErrorInTryComp("OOM");
        assertThrows(OutOfMemoryError.class, () -> JssrComponent.render(oomComp));

        JvmErrorInTryComp soeComp = new JvmErrorInTryComp("SOE");
        assertThrows(StackOverflowError.class, () -> JssrComponent.render(soeComp));

        JvmErrorInTryComp linkComp = new JvmErrorInTryComp("LINK");
        assertThrows(LinkageError.class, () -> JssrComponent.render(linkComp));

        // 2. Standard Exception instances MUST be caught by @catch(e):
        JvmErrorInTryComp normalComp = new JvmErrorInTryComp("NORMAL");
        String html = JssrComponent.render(normalComp);
        assertTrue(html.contains("Caught: Ordinary caught exception"));
    }
}

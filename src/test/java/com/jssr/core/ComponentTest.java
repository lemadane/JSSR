package com.jssr.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComponentTest {

    public enum Status {
        ACTIVE,
        INACTIVE
    }

    public enum MixedStatus {
        InProgress,
        Completed
    }

    public record UserPage(String username) implements JssrComponent {
        @Override
        public String template() {
            return "<h1>${username}</h1>";
        }
    }

    public record ArticlePage(String title, RawHtml content, JssrComponent badge) implements JssrComponent {
        @Override
        public String template() {
            return """
                <article>
                    <h2>${title}</h2>
                    <div class="content">${content}</div>
                    <div class="badge">${badge}</div>
                </article>
                """;
        }
    }

    public record Link(String href, String label) implements JssrComponent {
        @Override
        public String template() {
            return "<a href=\"${href}\">${label}</a>";
        }
    }

    public record Parent(String href, String label) implements JssrComponent {
        @Override
        public String template() {
            return "<Link href=\"${href}\" label=\"${label}\" />";
        }
    }

    public record HtmxButton(String hxGet, String label) implements JssrComponent {
        @Override
        public String template() {
            return "<button hx-get=\"${hxGet}\">${label}</button>";
        }
    }

    public record ApiButton(String endpoint) implements JssrComponent {
        @Override
        public String template() {
            return "<button data-endpoint=\"${endpoint}\">Call API</button>";
        }
    }

    public record TaskItem(MixedStatus status) implements JssrComponent {
        @Override
        public String template() {
            return "status=${status}";
        }
    }

    record PackagePrivateCard(String title) implements JssrComponent {
        @Override
        public String template() {
            return "<div class=\"card\">${title}</div>";
        }
    }

    public record Card(RawHtml children) implements JssrComponent {
        @Override
        public String template() {
            return "<div class=\"card\">${children}</div>";
        }
    }

    public record Self() implements JssrComponent {
        @Override
        public String template() {
            return "<Self />";
        }
    }

    public record ScalarProps(
            double amount,
            Double discount,
            float shipping,
            Float tax,
            int count,
            Integer bonus,
            long id,
            Long timestamp,
            short code,
            Short altCode,
            byte level,
            Byte altLevel,
            char symbol,
            Character altSymbol,
            boolean active,
            Boolean approved,
            Status status
    ) implements JssrComponent {
        @Override
        public String template() {
            return "amount=" + amount +
                    ", discount=" + discount +
                    ", shipping=" + shipping +
                    ", tax=" + tax +
                    ", count=" + count +
                    ", bonus=" + bonus +
                    ", id=" + id +
                    ", timestamp=" + timestamp +
                    ", code=" + code +
                    ", altCode=" + altCode +
                    ", level=" + level +
                    ", altLevel=" + altLevel +
                    ", symbol=" + symbol +
                    ", altSymbol=" + altSymbol +
                    ", active=" + active +
                    ", approved=" + approved +
                    ", status=" + status;
        }
    }

    public record Badge(String text) implements JssrComponent {
        @Override
        public String template() {
            return "<span class=\"badge\">${text}</span>";
        }
    }

    public record Container(String name) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div>
                    <Link href="/users/42" label="View User" />
                    <HtmxButton hxGet="/tasks/42" label="Fetch Task" />
                    <ApiButton endpoint="https://example.com/api" />
                    <ScalarProps amount="19.95" discount="2.50" shipping="4.99" tax="1.20" count="5" bonus="10" id="1000" timestamp="1700000000" code="12" altCode="34" level="1" altLevel="2" symbol="$" altSymbol="#" active="true" approved="false" status="ACTIVE" />
                </div>
                """;
        }
    }

    public record TestDefaults(String text, double amount, float weight, int count, boolean flag) implements JssrComponent {
        @Override
        public String template() {
            return text + "|" + amount + "|" + weight + "|" + count + "|" + flag;
        }
    }

    public record DefaultWrapper() implements JssrComponent {
        @Override
        public String template() {
            return "<TestDefaults />";
        }
    }

    @BeforeEach
    void setUp() {
        JssrComponent.REGISTRY.clear();
        JssrComponent.register("Link", Link.class);
        JssrComponent.register("Parent", Parent.class);
        JssrComponent.register("HtmxButton", HtmxButton.class);
        JssrComponent.register("ApiButton", ApiButton.class);
        JssrComponent.register("ScalarProps", ScalarProps.class);
        JssrComponent.register("Badge", Badge.class);
        JssrComponent.register("TestDefaults", TestDefaults.class);
        JssrComponent.register("TaskItem", TaskItem.class);
        JssrComponent.register("PackagePrivateCard", PackagePrivateCard.class);
        JssrComponent.register("Card", Card.class);
        JssrComponent.register("Self", Self.class);
    }

    @Test
    @DisplayName("HTML interpolation should be HTML-escaped by default to prevent XSS")
    void testHtmlEscapingByDefault() {
        UserPage xss = new UserPage("<script>alert(1)</script>");
        assertEquals("<h1>&lt;script&gt;alert(1)&lt;/script&gt;</h1>", xss.render());

        UserPage specialChars = new UserPage("Fish & Chips <Salt> \"Pepper\" 'Vinegar'");
        assertEquals("<h1>Fish &amp; Chips &lt;Salt&gt; &quot;Pepper&quot; &#39;Vinegar&#39;</h1>", specialChars.render());
    }

    @Test
    @DisplayName("Parent-to-child dynamic props containing HTML entities or & should NOT be double-escaped")
    void testParentToChildPropsNoDoubleEscaping() {
        Parent parent = new Parent("/users?a=1&b=2", "Tom & Jerry");
        String html = parent.render();

        assertEquals("<a href=\"/users?a=1&amp;b=2\">Tom &amp; Jerry</a>", html);
        assertFalse(html.contains("&amp;amp;"));
    }

    @Test
    @DisplayName("Unsafe URL protocols like javascript:, vbscript:, data: should be sanitized via SafeUrl")
    void testUnsafeUrlSanitization() {
        assertEquals("about:blank", SafeUrl.sanitize("javascript:alert(1)"));
        assertEquals("about:blank", SafeUrl.sanitize("vbscript:msgbox(1)"));
        assertEquals("about:blank", SafeUrl.sanitize("data:text/html,<script>alert(1)</script>"));
        assertEquals("/users/42", SafeUrl.sanitize("/users/42"));
    }

    @Test
    @DisplayName("Unknown component prop attributes (typos) should throw an explicit IllegalArgumentException")
    void testStrictPropsValidation() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            JssrComponent.processCustomTags("<Link hreef=\"/users\" label=\"Users\" />");
        });
        assertTrue(exception.getMessage().contains("Unknown attribute 'hreef' specified for JSSR component <Link>"));
    }

    @Test
    @DisplayName("Paired component tags like <Card>BODY</Card> should pass inner content to children prop")
    void testPairedComponentTags() {
        String html = JssrComponent.processCustomTags("<Card><h1>Header</h1></Card>");
        assertEquals("<div class=\"card\"><h1>Header</h1></div>", html);
    }

    @Test
    @DisplayName("Unclosed component tags should throw an explicit IllegalArgumentException")
    void testUnclosedTagParsingError() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            JssrComponent.processCustomTags("<Link href=\"/x\" label=\"X\"> BODY");
        });
        assertTrue(exception.getMessage().contains("Unclosed JSSR component tag <Link>"));
    }

    @Test
    @DisplayName("Component recursion depth exceeding limit should throw an IllegalStateException instead of StackOverflowError")
    void testRecursionLimitProtection() {
        Exception exception = assertThrows(IllegalStateException.class, () -> {
            new Self().render();
        });
        assertTrue(exception.getMessage().contains("recursion limit exceeded"));
    }

    @Test
    @DisplayName("Unquoted URL attributes containing slashes like href=/users/42 should parse correctly")
    void testUnquotedUrlParsing() {
        String html = JssrComponent.processCustomTags("<Link href=/users/42 label=View />");
        assertEquals("<a href=\"/users/42\">View</a>", html);
    }

    @Test
    @DisplayName("Mixed-case enum constants like InProgress or Completed should convert correctly")
    void testMixedCaseEnumParsing() {
        TaskItem task = new TaskItem(MixedStatus.InProgress);
        assertEquals("status=InProgress", task.render());

        String html = JssrComponent.processCustomTags("<TaskItem status=\"InProgress\" />");
        assertEquals("status=InProgress", html);
    }

    @Test
    @DisplayName("Package-private component records should be accessible and render properly")
    void testPackagePrivateComponentAccess() {
        String html = JssrComponent.processCustomTags("<PackagePrivateCard title=\"Secret Card\" />");
        assertEquals("<div class=\"card\">Secret Card</div>", html);
    }

    @Test
    @DisplayName("RawHtml wrapper and child JssrComponents should bypass HTML escaping")
    void testRawHtmlAndNestedComponentsBypassEscaping() {
        Badge badge = new Badge("Pro User");
        ArticlePage article = new ArticlePage("Safety Guide", RawHtml.of("<p>Paragraph with <b>bold</b> text.</p>"), badge);

        String rendered = article.render();
        assertTrue(rendered.contains("<h2>Safety Guide</h2>"));
        assertTrue(rendered.contains("<div class=\"content\"><p>Paragraph with <b>bold</b> text.</p></div>"));
        assertTrue(rendered.contains("<div class=\"badge\"><span class=\"badge\">Pro User</span></div>"));
    }

    @Test
    @DisplayName("Component attributes containing '/' such as URLs and HTMX routes should parse correctly")
    void testAttributeUrlsParsing() {
        Container container = new Container("Dashboard");
        String html = container.render();

        assertTrue(html.contains("<a href=\"/users/42\">View User</a>"));
        assertTrue(html.contains("<button hx-get=\"/tasks/42\">Fetch Task</button>"));
        assertTrue(html.contains("<button data-endpoint=\"https://example.com/api\">Call API</button>"));
    }

    @Test
    @DisplayName("Scalar properties including double, float, short, byte, char, wrapper types, and Enums should convert correctly")
    void testScalarPropertyConversions() {
        Container container = new Container("Test");
        String html = container.render();

        assertTrue(html.contains("amount=19.95"));
        assertTrue(html.contains("discount=2.5"));
        assertTrue(html.contains("shipping=4.99"));
        assertTrue(html.contains("tax=1.2"));
        assertTrue(html.contains("count=5"));
        assertTrue(html.contains("bonus=10"));
        assertTrue(html.contains("id=1000"));
        assertTrue(html.contains("timestamp=1700000000"));
        assertTrue(html.contains("code=12"));
        assertTrue(html.contains("altCode=34"));
        assertTrue(html.contains("level=1"));
        assertTrue(html.contains("altLevel=2"));
        assertTrue(html.contains("symbol=$"));
        assertTrue(html.contains("altSymbol=#"));
        assertTrue(html.contains("active=true"));
        assertTrue(html.contains("approved=false"));
        assertTrue(html.contains("status=ACTIVE"));
    }

    @Test
    @DisplayName("Missing component attributes should fall back to standard default values")
    void testMissingAttributesDefaultValues() {
        DefaultWrapper wrapper = new DefaultWrapper();
        String result = wrapper.render();
        assertEquals("null|0.0|0.0|0|false", result);
    }
}

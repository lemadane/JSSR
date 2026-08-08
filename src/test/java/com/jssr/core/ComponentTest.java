package com.jssr.core;

import java.util.List;
import java.util.Optional;
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

    public record Link(SafeUrl href, String label) implements JssrComponent {
        @Override
        public String template() {
            return "<a href=\"${href}\">${label}</a>";
        }
    }

    public record UnsafeStringLink(String href, String label) implements JssrComponent {
        @Override
        public String template() {
            return "<a href=\"${href}\">${label}</a>";
        }
    }

    public record Parent(SafeUrl href, String label) implements JssrComponent {
        @Override
        public String template() {
            return "<Link href=\"${href}\" label=\"${label}\" />";
        }
    }

    public record UserPageWithCard(String username) implements JssrComponent {
        @Override
        public String template() {
            return """
                <Card>
                    <p>${username}</p>
                </Card>
                """;
        }
    }

    public record CascadingExample(String first, String second) implements JssrComponent {
        @Override
        public String template() {
            return "<p>${first}</p>";
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

    public record TestDefaults(
            Optional<String> text,
            Optional<Double> amount,
            Optional<Float> weight,
            Optional<Integer> count,
            Optional<Boolean> flag
    ) implements JssrComponent {
        @Override
        public String template() {
            return text.orElse("null") + "|" + amount.orElse(0.0) + "|" + weight.orElse(0.0f) + "|" + count.orElse(0) + "|" + flag.orElse(false);
        }
    }

    public record DefaultWrapper() implements JssrComponent {
        @Override
        public String template() {
            return "<TestDefaults />";
        }
    }

    public record UserProfile(String name) {}

    public record NestedPropComponent(UserProfile user) implements JssrComponent {
        @Override
        public String template() {
            return "<h1>${user.name}</h1>";
        }
    }

    public record UnquotedAttrComponent(String title) implements JssrComponent {
        @Override
        public String template() {
            return "<div title=${title}>Hello</div>";
        }
    }

    public record FreeStandingStringComponent(String extra) implements JssrComponent {
        @Override
        public String template() {
            return "<button ${extra}>Click</button>";
        }
    }

    public record FreeStandingBooleanComponent(BooleanAttribute activeAttr, HtmlAttribute dataAttr, boolean disabled) implements JssrComponent {
        @Override
        public String template() {
            return "<input ${activeAttr} ${dataAttr} ${disabled} />";
        }
    }

    public record SrcdocComponent(String html) implements JssrComponent {
        @Override
        public String template() {
            return "<iframe srcdoc=\"${html}\"></iframe>";
        }
    }

    public record AlpineComponent(String expr) implements JssrComponent {
        @Override
        public String template() {
            return "<div x-init=\"${expr}\">Alpine</div>";
        }
    }

    public record HtmxOnComponent(String handler) implements JssrComponent {
        @Override
        public String template() {
            return "<button hx-on:click=\"${handler}\">HTMX</button>";
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
        JssrComponent.register("UserPageWithCard", UserPageWithCard.class);
    }

    @Test
    @DisplayName("Self-closing same-type nested components like <Card><Card /></Card> should parse cleanly")
    void testSelfClosingNestedSameTypeComponents() {
        String html = JssrComponent.processCustomTags("<Card><Card /></Card>");
        assertEquals("<div class=\"card\"><div class=\"card\"></div></div>", html);
    }

    @Test
    @DisplayName("Component tags inside <script>, <style>, and HTML comments should be ignored")
    void testParserScriptCommentAttributeIsolation() {
        String input = """
            <script>
                const template = "<Badge text='Hello' />";
            </script>
            <!-- <Badge text="Hidden" /> -->
            """;
        String html = JssrComponent.processCustomTags(input);

        assertTrue(html.contains("const template = \"<Badge text='Hello' />\";"));
        assertTrue(html.contains("<!-- <Badge text=\"Hidden\" /> -->"));
    }

    @Test
    @DisplayName("Single-pass variable interpolation should prevent cascading placeholder evaluation")
    void testSinglePassVariableInterpolation() {
        CascadingExample example = new CascadingExample("${second}", "SECRET");
        String html = example.render();

        assertEquals("<p>${second}</p>", html);
        assertFalse(html.contains("SECRET"));
    }

    @Test
    @DisplayName("Components that do NOT declare a children or content prop should reject paired tag body content")
    void testStrictPairedBodyValidation() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            JssrComponent.processCustomTags("<Link href=\"/users\" label=\"Users\">THIS SHOULD FAIL</Link>");
        });
        assertTrue(exception.getMessage().contains("Component <Link> does not accept paired body content"));
    }

    @Test
    @DisplayName("SafeUrl should be HTML-escaped inside HTML attributes to prevent attribute injection XSS")
    void testSafeUrlAttributeInjectionPrevention() {
        Link link = new Link(SafeUrl.of("https://example.com/\" onmouseover=\"alert(1)"), "Click");
        String html = link.render();

        assertTrue(html.contains("href=\"https://example.com/&quot; onmouseover=&quot;alert(1)\""));
        assertFalse(html.contains("\" onmouseover=\""));
    }

    @Test
    @DisplayName("SafeUrl should decode HTML entities before checking scheme to block entity-encoded javascript: XSS")
    void testSafeUrlHtmlEntityBypassPrevention() {
        Link link = new Link(SafeUrl.of("java&#115;cript:alert(1)"), "Click");
        String html = link.render();

        assertTrue(html.contains("href=\"about:blank\""));
        assertFalse(html.contains("javascript:"));
    }

    @Test
    @DisplayName("Paired RawHtml children should preserve pre-escaped user data without re-activating XSS tags")
    void testPairedRawHtmlChildrenXssPreservation() {
        UserPageWithCard page = new UserPageWithCard("<img src=x onerror=alert(1)>");
        String html = page.render();

        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"));
        assertFalse(html.contains("<img src=x onerror=alert(1)>"));
    }

    @Test
    @DisplayName("Nested same-type paired components like <Card><Card>Inner</Card></Card> should match nesting levels correctly")
    void testNestedSameTypePairedComponents() {
        String html = JssrComponent.processCustomTags("<Card><Card>Inner</Card></Card>");
        assertEquals("<div class=\"card\"><div class=\"card\">Inner</div></div>", html);
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
        Parent parent = new Parent(SafeUrl.of("/users?a=1&b=2"), "Tom & Jerry");
        String html = parent.render();

        assertEquals("<a href=\"/users?a=1&amp;b=2\">Tom &amp; Jerry</a>", html);
        assertFalse(html.contains("&amp;amp;"));
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
    @DisplayName("Unquoted URL attributes containing slashes like href=/users/42 should parse correctly in custom tags")
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
    @DisplayName("Missing component attributes with Optional types should fall back to Optional.empty()")
    void testMissingAttributesDefaultValues() {
        DefaultWrapper wrapper = new DefaultWrapper();
        String result = wrapper.render();
        assertEquals("null|0.0|0.0|0|false", result);
    }

    public record ScriptComponent(String data) implements JssrComponent {
        @Override
        public String template() {
            return "<script>const val = \"${data}\";</script>";
        }
    }

    public record StyleComponent(String color) implements JssrComponent {
        @Override
        public String template() {
            return "<style>body { color: ${color}; }</style>";
        }
    }

    public record CommentComponent(String text) implements JssrComponent {
        @Override
        public String template() {
            return "<!-- ${text} -->";
        }
    }

    public record TrustedHtmlComponent(RawHtml html) implements JssrComponent {
        @Override
        public String template() {
            return "<div>${html}</div>";
        }
    }

    @Test
    @DisplayName("Interpolation inside <script> blocks should throw an IllegalArgumentException")
    void testScriptInterpolationRejection() {
        ScriptComponent comp = new ScriptComponent("hello");
        Exception ex = assertThrows(IllegalArgumentException.class, comp::render);
        assertTrue(ex.getMessage().contains("is not allowed inside <script> blocks"));
    }

    @Test
    @DisplayName("Interpolation inside <style> blocks should throw an IllegalArgumentException")
    void testStyleInterpolationRejection() {
        StyleComponent comp = new StyleComponent("red");
        Exception ex = assertThrows(IllegalArgumentException.class, comp::render);
        assertTrue(ex.getMessage().contains("is not allowed inside <style> blocks"));
    }

    @Test
    @DisplayName("Interpolation inside HTML comments should throw an IllegalArgumentException")
    void testCommentInterpolationRejection() {
        CommentComponent comp = new CommentComponent("comment");
        Exception ex = assertThrows(IllegalArgumentException.class, comp::render);
        assertTrue(ex.getMessage().contains("is not allowed inside HTML comments"));
    }

    public record OnclickComponent(String action) implements JssrComponent {
        @Override
        public String template() {
            return "<button onclick=\"${action}\">Click</button>";
        }
    }

    public record StyleAttrComponent(String css) implements JssrComponent {
        @Override
        public String template() {
            return "<div style=\"${css}\">Content</div>";
        }
    }

    @Test
    @DisplayName("Interpolation inside inline event handler attributes (onclick) should throw an IllegalArgumentException")
    void testOnclickInterpolationRejection() {
        OnclickComponent comp = new OnclickComponent("alert(1)");
        Exception ex = assertThrows(IllegalArgumentException.class, comp::render);
        assertTrue(ex.getMessage().contains("is not allowed inside inline event handler attribute"));
    }

    @Test
    @DisplayName("Interpolation inside inline style attributes (style=) should throw an IllegalArgumentException")
    void testStyleAttrInterpolationRejection() {
        StyleAttrComponent comp = new StyleAttrComponent("color: red");
        Exception ex = assertThrows(IllegalArgumentException.class, comp::render);
        assertTrue(ex.getMessage().contains("is not allowed inside inline style attribute"));
    }

    @Test
    @DisplayName("JssrComponent.trustedHtml and RawHtml.trustedHtml should create trusted unescaped HTML wrappers")
    void testTrustedHtmlHelper() {
        TrustedHtmlComponent comp = new TrustedHtmlComponent(JssrComponent.trustedHtml("<span>Safe Markup</span>"));
        assertEquals("<div><span>Safe Markup</span></div>", comp.render());

        TrustedHtmlComponent comp2 = new TrustedHtmlComponent(RawHtml.trustedHtml("<strong>Bold Text</strong>"));
        assertEquals("<div><strong>Bold Text</strong></div>", comp2.render());
    }

    public record GenericContainer(String input) implements JssrComponent {
        @Override
        public String template() {
            return "<div title='${input}' data-value=\"${input}\"><p>${input}</p><textarea>${input}</textarea></div>";
        }
    }

    @Test
    @DisplayName("Comprehensive XSS security regression suite testing dangerous attack payloads")
    void testXssSecurityRegressionSuite() {
        List<String> xssPayloads = List.of(
            "<script>alert(1)</script>",
            "<img src=x onerror=alert(1)>",
            "\"><script>alert(1)</script>",
            "' onmouseover='alert(1)",
            "</textarea><script>alert(1)</script>"
        );

        for (String payload : xssPayloads) {
            GenericContainer container = new GenericContainer(payload);
            String html = container.render();

            assertFalse(html.contains("<script>alert(1)</script>"), "Failed for payload: " + payload);
            assertFalse(html.contains("<img src=x onerror=alert(1)>"), "Failed for payload: " + payload);
            assertFalse(html.contains("</textarea><script>"), "Failed for payload: " + payload);
            assertFalse(html.contains("' onmouseover='"), "Failed for payload: " + payload);
            assertFalse(html.contains("\"><script>"), "Failed for payload: " + payload);

            assertTrue(html.contains("&lt;") || html.contains("&gt;") || html.contains("&quot;") || html.contains("&#39;"));
        }
    }

    public record CheckboxForm(BooleanAttribute activeChecked, BooleanAttribute notificationsChecked) implements JssrComponent {
        public static CheckboxForm of(boolean active, boolean notifications) {
            return new CheckboxForm(
                BooleanAttribute.of("checked", active),
                BooleanAttribute.of("checked", notifications)
            );
        }

        @Override
        public String template() {
            return """
                <form>
                    <input type="checkbox" name="active" value="true" ${activeChecked} />
                    <input type="checkbox" name="notifications" value="true" ${notificationsChecked} />
                </form>
                """;
        }
    }

    public record RadioForm(BooleanAttribute adminChecked, BooleanAttribute userChecked) implements JssrComponent {
        public static RadioForm of(String selectedRole) {
            return new RadioForm(
                BooleanAttribute.of("checked", "ADMIN".equalsIgnoreCase(selectedRole)),
                BooleanAttribute.of("checked", "USER".equalsIgnoreCase(selectedRole))
            );
        }

        @Override
        public String template() {
            return """
                <form>
                    <input type="radio" name="role" value="ADMIN" ${adminChecked} />
                    <input type="radio" name="role" value="USER" ${userChecked} />
                </form>
                """;
        }
    }

    @Test
    @DisplayName("Checkbox components should render checked attribute based on BooleanAttribute state")
    void testCheckboxFormRendering() {
        CheckboxForm form1 = CheckboxForm.of(true, false);
        String html1 = form1.render();
        assertTrue(html1.contains("name=\"active\" value=\"true\" checked"));
        assertFalse(html1.contains("name=\"notifications\" value=\"true\" checked"));

        CheckboxForm form2 = CheckboxForm.of(false, true);
        String html2 = form2.render();
        assertFalse(html2.contains("name=\"active\" value=\"true\" checked"));
        assertTrue(html2.contains("name=\"notifications\" value=\"true\" checked"));
    }

    @Test
    @DisplayName("Radio button components should render checked attribute based on selected BooleanAttribute state")
    void testRadioFormRendering() {
        RadioForm adminForm = RadioForm.of("ADMIN");
        String htmlAdmin = adminForm.render();
        assertTrue(htmlAdmin.contains("value=\"ADMIN\" checked"));
        assertFalse(htmlAdmin.contains("value=\"USER\" checked"));

        RadioForm userForm = RadioForm.of("USER");
        String htmlUser = userForm.render();
        assertFalse(htmlUser.contains("value=\"ADMIN\" checked"));
        assertTrue(htmlUser.contains("value=\"USER\" checked"));
    }

    // ----------------------------------------------------------------------------------
    // NEW PRODUCTION HARDENING & SECURITY CONTEXT REJECTION TESTS
    // ----------------------------------------------------------------------------------

    @Test
    @DisplayName("Free-standing String variable interpolation between attributes should throw IllegalArgumentException")
    void testFreeStandingStringAttributeInjectionRejection() {
        FreeStandingStringComponent comp = new FreeStandingStringComponent("onmouseover=alert(1)");
        Exception ex = assertThrows(IllegalArgumentException.class, comp::render);
        assertTrue(ex.getMessage().contains("free-standing HTML attribute position is forbidden"));
    }

    @Test
    @DisplayName("Free-standing BooleanAttribute, HtmlAttribute, and boolean fields should render safely")
    void testFreeStandingTypedAttributesRendering() {
        FreeStandingBooleanComponent comp = new FreeStandingBooleanComponent(
            BooleanAttribute.of("checked", true),
            HtmlAttribute.of("data-test", "val"),
            true
        );
        String html = comp.render();
        assertTrue(html.contains("<input checked data-test=\"val\" disabled />"));
    }

    @Test
    @DisplayName("Unquoted HTML attribute interpolation should throw IllegalArgumentException")
    void testUnquotedAttributeInterpolationRejection() {
        UnquotedAttrComponent comp = new UnquotedAttrComponent("hello");
        Exception ex = assertThrows(IllegalArgumentException.class, comp::render);
        assertTrue(ex.getMessage().contains("unquoted HTML attribute is forbidden"));
    }

    @Test
    @DisplayName("URL attributes (href) strictly require SafeUrl type and reject raw String variables")
    void testUrlAttributeSafeUrlRequirement() {
        UnsafeStringLink link = new UnsafeStringLink("https://example.com", "Click");
        Exception ex = assertThrows(IllegalArgumentException.class, link::render);
        assertTrue(ex.getMessage().contains("requires a SafeUrl field type instead of String"));
    }

    @Test
    @DisplayName("Variable interpolation inside iframe srcdoc attribute should throw IllegalArgumentException")
    void testSrcdocAttributeRejection() {
        SrcdocComponent comp = new SrcdocComponent("<script>alert(1)</script>");
        Exception ex = assertThrows(IllegalArgumentException.class, comp::render);
        assertTrue(ex.getMessage().contains("inside 'srcdoc' attribute is forbidden"));
    }

    @Test
    @DisplayName("Variable interpolation inside Alpine.js attributes (x-init, @click, :class) should throw IllegalArgumentException")
    void testAlpineAttributeRejection() {
        AlpineComponent comp = new AlpineComponent("alert(1)");
        Exception ex = assertThrows(IllegalArgumentException.class, comp::render);
        assertTrue(ex.getMessage().contains("inside executable framework attribute"));
    }

    @Test
    @DisplayName("Variable interpolation inside HTMX event attributes (hx-on:click) should throw IllegalArgumentException")
    void testHtmxAttributeRejection() {
        HtmxOnComponent comp = new HtmxOnComponent("alert(1)");
        Exception ex = assertThrows(IllegalArgumentException.class, comp::render);
        assertTrue(ex.getMessage().contains("inside executable framework attribute"));
    }

    @Test
    @DisplayName("Nested property evaluation (${user.name}) should resolve correctly")
    void testNestedPropertyAccess() {
        NestedPropComponent comp = new NestedPropComponent(new UserProfile("Charlie"));
        assertEquals("<h1>Charlie</h1>", comp.render());
    }

    public record InvalidPropComp(String name) implements JssrComponent {
        @Override
        public String template() {
            return "<h1>${usernmae}</h1>";
        }
    }

    public record WhitespaceHrefLink(String href) implements JssrComponent {
        @Override
        public String template() {
            return "<a href = \"${href}\">Link</a>";
        }
    }

    public record WhitespaceOnclickComponent(String action) implements JssrComponent {
        @Override
        public String template() {
            return "<button onclick \t = \n \"${action}\">Click</button>";
        }
    }

    public record WhitespaceStyleComponent(String css) implements JssrComponent {
        @Override
        public String template() {
            return "<div style = \"${css}\">Text</div>";
        }
    }

    public record WhitespaceSrcdocComponent(String html) implements JssrComponent {
        @Override
        public String template() {
            return "<iframe srcdoc = \"${html}\"></iframe>";
        }
    }

    public record WhitespaceAlpineComponent(String expr) implements JssrComponent {
        @Override
        public String template() {
            return "<div x-init = \"${expr}\">Text</div>";
        }
    }

    public record WhitespaceHtmxComponent(String handler) implements JssrComponent {
        @Override
        public String template() {
            return "<button hx-on:click = \"${handler}\">Text</button>";
        }
    }

    public record RawHtmlAttrComponent(RawHtml title) implements JssrComponent {
        @Override
        public String template() {
            return "<div title=\"${title}\">Text</div>";
        }
    }

    @Test
    @DisplayName("Unknown template variable placeholders should fail fast with explicit IllegalArgumentException")
    void testUnknownVariableFailFast() {
        InvalidPropComp comp = new InvalidPropComp("Charlie");
        Exception ex = assertThrows(IllegalArgumentException.class, comp::render);
        assertTrue(ex.getMessage().contains("Unknown JSSR interpolation property '${usernmae}'"));
    }

    @Test
    @DisplayName("Whitespace around '=' should NOT bypass attribute security context detection")
    void testWhitespaceAroundEqualsAttributeProtection() {
        // href = "${href}" with raw String should require SafeUrl
        Exception ex1 = assertThrows(IllegalArgumentException.class, () -> new WhitespaceHrefLink("javascript:alert(1)").render());
        assertTrue(ex1.getMessage().contains("requires a SafeUrl field type"));

        // onclick = "${action}" should be rejected
        Exception ex2 = assertThrows(IllegalArgumentException.class, () -> new WhitespaceOnclickComponent("alert(1)").render());
        assertTrue(ex2.getMessage().contains("inline event handler attribute"));

        // style = "${css}" should be rejected
        Exception ex3 = assertThrows(IllegalArgumentException.class, () -> new WhitespaceStyleComponent("color:red").render());
        assertTrue(ex3.getMessage().contains("inline style attribute"));

        // srcdoc = "${html}" should be rejected
        Exception ex4 = assertThrows(IllegalArgumentException.class, () -> new WhitespaceSrcdocComponent("<script>alert(1)</script>").render());
        assertTrue(ex4.getMessage().contains("inside 'srcdoc' attribute is forbidden"));

        // x-init = "${expr}" should be rejected
        Exception ex5 = assertThrows(IllegalArgumentException.class, () -> new WhitespaceAlpineComponent("alert(1)").render());
        assertTrue(ex5.getMessage().contains("executable framework attribute"));

        // hx-on:click = "${handler}" should be rejected
        Exception ex6 = assertThrows(IllegalArgumentException.class, () -> new WhitespaceHtmxComponent("alert(1)").render());
        assertTrue(ex6.getMessage().contains("executable framework attribute"));
    }

    @Test
    @DisplayName("BooleanAttribute name must be strictly validated against standard HTML boolean attribute allowlist")
    void testBooleanAttributeNameAllowlistValidation() {
        // Valid boolean attributes
        assertDoesNotThrow(() -> BooleanAttribute.of("checked", true));
        assertDoesNotThrow(() -> BooleanAttribute.of("disabled", true));
        assertDoesNotThrow(() -> BooleanAttribute.of("selected", true));

        // Invalid / injected boolean attribute names
        Exception ex1 = assertThrows(IllegalArgumentException.class, () -> BooleanAttribute.of("autofocus onfocus=alert(1)", true));
        assertTrue(ex1.getMessage().contains("Invalid or unsafe boolean HTML attribute name"));

        Exception ex2 = assertThrows(IllegalArgumentException.class, () -> BooleanAttribute.of("onclick", true));
        assertTrue(ex2.getMessage().contains("Invalid or unsafe boolean HTML attribute name"));
    }

    @Test
    @DisplayName("HtmlAttribute name must be validated against spaces/special chars and security blocklist")
    void testHtmlAttributeNameValidationAndBlocklist() {
        // Valid custom/data attributes
        assertDoesNotThrow(() -> HtmlAttribute.of("data-role", "admin"));
        assertDoesNotThrow(() -> HtmlAttribute.of("aria-label", "Close"));
        assertDoesNotThrow(() -> HtmlAttribute.of("title", "Help text"));

        // Forbidden attribute names: href (URL), onclick, x-init, spaces/attribute injection
        Exception ex1 = assertThrows(IllegalArgumentException.class, () -> HtmlAttribute.of("href", "javascript:alert(1)"));
        assertTrue(ex1.getMessage().contains("Unsafe HTML attribute name 'href'"));

        Exception ex2 = assertThrows(IllegalArgumentException.class, () -> HtmlAttribute.of("onclick", "alert(1)"));
        assertTrue(ex2.getMessage().contains("Unsafe HTML attribute name 'onclick'"));

        Exception ex3 = assertThrows(IllegalArgumentException.class, () -> HtmlAttribute.of("data-x onmouseover", "alert(1)"));
        assertTrue(ex3.getMessage().contains("Invalid HTML attribute name"));
    }

    @Test
    @DisplayName("RawHtml cannot be interpolated inside an HTML tag attribute")
    void testRawHtmlInAttributeRejection() {
        RawHtmlAttrComponent comp = new RawHtmlAttrComponent(RawHtml.of("\" onmouseover=\"alert(1)"));
        Exception ex = assertThrows(IllegalArgumentException.class, comp::render);
        assertTrue(ex.getMessage().contains("RawHtml cannot be interpolated inside an HTML attribute"));
    }

    public record ObjectDataComp(String resource) implements JssrComponent {
        @Override public String template() { return "<object data=\"${resource}\"></object>"; }
    }

    public record SafeObjectDataComp(SafeUrl resource) implements JssrComponent {
        @Override public String template() { return "<object data=\"${resource}\"></object>"; }
    }

    @Test
    @DisplayName("URL-valued attributes like <object data=\"${resource}\"> strictly require SafeUrl and block raw String interpolation")
    void testObjectDataUrlAttributeProtection() {
        // String resource should be rejected
        ObjectDataComp unsafeComp = new ObjectDataComp("javascript:alert(1)");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, unsafeComp::render);
        assertTrue(ex.getMessage().contains("inside URL attribute 'data' requires a SafeUrl field type"));

        // SafeUrl resource should render safely
        SafeObjectDataComp safeComp = new SafeObjectDataComp(SafeUrl.of("/media/widget.swf"));
        assertEquals("<object data=\"/media/widget.swf\"></object>", safeComp.render());
    }

    @Test
    @DisplayName("HtmlAttribute forbids URL-valued attribute names like 'data', 'srcset', 'ping', and 'codebase'")
    void testHtmlAttributeExpandedUrlBlocklist() {
        assertThrows(IllegalArgumentException.class, () -> HtmlAttribute.of("data", "javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> HtmlAttribute.of("srcset", "javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> HtmlAttribute.of("ping", "javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> HtmlAttribute.of("codebase", "javascript:alert(1)"));
    }

    public record UnsafeSrcSetComp(String srcset) implements JssrComponent {
        @Override public String template() { return "<img srcset=\"${srcset}\" />"; }
    }

    public record UnsafeSrcSetSafeUrlComp(SafeUrl srcset) implements JssrComponent {
        @Override public String template() { return "<img srcset=\"${srcset}\" />"; }
    }

    public record SafeSrcSetComp(SafeSrcSet srcset) implements JssrComponent {
        @Override public String template() { return "<img srcset=\"${srcset}\" />"; }
    }

    @Test
    @DisplayName("srcset attributes strictly require SafeSrcSet and individually sanitize every comma-separated candidate URL")
    void testSafeSrcSetMultiUrlSanitization() {
        // Raw String and raw SafeUrl must be rejected
        UnsafeSrcSetComp unsafe1 = new UnsafeSrcSetComp("https://safe.example/a.png 1x, javascript:alert(1) 2x");
        assertThrows(IllegalArgumentException.class, unsafe1::render);

        UnsafeSrcSetSafeUrlComp unsafe2 = new UnsafeSrcSetSafeUrlComp(SafeUrl.of("https://safe.example/a.png 1x, javascript:alert(1) 2x"));
        assertThrows(IllegalArgumentException.class, unsafe2::render);

        // SafeSrcSet must sanitize individual candidates
        SafeSrcSet safeSet = SafeSrcSet.of("https://safe.example/a.png 1x, javascript:alert(1) 2x, /img/c.png 1000w");
        SafeSrcSetComp comp = new SafeSrcSetComp(safeSet);

        String html = comp.render();
        assertTrue(html.contains("https://safe.example/a.png 1x"));
        assertTrue(html.contains("about:blank 2x"));
        assertTrue(html.contains("/img/c.png 1000w"));
        assertFalse(html.contains("javascript:"));
    }

    public record UnsafePingComp(String urls) implements JssrComponent {
        @Override public String template() { return "<a href=\"/link\" ping=\"${urls}\">Click</a>"; }
    }

    public record SafePingComp(SafeUrlList urls) implements JssrComponent {
        @Override public String template() { return "<a href=\"/link\" ping=\"${urls}\">Click</a>"; }
    }

    @Test
    @DisplayName("ping attributes strictly require SafeUrlList and individually sanitize space-separated URLs")
    void testSafeUrlListPingSanitization() {
        // Raw String must be rejected
        UnsafePingComp unsafe = new UnsafePingComp("https://analytics.org/ping javascript:alert(1)");
        assertThrows(IllegalArgumentException.class, unsafe::render);

        // SafeUrlList must sanitize individual URLs
        SafeUrlList list = SafeUrlList.of("https://analytics.org/ping javascript:alert(1) http://metrics.com");
        SafePingComp comp = new SafePingComp(list);

        String html = comp.render();
        assertTrue(html.contains("https://analytics.org/ping"));
        assertTrue(html.contains("about:blank"));
        assertTrue(html.contains("http://metrics.com"));
        assertFalse(html.contains("javascript:"));
    }
}



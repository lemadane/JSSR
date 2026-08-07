package com.jssr.e2e;

import com.jssr.core.JssrComponent;
import com.jssr.core.RawHtml;
import com.jssr.core.SafeUrl;
import com.jssr.e2e.app.TestApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class UserCrudE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("1. Page & Layout SSR Rendering Tests")
    class PageRenderingTests {

        @Test
        @DisplayName("GET / should render full SSR page with JSSR layout, scripts, navbar and initial seed data")
        void testGetFullPage() throws Exception {
            mockMvc.perform(get("/"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("<!DOCTYPE html>")))
                    .andExpect(content().string(containsString("<title>User Management - JSSR Library Demo</title>")))
                    .andExpect(content().string(containsString("unpkg.com/htmx.org")))
                    .andExpect(content().string(containsString("unpkg.com/alpinejs")))
                    .andExpect(content().string(containsString("cdn.tailwindcss.com")))
                    .andExpect(content().string(containsString("JSSR <span class=\"text-indigo-400 font-normal\">UI Library</span>")))
                    .andExpect(content().string(containsString("Sarah Connor")))
                    .andExpect(content().string(containsString("Alex Mercer")))
                    .andExpect(content().string(containsString("Elena Rostova")))
                    .andExpect(content().string(containsString("Marcus Vance")))
                    .andExpect(content().string(containsString("Chloe Bennett")));
        }

        @Test
        @DisplayName("GET /users should render isolated HTMX list container fragment without outer document shell")
        void testGetUsersPartial() throws Exception {
            mockMvc.perform(get("/users"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(content().string(not(containsString("<!DOCTYPE html>"))))
                    .andExpect(content().string(containsString("id=\"user-list-container\"")))
                    .andExpect(content().string(containsString("Total Users")))
                    .andExpect(content().string(containsString("Sarah Connor")))
                    .andExpect(content().string(containsString("Showing 5 users")));
        }
    }

    @Nested
    @DisplayName("2. UserForm Component Rendering & Props Tests")
    class UserFormComponentTests {

        @Test
        @DisplayName("GET /users/new should render JSSR UserForm creation modal with clean default parameters")
        void testRenderNewUserForm() throws Exception {
            mockMvc.perform(get("/users/new"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("id=\"user-form-modal\"")))
                    .andExpect(content().string(containsString("Create New User")))
                    .andExpect(content().string(containsString("hx-post=\"/users\"")))
                    .andExpect(content().string(containsString("Create User")));
        }

        @Test
        @DisplayName("GET /users/1/edit should render JSSR UserForm edit modal pre-filled via ${fieldName} interpolation without double escaping")
        void testRenderEditUserForm() throws Exception {
            mockMvc.perform(get("/users/1/edit"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("id=\"user-form-modal\"")))
                    .andExpect(content().string(containsString("Edit User")))
                    .andExpect(content().string(containsString("hx-post=\"/users/1\"")))
                    .andExpect(content().string(containsString("value=\"Sarah Connor\"")))
                    .andExpect(content().string(containsString("value=\"sarah.connor@jssr.dev\"")))
                    .andExpect(content().string(containsString("Save Changes")));
        }
    }

    @Nested
    @DisplayName("3. Architecture & Security Issue Prevention Tests")
    class ArchitectureAndSecurityTests {

        public record CustomCard(RawHtml children) implements JssrComponent {
            @Override
            public String template() {
                return "<div class=\"custom-card\">${children}</div>";
            }
        }

        public record DummyLink(SafeUrl href, String label) implements JssrComponent {
            @Override
            public String template() {
                return "<a href=\"${href}\">${label}</a>";
            }
        }

        public record CascadingExample(String first, String second) implements JssrComponent {
            @Override
            public String template() {
                return "<p>${first}</p>";
            }
        }

        public record PageWithCard(String username) implements JssrComponent {
            @Override
            public String template() {
                return """
                    <CustomCard>
                        <p>${username}</p>
                    </CustomCard>
                    """;
            }
        }

        public record RecursiveComp() implements JssrComponent {
            @Override
            public String template() {
                return "<RecursiveComp />";
            }
        }

        @Test
        @DisplayName("1. Parent to Child dynamic props should escape exactly ONCE and prevent double escaping (&amp;amp;)")
        void testPreventDoubleEscaping() throws Exception {
            mockMvc.perform(post("/users")
                            .param("name", "Tom & Jerry")
                            .param("email", "tom.jerry@jssr.dev")
                            .param("role", "Developer")
                            .param("status", "ACTIVE"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Tom &amp; Jerry")))
                    .andExpect(content().string(not(containsString("Tom &amp;amp; Jerry"))));
        }

        @Test
        @DisplayName("2. Single-pass variable interpolation should prevent cascading placeholder replacement")
        void testSinglePassInterpolation() {
            CascadingExample example = new CascadingExample("${second}", "SECRET_DATA");
            String html = example.render();

            assertEquals("<p>${second}</p>", html);
            assertFalse(html.contains("SECRET_DATA"));
        }

        @Test
        @DisplayName("3. Parser should isolate <script>, <style>, and HTML comments from custom tag parsing")
        void testScriptAndCommentParserIsolation() {
            String template = """
                <script>
                    const x = "<DummyLink href='/test' label='Test' />";
                </script>
                <!-- <DummyLink href="/comment" label="Comment" /> -->
                """;
            String html = JssrComponent.processCustomTags(template);

            assertTrue(html.contains("const x = \"<DummyLink href='/test' label='Test' />\";"));
            assertTrue(html.contains("<!-- <DummyLink href=\"/comment\" label=\"Comment\" /> -->"));
        }

        @Test
        @DisplayName("4. Self-closing same-type nested tags (<CustomCard><CustomCard /></CustomCard>) should parse cleanly")
        void testSelfClosingNestedSameTypeTags() {
            JssrComponent.register("CustomCard", CustomCard.class);
            String html = JssrComponent.processCustomTags("<CustomCard><CustomCard /></CustomCard>");
            assertEquals("<div class=\"custom-card\"><div class=\"custom-card\"></div></div>", html);
        }

        @Test
        @DisplayName("5. Components without children/content props should reject paired body content")
        void testRejectPairedBodyForNonContainerComponents() {
            JssrComponent.register("DummyLink", DummyLink.class);
            Exception ex = assertThrows(IllegalArgumentException.class, () -> {
                JssrComponent.processCustomTags("<DummyLink href=\"/users\" label=\"Users\">Dangling Body Content</DummyLink>");
            });
            assertTrue(ex.getMessage().contains("Component <DummyLink> does not accept paired body content"));
        }

        @Test
        @DisplayName("6. SafeUrl should sanitize javascript: XSS payloads to about:blank and HTML escape attributes")
        void testSanitizeUnsafeUrlProtocols() {
            assertEquals("about:blank", SafeUrl.sanitize("javascript:alert(1)"));
            assertEquals("about:blank", SafeUrl.sanitize("java&#115;cript:alert(1)"));
            assertEquals("about:blank", SafeUrl.sanitize("vbscript:msgbox(1)"));
            assertEquals("about:blank", SafeUrl.sanitize("data:text/html,<script>alert(1)</script>"));
            assertEquals("/users/42", SafeUrl.sanitize("/users/42"));

            DummyLink link = new DummyLink(SafeUrl.of("https://example.com/\" onmouseover=\"alert(1)"), "Click");
            String html = link.render();
            assertTrue(html.contains("href=\"https://example.com/&quot; onmouseover=&quot;alert(1)\""));
            assertFalse(html.contains("\" onmouseover=\""));
        }

        @Test
        @DisplayName("7. Paired RawHtml children should preserve pre-escaped user data without re-activating XSS tags")
        void testPairedRawHtmlChildrenXssPreservation() {
            JssrComponent.register("CustomCard", CustomCard.class);
            PageWithCard page = new PageWithCard("<img src=x onerror=alert(1)>");
            String html = page.render();

            assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"));
            assertFalse(html.contains("<img src=x onerror=alert(1)>"));
        }

        @Test
        @DisplayName("8. Unknown JSX attribute typos (e.g. hreef) should throw an explicit IllegalArgumentException")
        void testRejectUnknownAttributeTypos() {
            JssrComponent.register("DummyLink", DummyLink.class);
            Exception ex = assertThrows(IllegalArgumentException.class, () -> {
                JssrComponent.processCustomTags("<DummyLink hreef=\"/users\" label=\"View\" />");
            });
            assertTrue(ex.getMessage().contains("Unknown attribute 'hreef' specified for JSSR component <DummyLink>"));
        }

        @Test
        @DisplayName("9. Component infinite recursion should throw IllegalStateException depth limit error instead of StackOverflowError")
        void testRecursionLimitProtection() {
            JssrComponent.register("RecursiveComp", RecursiveComp.class);
            Exception ex = assertThrows(IllegalStateException.class, () -> {
                new RecursiveComp().render();
            });
            assertTrue(ex.getMessage().contains("recursion limit exceeded"));
        }

        @Test
        @DisplayName("10. Unquoted URL attribute paths (href=/users/42) should parse completely")
        void testUnquotedUrlAttributeParsing() {
            JssrComponent.register("DummyLink", DummyLink.class);
            String html = JssrComponent.processCustomTags("<DummyLink href=/users/42 label=View />");
            assertEquals("<a href=\"/users/42\">View</a>", html);
        }
    }

    @Nested
    @DisplayName("4. Live Search & Dynamic Filtering Tests")
    class SearchAndFilterTests {

        @Test
        @DisplayName("GET /users/search?q=Sarah should return matching user and exclude non-matching users")
        void testSearchByName() throws Exception {
            mockMvc.perform(get("/users/search").param("q", "Sarah"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Sarah Connor")))
                    .andExpect(content().string(not(containsString("Alex Mercer"))))
                    .andExpect(content().string(not(containsString("Elena Rostova"))))
                    .andExpect(content().string(containsString("Showing 1 users")));
        }

        @Test
        @DisplayName("GET /users/search?q=Designer should filter by user role attribute")
        void testSearchByRole() throws Exception {
            mockMvc.perform(get("/users/search").param("q", "Designer"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Elena Rostova")))
                    .andExpect(content().string(containsString("Designer")))
                    .andExpect(content().string(not(containsString("Sarah Connor"))))
                    .andExpect(content().string(containsString("Showing 1 users")));
        }

        @Test
        @DisplayName("GET /users/search?q=nonexistent query should render clean empty state fallback message")
        void testSearchEmptyResults() throws Exception {
            mockMvc.perform(get("/users/search").param("q", "NonExistentUser999"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("No users found matching your criteria")))
                    .andExpect(content().string(containsString("Showing 0 users")));
        }
    }

    @Nested
    @DisplayName("5. User Creation Flow Tests")
    class UserCreationTests {

        @Test
        @DisplayName("POST /users with valid parameters should save user entity, update statistics, and return Toast alert with single-escaped text")
        void testCreateUserSuccess() throws Exception {
            mockMvc.perform(post("/users")
                            .param("name", "Diana & Prince")
                            .param("email", "diana.prince@jssr.dev")
                            .param("role", "Admin")
                            .param("status", "ACTIVE"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Diana &amp; Prince")))
                    .andExpect(content().string(not(containsString("Diana &amp;amp; Prince"))))
                    .andExpect(content().string(containsString("diana.prince@jssr.dev")))
                    .andExpect(content().string(containsString("User &#39;Diana &amp; Prince&#39; created successfully!")))
                    .andExpect(content().string(containsString("Showing 6 users")));
        }
    }

    @Nested
    @DisplayName("6. User Update Flow Tests")
    class UserUpdateTests {

        @Test
        @DisplayName("POST /users/{id} should update user fields and return updated component fragment with single HTML escaping")
        void testUpdateUserSuccess() throws Exception {
            mockMvc.perform(post("/users/2")
                            .param("name", "Tom & Jerry")
                            .param("email", "tom.jerry@jssr.dev")
                            .param("role", "Product Lead")
                            .param("status", "ACTIVE"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Tom &amp; Jerry")))
                    .andExpect(content().string(not(containsString("Tom &amp;amp; Jerry"))))
                    .andExpect(content().string(containsString("tom.jerry@jssr.dev")))
                    .andExpect(content().string(containsString("Product Lead")))
                    .andExpect(content().string(containsString("User &#39;Tom &amp; Jerry&#39; updated successfully!")));
        }

        @Test
        @DisplayName("POST /users/{id} with invalid ID should return error Toast notification")
        void testUpdateUserNotFound() throws Exception {
            mockMvc.perform(post("/users/999999")
                            .param("name", "Ghost User")
                            .param("email", "ghost@jssr.dev")
                            .param("role", "Developer")
                            .param("status", "ACTIVE"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("User not found!")));
        }
    }

    @Nested
    @DisplayName("7. User Status Toggle Flow Tests")
    class UserStatusToggleTests {

        @Test
        @DisplayName("POST /users/{id}/toggle should switch user from INACTIVE to ACTIVE")
        void testToggleUserStatusInactiveToActive() throws Exception {
            mockMvc.perform(post("/users/3/toggle"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Status for &#39;Elena Rostova&#39; changed to ACTIVE")));
        }

        @Test
        @DisplayName("POST /users/{id}/toggle should switch user from ACTIVE to INACTIVE")
        void testToggleUserStatusActiveToInactive() throws Exception {
            mockMvc.perform(post("/users/1/toggle"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Status for &#39;Sarah Connor&#39; changed to INACTIVE")));
        }
    }

    @Nested
    @DisplayName("8. User Deletion Flow Tests")
    class UserDeletionTests {

        @Test
        @DisplayName("DELETE /users/{id} should remove user entity from system and decrement total count")
        void testDeleteUserSuccess() throws Exception {
            mockMvc.perform(delete("/users/2"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("alex.mercer@jssr.dev"))))
                    .andExpect(content().string(containsString("User &#39;Alex Mercer&#39; removed from system.")))
                    .andExpect(content().string(containsString("Showing 4 users")));
        }

        @Test
        @DisplayName("DELETE /users/{id} with non-existent ID should handle gracefully")
        void testDeleteUserNotFound() throws Exception {
            mockMvc.perform(delete("/users/999999"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("User &#39;User&#39; removed from system.")));
        }
    }
}

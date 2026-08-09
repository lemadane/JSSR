package com.jssr.e2e;

import com.jssr.core.BooleanAttribute;
import com.jssr.core.JssrComponent;
import com.jssr.core.RawHtml;
import com.jssr.core.SafeUrl;
import com.jssr.e2e.app.TestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jssr.e2e.app.MockMvcTestConfig;
import org.springframework.context.annotation.Import;

@SpringBootTest(classes = TestApplication.class)
@Import(MockMvcTestConfig.class)
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
        @DisplayName("1b. E2E HTTP test: Submitting XSS payload to POST /users must render HTML-escaped output via JssrConverter without executing raw tags")
        void testE2eXssPostEscaping() throws Exception {
            mockMvc.perform(post("/users")
                            .param("name", "<script>alert('XSS')</script>")
                            .param("email", "hacker@jssr.dev")
                            .param("role", "Developer")
                            .param("status", "ACTIVE"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("&lt;script&gt;alert(&#39;XSS&#39;)&lt;/script&gt;")))
                    .andExpect(content().string(not(containsString("<script>alert('XSS')</script>"))));
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

    @Nested
    @DisplayName("9. Radio Buttons & Checkboxes E2E Form Control Tests")
    class FormControlsRadioAndCheckboxTests {

        public record AdvancedForm(
            BooleanAttribute subscribeChecked,
            BooleanAttribute termsChecked,
            BooleanAttribute freeChecked,
            BooleanAttribute proChecked,
            BooleanAttribute enterpriseChecked
        ) implements JssrComponent {
            public static AdvancedForm of(boolean subscribe, boolean acceptTerms, String plan) {
                return new AdvancedForm(
                    BooleanAttribute.of("checked", subscribe),
                    BooleanAttribute.of("checked", acceptTerms),
                    BooleanAttribute.of("checked", "FREE".equalsIgnoreCase(plan)),
                    BooleanAttribute.of("checked", "PRO".equalsIgnoreCase(plan)),
                    BooleanAttribute.of("checked", "ENTERPRISE".equalsIgnoreCase(plan))
                );
            }

            @Override
            public String template() {
                return """
                    <form id="advanced-form">
                        <label>
                            <input type="checkbox" name="subscribe" value="true" ${subscribeChecked} /> Subscribe to Newsletter
                        </label>
                        <label>
                            <input type="checkbox" name="acceptTerms" value="true" ${termsChecked} /> Accept Terms
                        </label>

                        <div class="radio-group">
                            <label>
                                <input type="radio" name="plan" value="FREE" ${freeChecked} /> Free Plan
                            </label>
                            <label>
                                <input type="radio" name="plan" value="PRO" ${proChecked} /> Pro Plan
                            </label>
                            <label>
                                <input type="radio" name="plan" value="ENTERPRISE" ${enterpriseChecked} /> Enterprise Plan
                            </label>
                        </div>
                    </form>
                    """;
            }
        }

        @Test
        @DisplayName("Radio buttons and Checkboxes should render correct checked state for form inputs")
        void testFormControlsCheckedState() {
            AdvancedForm form = AdvancedForm.of(true, false, "PRO");
            String html = form.render();

            // Checkbox assertions
            assertTrue(html.contains("name=\"subscribe\" value=\"true\" checked"));
            assertFalse(html.contains("name=\"acceptTerms\" value=\"true\" checked"));

            // Radio button assertions
            assertFalse(html.contains("value=\"FREE\" checked"));
            assertTrue(html.contains("value=\"PRO\" checked"));
            assertFalse(html.contains("value=\"ENTERPRISE\" checked"));
        }

        @Test
        @DisplayName("Control flow directives (@if, @elseif, @else, @end) should render correct branch in E2E components")
        void testE2eControlFlowDirectives() {
            com.jssr.e2e.app.components.UserStatusBadge adminBadge = new com.jssr.e2e.app.components.UserStatusBadge("ADMIN", "ACTIVE", true);
            String htmlAdmin = adminBadge.render();
            assertTrue(htmlAdmin.contains("badge-admin"));
            assertTrue(htmlAdmin.contains("System Admin"));
            assertTrue(htmlAdmin.contains("status-active"));
            assertFalse(htmlAdmin.contains("badge-dev"));
            assertFalse(htmlAdmin.contains("badge-user"));

            com.jssr.e2e.app.components.UserStatusBadge devBadge = new com.jssr.e2e.app.components.UserStatusBadge("DEVELOPER", "INACTIVE", false);
            String htmlDev = devBadge.render();
            assertTrue(htmlDev.contains("badge-dev"));
            assertTrue(htmlDev.contains("Core Developer"));
            assertTrue(htmlDev.contains("status-inactive"));
            assertFalse(htmlDev.contains("badge-admin"));

            com.jssr.e2e.app.components.UserStatusBadge userBadge = new com.jssr.e2e.app.components.UserStatusBadge("GUEST", "ACTIVE", true);
            String htmlUser = userBadge.render();
            assertTrue(htmlUser.contains("badge-user"));
            assertTrue(htmlUser.contains("Standard User (GUEST)"));
            assertTrue(htmlUser.contains("status-active"));
        }

        @Test
        @DisplayName("10. Realistic UserProfileDashboard E2E test covering all control flow directive use cases")
        void testRealisticUserProfileDashboardAllControlFlowUseCases() {
            // Persona 1: Admin User (Active, Verified, Pro, Team Owner, High Storage, Notifications, Bio, Activity)
            com.jssr.e2e.app.model.DashboardUser adminUser = new com.jssr.e2e.app.model.DashboardUser(
                101L, "Sarah Connor", "sarah@cyberdyne.com", "ADMIN", "ACTIVE",
                true, true, true, true, 1250, 5,
                java.util.Optional.of("Lead System Administrator"),
                java.util.List.of("Updated firewall rules", "Provisioned servers"),
                com.jssr.core.BooleanAttribute.present("checked")
            );
            com.jssr.e2e.app.components.UserProfileDashboard adminDashboard = new com.jssr.e2e.app.components.UserProfileDashboard(adminUser);
            String html1 = adminDashboard.render();

            assertTrue(html1.contains("System Administrator"));
            assertTrue(html1.contains("Active Account"));
            assertFalse(html1.contains("email-warning")); // Verified email -> warning hidden
            assertTrue(html1.contains("Critical Storage Warning: Over 1000MB used!"));
            assertTrue(html1.contains("5 Unread Messages"));
            assertTrue(html1.contains("PRO Enterprise Plan Active"));
            assertTrue(html1.contains("Team Owner (Unlimited Member Seats Available)"));
            assertTrue(html1.contains("Lead System Administrator"));
            assertTrue(html1.contains("Recent Activity Log"));

            // Persona 2: Lead Developer (Active, Unverified, Pro, Team Member, Medium Storage, 0 Notifications)
            com.jssr.e2e.app.model.DashboardUser devUser = new com.jssr.e2e.app.model.DashboardUser(
                102L, "Alex Mercer", "alex@dev.local", "DEVELOPER", "ACTIVE",
                true, false, true, false, 650, 0,
                java.util.Optional.empty(), null,
                com.jssr.core.BooleanAttribute.absent("checked")
            );
            com.jssr.e2e.app.components.UserProfileDashboard devDashboard = new com.jssr.e2e.app.components.UserProfileDashboard(devUser);
            String html2 = devDashboard.render();

            assertTrue(html2.contains("Lead Developer"));
            assertTrue(html2.contains("Active Account"));
            assertTrue(html2.contains("email-warning")); // Unverified email -> warning visible
            assertTrue(html2.contains("Moderate Storage Warning: Over 500MB used."));
            assertTrue(html2.contains("All Caught Up"));
            assertTrue(html2.contains("PRO Enterprise Plan Active"));
            assertTrue(html2.contains("Team Member Access"));
            assertFalse(html2.contains("bio-section")); // Empty bio -> section hidden
            assertFalse(html2.contains("activity-section")); // Null activities -> section hidden

            // Persona 3: Free Standard User (Suspended, Unverified, Free Plan, Low Storage)
            com.jssr.e2e.app.model.DashboardUser freeUser = new com.jssr.e2e.app.model.DashboardUser(
                103L, "Bob Smith", "bob@example.com", "MEMBER", "SUSPENDED",
                false, false, false, false, 45, 0,
                java.util.Optional.empty(), java.util.List.of(),
                com.jssr.core.BooleanAttribute.absent("checked")
            );
            com.jssr.e2e.app.components.UserProfileDashboard freeDashboard = new com.jssr.e2e.app.components.UserProfileDashboard(freeUser);
            String html3 = freeDashboard.render();

            assertTrue(html3.contains("Member (MEMBER)"));
            assertTrue(html3.contains("Suspended Account"));
            assertTrue(html3.contains("Optimal Storage Usage."));
            assertTrue(html3.contains("Free Plan"));
            assertTrue(html3.contains("Upgrade to Pro"));
        }

        @Test
        @DisplayName("11. E2E test for @for (item : list) directive with nested @if/@elseif/@else and @else fallback")
        void testE2eForLoopWithNestedIfAndElseFallback() {
            // Case A: User with 3 projects in different statuses
            com.jssr.e2e.app.model.Project p1 = new com.jssr.e2e.app.model.Project(1L, "JSSR Production Engine", "Zero-reflection HTML SSR engine", "COMPLETED", 100);
            com.jssr.e2e.app.model.Project p2 = new com.jssr.e2e.app.model.Project(2L, "Spring Boot Integration", "Full HTTP MVC rendering extension", "IN_PROGRESS", 65);
            com.jssr.e2e.app.model.Project p3 = new com.jssr.e2e.app.model.Project(3L, "WebAssembly Compiler", "Native AOT compilation pipeline", "PLANNING", 0);

            com.jssr.e2e.app.components.UserProjectsCard populatedCard = new com.jssr.e2e.app.components.UserProjectsCard("Elena Rostova", java.util.List.of(p1, p2, p3));
            String htmlPopulated = populatedCard.render();

            assertTrue(htmlPopulated.contains("Assigned Projects for Elena Rostova"));
            assertTrue(htmlPopulated.contains("JSSR Production Engine"));
            assertTrue(htmlPopulated.contains("badge-completed"));
            assertTrue(htmlPopulated.contains("Completed (100%)"));

            assertTrue(htmlPopulated.contains("Spring Boot Integration"));
            assertTrue(htmlPopulated.contains("badge-progress"));
            assertTrue(htmlPopulated.contains("In Progress (65%)"));

            assertTrue(htmlPopulated.contains("WebAssembly Compiler"));
            assertTrue(htmlPopulated.contains("badge-planning"));
            assertTrue(htmlPopulated.contains("Planning Phase"));

            assertFalse(htmlPopulated.contains("no-projects-fallback"));

            // Case B: User with 0 projects -> triggers @else fallback branch
            com.jssr.e2e.app.components.UserProjectsCard emptyCard = new com.jssr.e2e.app.components.UserProjectsCard("Marcus Vance", java.util.List.of());
            String htmlEmpty = emptyCard.render();

            assertTrue(htmlEmpty.contains("Assigned Projects for Marcus Vance"));
            assertTrue(htmlEmpty.contains("no-projects-fallback"));
            assertTrue(htmlEmpty.contains("No projects currently assigned to Marcus Vance."));
            assertFalse(htmlEmpty.contains("project-card"));
        }

        @Test
        @DisplayName("12. Real-world E2E test for nested @for loops with @if/@elseif/@else, @continue, and @break")
        void testE2eWhileLoopWithNestedForIfContinueBreak() {
            // Batch 1 metrics: 2 normal metrics, 1 ignored metric
            com.jssr.e2e.app.model.MetricItem m1 = new com.jssr.e2e.app.model.MetricItem("CPU Load", "OK", "12%", false, false);
            com.jssr.e2e.app.model.MetricItem m2 = new com.jssr.e2e.app.model.MetricItem("Debug Telemetry", "OK", "0", true, false); // ignored (@continue)
            com.jssr.e2e.app.model.MetricItem m3 = new com.jssr.e2e.app.model.MetricItem("Memory Pressure", "WARNING", "84%", false, false);

            // Batch 2 metrics: 1 normal metric, 1 critical failure (@break), 1 unreachable metric
            com.jssr.e2e.app.model.MetricItem m4 = new com.jssr.e2e.app.model.MetricItem("Disk I/O", "OK", "12 MB/s", false, false);
            com.jssr.e2e.app.model.MetricItem m5 = new com.jssr.e2e.app.model.MetricItem("RAID Array Primary Controller", "CRITICAL", "OFFLINE", false, true); // fatal (@break)
            com.jssr.e2e.app.model.MetricItem m6 = new com.jssr.e2e.app.model.MetricItem("Network Throughput", "OK", "1 Gbps", false, false);

            com.jssr.e2e.app.model.BatchCursor cursor = new com.jssr.e2e.app.model.BatchCursor(
                "Production Cluster Health Diagnostic Report",
                java.util.List.of(
                    java.util.List.of(m1, m2, m3),
                    java.util.List.of(m4, m5, m6)
                ),
                new java.util.concurrent.atomic.AtomicInteger(0)
            );

            com.jssr.e2e.app.components.AnalyticsReportCard reportCard = new com.jssr.e2e.app.components.AnalyticsReportCard(cursor);
            String html = reportCard.render();

            assertTrue(html.contains("Production Cluster Health Diagnostic Report"));
            assertTrue(html.contains("CPU Load"));
            assertTrue(html.contains("OK (12%)"));
            assertFalse(html.contains("Debug Telemetry")); // Skipped via @continue
            assertTrue(html.contains("Memory Pressure"));
            assertTrue(html.contains("WARNING (84%)"));

            assertTrue(html.contains("Disk I/O"));
            assertTrue(html.contains("FATAL HARDWARE FAILURE DETECTED ON RAID Array Primary Controller"));
            assertFalse(html.contains("Network Throughput")); // Aborted via @break
        }

        @Test
        @DisplayName("13. Real-world E2E test for @switch (typeof(user)) with polymorphic record types")
        void testE2eSwitchWithTypeOfAndCaseBreakDefault() {
            // Case 1: AdminUser
            com.jssr.e2e.app.model.AdminUser admin = new com.jssr.e2e.app.model.AdminUser("Elena Rostova", "SUPERUSER,READ,WRITE");
            com.jssr.e2e.app.components.UserRoleCard adminCard = new com.jssr.e2e.app.components.UserRoleCard(admin);
            String adminHtml = adminCard.render();
            assertTrue(adminHtml.contains("role-badge-admin"));
            assertTrue(adminHtml.contains("System Administrator: Elena Rostova (SUPERUSER,READ,WRITE)"));

            // Case 2: DeveloperUser
            com.jssr.e2e.app.model.DeveloperUser dev = new com.jssr.e2e.app.model.DeveloperUser("Marcus Vance", "@mvance", "Java");
            com.jssr.e2e.app.components.UserRoleCard devCard = new com.jssr.e2e.app.components.UserRoleCard(dev);
            String devHtml = devCard.render();
            assertTrue(devHtml.contains("role-badge-dev"));
            assertTrue(devHtml.contains("Developer: Marcus Vance (@mvance - Java)"));

            // Case 3: StandardUser
            com.jssr.e2e.app.model.StandardUser user = new com.jssr.e2e.app.model.StandardUser("Sophia Chen", "ENTERPRISE");
            com.jssr.e2e.app.components.UserRoleCard userCard = new com.jssr.e2e.app.components.UserRoleCard(user);
            String userHtml = userCard.render();
            assertTrue(userHtml.contains("role-badge-user"));
            assertTrue(userHtml.contains("User: Sophia Chen (ENTERPRISE)"));

            // Case 4: null -> @default fallback
            com.jssr.e2e.app.components.UserRoleCard guestCard = new com.jssr.e2e.app.components.UserRoleCard(null);
            String guestHtml = guestCard.render();
            assertTrue(guestHtml.contains("role-badge-guest"));
            assertTrue(guestHtml.contains("Anonymous Guest Account"));
        }

        @Test
        @DisplayName("14. Real-world E2E test for @if (user instanceof Type varName) pattern matching and variable binding")
        void testE2eIfInstanceofPatternMatching() {
            // Case 1: AdminUser
            com.jssr.e2e.app.model.AdminUser admin = new com.jssr.e2e.app.model.AdminUser("Elena Rostova", "SUPERUSER,READ,WRITE");
            com.jssr.e2e.app.components.PatternMatchingUserCard adminCard = new com.jssr.e2e.app.components.PatternMatchingUserCard(admin);
            String adminHtml = adminCard.render();
            assertTrue(adminHtml.contains("role-badge-admin"));
            assertTrue(adminHtml.contains("System Administrator: Elena Rostova (SUPERUSER,READ,WRITE)"));

            // Case 2: DeveloperUser
            com.jssr.e2e.app.model.DeveloperUser dev = new com.jssr.e2e.app.model.DeveloperUser("Marcus Vance", "@mvance", "Java");
            com.jssr.e2e.app.components.PatternMatchingUserCard devCard = new com.jssr.e2e.app.components.PatternMatchingUserCard(dev);
            String devHtml = devCard.render();
            assertTrue(devHtml.contains("role-badge-dev"));
            assertTrue(devHtml.contains("Developer: Marcus Vance (@mvance - Java)"));

            // Case 3: StandardUser
            com.jssr.e2e.app.model.StandardUser user = new com.jssr.e2e.app.model.StandardUser("Sophia Chen", "ENTERPRISE");
            com.jssr.e2e.app.components.PatternMatchingUserCard userCard = new com.jssr.e2e.app.components.PatternMatchingUserCard(user);
            String userHtml = userCard.render();
            assertTrue(userHtml.contains("role-badge-user"));
            assertTrue(userHtml.contains("User: Sophia Chen (ENTERPRISE)"));

            // Case 4: null -> @else fallback
            com.jssr.e2e.app.components.PatternMatchingUserCard guestCard = new com.jssr.e2e.app.components.PatternMatchingUserCard(null);
            String guestHtml = guestCard.render();
            assertTrue(guestHtml.contains("role-badge-guest"));
            assertTrue(guestHtml.contains("Anonymous Guest Account"));
        }

        @Test
        @DisplayName("15. Real-world E2E test for @try ... @catch (e) ... @end template error boundary resilience")
        void testE2eTryCatchErrorBoundary() {
            // Case 1: Healthy rendering without fault
            com.jssr.e2e.app.components.FaultTolerantDashboardCard healthyCard = 
                new com.jssr.e2e.app.components.FaultTolerantDashboardCard("OPERATIONAL", false);
            String healthyHtml = healthyCard.render();
            assertTrue(healthyHtml.contains("Primary Service Status: OPERATIONAL"));
            assertTrue(healthyHtml.contains("Secondary Analytics Microservice Connected"));
            assertFalse(healthyHtml.contains("widget-fallback"));

            // Case 2: Fault triggered inside sub-widget -> @try ... @catch captures fault and renders fallback UI
            com.jssr.e2e.app.components.FaultTolerantDashboardCard faultyCard = 
                new com.jssr.e2e.app.components.FaultTolerantDashboardCard("OPERATIONAL", true);
            String faultyHtml = faultyCard.render();
            assertTrue(faultyHtml.contains("Primary Service Status: OPERATIONAL"));
            assertTrue(faultyHtml.contains("widget-fallback"));
            assertTrue(faultyHtml.contains("Microservice widget isolated cleanly"));
            assertTrue(faultyHtml.contains("Unknown JSSR interpolation property"));
            assertTrue(faultyHtml.contains("nonExistentMicroserviceProperty"));
        }
    }

    @Nested
    @DisplayName("4. End-to-End Control Flow & Dashboard HTTP Integration Tests")
    class SystemDashboardHttpE2ETests {

        @Test
        @DisplayName("GET /dashboard?userType=admin should render full layout with Admin permissions, healthy telemetry, and active project list")
        void testGetDashboardAdminUser() throws Exception {
            mockMvc.perform(get("/dashboard?userType=admin"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                    .andExpect(content().string(containsString("<!DOCTYPE html>")))
                    .andExpect(content().string(containsString("<title>Operations Control Center - JSSR Library Demo</title>")))
                    .andExpect(content().string(containsString("Enterprise Operations Control Center")))
                    .andExpect(content().string(containsString("Administrator Access Mode")))
                    .andExpect(content().string(containsString("Master Admin: Elena Rostova")))
                    .andExpect(content().string(containsString("SUPERUSER,READ,WRITE")))
                    .andExpect(content().string(containsString("Telemetry Sensor Cluster: Fully Operational")))
                    .andExpect(content().string(containsString("[Audit Log]: Telemetry lifecycle scan completed at runtime.")))
                    .andExpect(content().string(containsString("JSSR Core Engine")))
                    .andExpect(content().string(containsString("★ Starred")))
                    .andExpect(content().string(containsString("Spring WebMvc Integration")))
                    .andExpect(content().string(not(containsString("Internal Debug Helper")))); // Excluded via @continue (priority < 0)
        }

        @Test
        @DisplayName("GET /dashboard?userType=dev should render Developer profile with primary programming language")
        void testGetDashboardDeveloperUser() throws Exception {
            mockMvc.perform(get("/dashboard?userType=dev"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Engineer Workspace Mode")))
                    .andExpect(content().string(containsString("Lead Dev: Marcus Vance (@mvance - Java)")));
        }

        @Test
        @DisplayName("GET /dashboard?fault=true should trigger @try: ... @catch(e): ... @finally: template error boundary over HTTP")
        void testGetDashboardFaultIsolation() throws Exception {
            mockMvc.perform(get("/dashboard?fault=true"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Sensor Isolation Failure: Captured exception")))
                    .andExpect(content().string(containsString("Manual Telemetry Fault Triggered via @throw")))
                    .andExpect(content().string(containsString("[Audit Log]: Telemetry lifecycle scan completed at runtime."))); // @finally executed
        }

        @Test
        @DisplayName("GET /dashboard?emptyProjects=true should render @for ... @else: empty-list fallback UI")
        void testGetDashboardEmptyProjectsFallback() throws Exception {
            mockMvc.perform(get("/dashboard?emptyProjects=true"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("No active projects registered in this cluster context.")));
        }
    }
}

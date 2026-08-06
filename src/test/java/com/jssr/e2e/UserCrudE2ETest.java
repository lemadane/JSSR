package com.jssr.e2e;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class UserCrudE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("1. Page & Layout Rendering Tests")
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
        @DisplayName("GET /users should render isolated HTMX list container fragment")
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
    @DisplayName("2. UserForm Component Rendering Tests")
    class UserFormComponentTests {

        @Test
        @DisplayName("GET /users/new should render JSSR UserForm component for creation with clean defaults")
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
        @DisplayName("GET /users/1/edit should render JSSR UserForm component pre-filled with user record attributes using variable interpolation")
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
    @DisplayName("3. Live Search & Filtering Tests")
    class SearchAndFilterTests {

        @Test
        @DisplayName("GET /users/search?q=Sarah should return only matching user name")
        void testSearchByName() throws Exception {
            mockMvc.perform(get("/users/search").param("q", "Sarah"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Sarah Connor")))
                    .andExpect(content().string(not(containsString("Alex Mercer"))))
                    .andExpect(content().string(not(containsString("Elena Rostova"))))
                    .andExpect(content().string(containsString("Showing 1 users")));
        }

        @Test
        @DisplayName("GET /users/search?q=Designer should filter by user role")
        void testSearchByRole() throws Exception {
            mockMvc.perform(get("/users/search").param("q", "Designer"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Elena Rostova")))
                    .andExpect(content().string(containsString("Designer")))
                    .andExpect(content().string(not(containsString("Sarah Connor"))))
                    .andExpect(content().string(containsString("Showing 1 users")));
        }

        @Test
        @DisplayName("GET /users/search?q=nonexistent query should render clean empty state message")
        void testSearchEmptyResults() throws Exception {
            mockMvc.perform(get("/users/search").param("q", "NonExistentUser999"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("No users found matching your criteria")))
                    .andExpect(content().string(containsString("Showing 0 users")));
        }
    }

    @Nested
    @DisplayName("4. User Creation Tests")
    class UserCreationTests {

        @Test
        @DisplayName("POST /users with valid inputs should create user, update stats, and render success Toast")
        void testCreateUserSuccess() throws Exception {
            mockMvc.perform(post("/users")
                            .param("name", "Diana Prince")
                            .param("email", "diana.prince@jssr.dev")
                            .param("role", "Admin")
                            .param("status", "ACTIVE"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Diana Prince")))
                    .andExpect(content().string(containsString("diana.prince@jssr.dev")))
                    .andExpect(content().string(containsString("User 'Diana Prince' created successfully!")))
                    .andExpect(content().string(containsString("Showing 6 users")));
        }
    }

    @Nested
    @DisplayName("5. User Update Tests")
    class UserUpdateTests {

        @Test
        @DisplayName("POST /users/{id} should update user attributes and display success Toast")
        void testUpdateUserSuccess() throws Exception {
            mockMvc.perform(post("/users/2")
                            .param("name", "Alex Mercer Revised")
                            .param("email", "alex.revised@jssr.dev")
                            .param("role", "Product Lead")
                            .param("status", "ACTIVE"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Alex Mercer Revised")))
                    .andExpect(content().string(containsString("alex.revised@jssr.dev")))
                    .andExpect(content().string(containsString("Product Lead")))
                    .andExpect(content().string(containsString("User 'Alex Mercer Revised' updated successfully!")));
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
    @DisplayName("6. User Status Toggle Tests")
    class UserStatusToggleTests {

        @Test
        @DisplayName("POST /users/{id}/toggle should switch user from INACTIVE to ACTIVE")
        void testToggleUserStatusInactiveToActive() throws Exception {
            mockMvc.perform(post("/users/3/toggle"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Status for 'Elena Rostova' changed to ACTIVE")));
        }

        @Test
        @DisplayName("POST /users/{id}/toggle should switch user from ACTIVE to INACTIVE")
        void testToggleUserStatusActiveToInactive() throws Exception {
            mockMvc.perform(post("/users/1/toggle"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Status for 'Sarah Connor' changed to INACTIVE")));
        }
    }

    @Nested
    @DisplayName("7. User Deletion Tests")
    class UserDeletionTests {

        @Test
        @DisplayName("DELETE /users/{id} should remove user from system and decrement user count")
        void testDeleteUserSuccess() throws Exception {
            mockMvc.perform(delete("/users/2"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("alex.mercer@jssr.dev"))))
                    .andExpect(content().string(containsString("User 'Alex Mercer' removed from system.")))
                    .andExpect(content().string(containsString("Showing 4 users")));
        }

        @Test
        @DisplayName("DELETE /users/{id} with non-existent ID should handle gracefully")
        void testDeleteUserNotFound() throws Exception {
            mockMvc.perform(delete("/users/999999"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("User 'User' removed from system.")));
        }
    }
}

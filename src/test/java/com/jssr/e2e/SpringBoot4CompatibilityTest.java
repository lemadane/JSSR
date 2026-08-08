package com.jssr.e2e;

import com.jssr.core.JssrComponent;
import com.jssr.core.SafeUrl;
import com.jssr.e2e.app.MockMvcTestConfig;
import com.jssr.e2e.app.TestApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Spring Boot 3.x / 4.x cross-version compatibility test suite.
 * Verifies that JSSR component rendering, Spring Web MVC controller bindings, and HttpMessageConverter
 * integration function seamlessly across Spring Boot 3.x and 4.x runtime environments.
 */
@SpringBootTest(classes = TestApplication.class)
@Import(MockMvcTestConfig.class)
public class SpringBoot4CompatibilityTest {

    @Autowired
    private MockMvc mockMvc;

    public record SpringBoot4Card(String title, SafeUrl homeUrl) implements JssrComponent {
        @Override
        public String template() {
            return """
                <div class="springboot4-card bg-slate-900 text-white p-6 rounded-xl border border-slate-800">
                    <h2 class="text-xl font-bold text-emerald-400">${title}</h2>
                    <a href="${homeUrl}" class="inline-block mt-4 px-4 py-2 bg-emerald-600 text-white rounded">
                        Return to Hub
                    </a>
                </div>
                """;
        }
    }

    @Test
    @DisplayName("Spring MVC controller rendering JSSR component record returns clean HTML with HTTP 200 OK")
    void testSpringMvcJssrComponentControllerRendering() throws Exception {
        mockMvc.perform(get("/dashboard?userType=admin")
                .accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enterprise Operations Control Center")))
                .andExpect(content().string(containsString("Telemetry Sensor Cluster: Fully Operational")));
    }

    @Test
    @DisplayName("Direct JSSR component record instantiation produces Spring Boot 4 compliant HTML output")
    void testDirectComponentRenderingForSpringBoot4() {
        SpringBoot4Card card = new SpringBoot4Card("Spring Boot 4 Integration Hub", SafeUrl.of("/home"));
        String html = card.render();

        org.junit.jupiter.api.Assertions.assertTrue(html.contains("Spring Boot 4 Integration Hub"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("href=\"/home\""));
    }
}

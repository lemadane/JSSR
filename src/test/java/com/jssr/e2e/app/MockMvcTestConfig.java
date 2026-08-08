package com.jssr.e2e.app;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;

@TestConfiguration
public class MockMvcTestConfig {

    @Bean
    public MockMvc mockMvc(WebApplicationContext wac) {
        if (wac.getServletContext() == null && wac instanceof GenericWebApplicationContext gwc) {
            gwc.setServletContext(new MockServletContext());
        }
        return MockMvcBuilders.webAppContextSetup(wac).build();
    }
}

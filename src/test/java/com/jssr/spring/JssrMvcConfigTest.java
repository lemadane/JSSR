package com.jssr.spring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JssrMvcConfigTest {

    @Test
    @DisplayName("JssrMvcConfig.extendMessageConverters should prepend JssrConverter while preserving existing default converters")
    void testExtendMessageConvertersPreservesDefaults() {
        JssrMvcConfig config = new JssrMvcConfig();

        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        converters.add(new ByteArrayHttpMessageConverter());
        converters.add(new StringHttpMessageConverter());
        converters.add(new MappingJackson2HttpMessageConverter());

        config.extendMessageConverters(converters);

        assertEquals(4, converters.size());
        assertInstanceOf(JssrConverter.class, converters.get(0));
        assertInstanceOf(ByteArrayHttpMessageConverter.class, converters.get(1));
        assertInstanceOf(StringHttpMessageConverter.class, converters.get(2));
        assertInstanceOf(MappingJackson2HttpMessageConverter.class, converters.get(3));
    }
}

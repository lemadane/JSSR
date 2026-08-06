package com.jssr.spring;

import com.jssr.core.JssrComponent;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring Web HttpMessageConverter converting JSSR JssrComponent objects directly into HTML responses.
 */
public class JssrConverter extends AbstractHttpMessageConverter<JssrComponent> {

    public JssrConverter() {
        super(new MediaType("text", "html", StandardCharsets.UTF_8));
    }

    @Override
    protected boolean supports(@NonNull Class<?> clazz) {
        return JssrComponent.class.isAssignableFrom(clazz);
    }

    @Override
    @NonNull
    protected JssrComponent readInternal(@NonNull Class<? extends JssrComponent> clazz, @NonNull HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
        throw new UnsupportedOperationException("Reading HTTP body into JssrComponent is not supported.");
    }

    @Override
    protected void writeInternal(@NonNull JssrComponent component, @NonNull HttpOutputMessage outputMessage)
            throws IOException, HttpMessageNotWritableException {
        String html = component.render();
        outputMessage.getBody().write(html.getBytes(StandardCharsets.UTF_8));
    }
}

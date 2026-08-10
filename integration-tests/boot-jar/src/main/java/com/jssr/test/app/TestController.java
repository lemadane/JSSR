package com.jssr.test.app;

import com.jssr.core.compiler.CompilationStatus;
import com.jssr.core.compiler.JssrPrecompiler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class TestController {

    @GetMapping("/test-render")
    public Map<String, Object> testRender() {
        UserCard card = new UserCard("Alice", true);
        String html = card.renderPrecompiled();
        CompilationStatus status = JssrPrecompiler.status(UserCard.class);

        return Map.of(
            "html", html,
            "status", status.name()
        );
    }
}

package com.jssr.test.app;

import com.jssr.core.JssrComponent;

public record UserCard(String name, boolean active) implements JssrComponent {
    @Override
    public String template() {
        return "<h1>${name}</h1>@if(active)<span>Active</span>@end";
    }
}

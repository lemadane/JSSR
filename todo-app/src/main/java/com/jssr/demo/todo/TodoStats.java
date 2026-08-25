package com.jssr.demo.todo;

import com.jssr.core.JssrComponent;

public record TodoStats(long completedCount, long totalCount) implements JssrComponent {

    @Override
    public String render() {
        return """
            <p class="stats">Completed: ${completedCount} / ${totalCount}</p>
            """;
    }
}

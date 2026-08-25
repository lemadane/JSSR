package com.jssr.demo.todo;

import com.jssr.core.JssrComponent;

public record TodoActionBar(Todo todo) implements JssrComponent {

    static {
        JssrComponent.register("TodoItemActions", TodoItemActions.class);
    }

    @Override
    public String render() {
        return """
            <div class="todo-actions">
                <TodoItemActions todo="${todo}" />
            </div>
            """;
    }
}

package com.jssr.demo.todo;

import com.jssr.core.JssrComponent;

public record TodoRow(Todo todo) implements JssrComponent {

    static {
        JssrComponent.register("TodoActionBar", TodoActionBar.class);
    }

    @Override
    public String render() {
        return """
            @if (todo.completed) {
                <li class="todo-row done">
                    <span class="todo-title">${todo.title}</span>
                    <TodoActionBar todo="${todo}" />
                </li>
            } @else {
                <li class="todo-row open">
                    <span class="todo-title">${todo.title}</span>
                    <TodoActionBar todo="${todo}" />
                </li>
            }
            """;
    }
}

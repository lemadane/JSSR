package com.jssr.demo.todo;

import com.jssr.core.JssrComponent;
import com.jssr.core.SafeUrl;

public record TodoItemActions(Todo todo) implements JssrComponent {

    public SafeUrl toggleAction() {
        return SafeUrl.of("/todos/" + todo.id() + "/toggle");
    }

    public SafeUrl editAction() {
        return SafeUrl.of("/todos/" + todo.id() + "/edit");
    }

    public SafeUrl deleteAction() {
        return SafeUrl.of("/todos/" + todo.id() + "/delete");
    }

    @Override
    public String render() {
        return """
            <form method="post" action="${toggleAction}">
                @if (todo.completed) {
                    <button type="submit" class="todo-btn secondary">Mark Open</button>
                } @else {
                    <button type="submit" class="todo-btn secondary">Mark Done</button>
                }
            </form>

            <form method="post" action="${editAction}" class="todo-edit-form">
                <input type="text" name="title" value="${todo.title}" maxlength="120" required>
                <button type="submit" class="todo-btn">Save</button>
            </form>

            <form method="post" action="${deleteAction}">
                <button type="submit" class="todo-btn danger">Delete</button>
            </form>
            """;
    }
}

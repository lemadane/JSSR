package com.jssr.demo.todo;

import com.jssr.core.JssrComponent;

public record TodoForm(String query) implements JssrComponent {

    @Override
    public String render() {
        return """
            <form method="post" action="/todos" class="form-grid">
                <input type="text" name="title" placeholder="Add a task" required maxlength="120">
                <button type="submit">Add</button>
            </form>

            <form method="get" action="/todos" class="filter">
                <input type="text" name="q" value="${query}" placeholder="Filter tasks">
                <button type="submit">Filter</button>
            </form>
            """;
    }
}

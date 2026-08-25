package com.jssr.demo.todo;

import com.jssr.core.JssrComponent;
import java.util.List;

public record TodoList(List<Todo> todos) implements JssrComponent {

    static {
        JssrComponent.register("TodoRow", TodoRow.class);
    }

    @Override
    public String render() {
        List<Todo> safeTodoList = todos == null ? List.of() : todos;
        return JssrComponent.render(this, java.util.Map.of("rows", safeTodoList), """
            <ul class="list">
                @for (row : rows) {
                    <TodoRow todo="${row}" />
                } @else {
                    <li class="empty">No tasks yet. Add one above.</li>
                }
            </ul>
            """);
    }
}

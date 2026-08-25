package com.jssr.demo.todo;

import com.jssr.core.JssrComponent;
import java.util.List;

public record TodoList(List<Todo> todos) implements JssrComponent {

    static {
        JssrComponent.register("TodoRow", TodoRow.class);
    }

    @Override
    public String render() {
        return """
                <ul class="list">
                    @for (todo : todos) {
                        <TodoRow todo="${todo}" />
                    } @else {
                        <li class="empty">No tasks yet. Add one above.</li>
                    }
                </ul>
        """;
    }
}

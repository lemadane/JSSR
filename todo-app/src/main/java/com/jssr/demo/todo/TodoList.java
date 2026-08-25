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
                   <!--if(todos = null && todos.size() > 0) { // happens in the background that is why we have @else-->
                    @for (todo : todos) {
                        <TodoRow todo="${todo}" />
                    } @else {
                        <li class="empty">No tasks yet. Add one above.</li>
                    }
                </ul>
        """;
    }
}

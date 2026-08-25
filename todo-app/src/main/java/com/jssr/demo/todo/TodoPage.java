package com.jssr.demo.todo;

import com.jssr.core.JssrComponent;
import java.util.List;

public record TodoPage(List<Todo> todos, long totalCount, long completedCount, String query) implements JssrComponent {

    static {
        JssrComponent.register("TodoList", TodoList.class);
        JssrComponent.register("TodoForm", TodoForm.class);
    }

    @Override
    public String render() {
        return """
            <!doctype html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>JSSR Todo Demo</title>
                <style>
                    :root {
                        --bg: #f6f8fb;
                        --card: #ffffff;
                        --ink: #102542;
                        --muted: #506176;
                        --accent: #1f6feb;
                        --ok: #1f883d;
                        --line: #d0d7de;
                    }
                    body {
                        margin: 0;
                        background: radial-gradient(circle at 10% -10%, #dbeafe 0%, var(--bg) 45%);
                        color: var(--ink);
                        font-family: "IBM Plex Sans", "Segoe UI", sans-serif;
                    }
                    main {
                        max-width: 760px;
                        margin: 48px auto;
                        padding: 0 16px;
                    }
                    .card {
                        background: var(--card);
                        border: 1px solid var(--line);
                        border-radius: 14px;
                        box-shadow: 0 8px 36px rgba(16, 37, 66, 0.08);
                        padding: 24px;
                    }
                    h1 {
                        margin: 0 0 6px;
                        font-size: 2rem;
                    }
                    .subtitle {
                        margin: 0 0 18px;
                        color: var(--muted);
                    }
                    .stats {
                        margin: 0 0 18px;
                        color: var(--muted);
                        font-size: 0.95rem;
                    }
                    .form-grid {
                        display: grid;
                        grid-template-columns: 1fr auto;
                        gap: 8px;
                        margin-bottom: 14px;
                    }
                    input[type="text"] {
                        border: 1px solid var(--line);
                        border-radius: 10px;
                        padding: 10px 12px;
                        font: inherit;
                    }
                    button {
                        border: 0;
                        border-radius: 10px;
                        padding: 10px 14px;
                        background: var(--accent);
                        color: #fff;
                        font-weight: 600;
                        cursor: pointer;
                    }
                    button:hover {
                        filter: brightness(0.95);
                    }
                    .filter {
                        margin-bottom: 14px;
                    }
                    .list {
                        list-style: none;
                        margin: 0;
                        padding: 0;
                        display: grid;
                        gap: 10px;
                    }
                    .todo-row {
                        display: flex;
                        justify-content: space-between;
                        align-items: flex-start;
                        gap: 12px;
                        border: 1px solid var(--line);
                        border-radius: 10px;
                        padding: 10px 12px;
                    }
                    .todo-row.done {
                        border-color: rgba(31, 136, 61, 0.35);
                    }
                    .todo-row.done .todo-title {
                        text-decoration: line-through;
                        color: var(--ok);
                    }
                    .todo-actions {
                        display: flex;
                        flex-wrap: wrap;
                        gap: 6px;
                        justify-content: flex-end;
                        align-items: center;
                    }
                    .todo-actions form {
                        margin: 0;
                    }
                    .todo-btn {
                        border: 0;
                        border-radius: 8px;
                        padding: 8px 10px;
                        background: var(--accent);
                        color: #fff;
                        font-weight: 600;
                        cursor: pointer;
                        font-size: 0.85rem;
                    }
                    .todo-btn.secondary {
                        background: #2f81f7;
                    }
                    .todo-btn.danger {
                        background: #cf222e;
                    }
                    .todo-edit-form {
                        display: flex;
                        gap: 6px;
                        align-items: center;
                    }
                    .todo-edit-form input[type="text"] {
                        width: 180px;
                        border: 1px solid var(--line);
                        border-radius: 8px;
                        padding: 8px 10px;
                    }
                    .empty {
                        border: 1px dashed var(--line);
                        border-radius: 10px;
                        padding: 14px;
                        text-align: center;
                        color: var(--muted);
                    }
                </style>
            </head>
            <body>
                <main>
                    <section class="card">
                        <h1>Todo Demo</h1>
                        <p class="subtitle">Spring MVC + JSSR record components.</p>
                        <p class="stats">Completed: ${completedCount} / ${totalCount}</p>

                        <TodoForm query="${query}" />

                        <TodoList todos="${todos}" />
                    </section>
                </main>
            </body>
            </html>
            """;
    }
}

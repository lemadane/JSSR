package com.jssr.demo.todo;

public record Todo(
        long id,
        String title,
        boolean completed) {
    
    Todo(long id, String title) {
        this(id, title, false);
    }
    Todo toggle() {
        return new Todo(id, title, !completed);
    }

    Todo withTitle(String nextTitle) {
        return new Todo(id, nextTitle, completed);
    }
}
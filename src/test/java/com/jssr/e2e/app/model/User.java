package com.jssr.e2e.app.model;

public record User(
    Long id,
    String name,
    String email,
    String role,
    String status,
    String createdAt
) {
    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}

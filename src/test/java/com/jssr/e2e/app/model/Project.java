package com.jssr.e2e.app.model;

public record Project(
    Long id,
    String name,
    String description,
    String status,
    int completionPercentage
) {}

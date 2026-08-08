package com.jssr.e2e.app.model;

public record DeveloperUser(
    String name,
    String githubHandle,
    String primaryLanguage
) {}

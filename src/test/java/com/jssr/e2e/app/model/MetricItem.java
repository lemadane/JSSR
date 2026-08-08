package com.jssr.e2e.app.model;

public record MetricItem(
    String name,
    String status,
    String value,
    boolean ignore,
    boolean criticalFailure
) {}

package com.jssr.e2e.app.model;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public record BatchCursor(
    String reportTitle,
    List<List<MetricItem>> batches,
    AtomicInteger cursorIndex
) {
    public boolean hasUnprocessedBatches() {
        return cursorIndex.get() < batches.size();
    }

    public int getCurrentBatchNumber() {
        return cursorIndex.get();
    }

    public List<MetricItem> getCurrentMetrics() {
        int idx = cursorIndex.getAndIncrement();
        if (idx < batches.size()) {
            return batches.get(idx);
        }
        return List.of();
    }
}

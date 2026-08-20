package com.jssr.e2e.app.components;

import com.jssr.core.JssrComponent;
import com.jssr.e2e.app.model.BatchCursor;

public record AnalyticsReportCard(
    BatchCursor cursor
) implements JssrComponent {

    @Override
    public String render() {
        return """
            <div id="analytics-report-container" class="max-w-4xl mx-auto p-6 bg-slate-900 text-slate-100 rounded-xl shadow-2xl border border-slate-800">
                <h1 class="text-2xl font-bold mb-6 text-slate-100">${cursor.reportTitle}</h1>

                <div class="batches-container space-y-6">
                <!-- Outer @for loop iterating batches -->
                @for (batch : cursor.batches) {
                    <div class="batch-card p-5 bg-slate-800/60 rounded-xl border border-slate-700">
                        <h3 class="text-base font-bold text-indigo-400 mb-3">Batch</h3>

                        <div class="metrics-list space-y-2">
                        <!-- Nested @for loop inside @for loop -->
                        @for (metric : batch) {
                            <!-- Loop control: @continue skipping ignored metrics -->
                            @if (metric.ignore) {
                                @continue
                            }

                            <!-- Loop control: @break aborting batch on critical failure -->
                            @if (metric.criticalFailure) {
                                <div class="alert-fatal p-3 bg-rose-950/80 border border-rose-500 text-rose-300 rounded text-xs font-bold font-mono">
                                    🚨 FATAL HARDWARE FAILURE DETECTED ON ${metric.name} - ABORTING BATCH PROCESSING
                                </div>
                                @break
                            }

                            <div class="metric-row p-3 bg-slate-900/60 rounded flex justify-between items-center text-xs font-mono border border-slate-800">
                                <span class="text-slate-300 font-semibold">${metric.name}</span>

                                <!-- Nested @if / @elseif / @else inside @for -->
                                @if (metric.status == 'CRITICAL') {
                                    <span class="badge-critical bg-rose-500/10 text-rose-400 border border-rose-500/20 px-3 py-1 rounded-full font-bold">
                                        CRITICAL (${metric.value})
                                    </span>
                                } @elseif (metric.status == 'WARNING') {
                                    <span class="badge-warning bg-amber-500/10 text-amber-400 border border-amber-500/20 px-3 py-1 rounded-full font-bold">
                                        WARNING (${metric.value})
                                    </span>
                                } @else {
                                    <span class="badge-ok bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 px-3 py-1 rounded-full font-bold">
                                        OK (${metric.value})
                                    </span>
                                }
                            </div>
                        } @else {
                            <p class="empty-metrics text-xs text-slate-500 italic">No metrics recorded in this batch.</p>
                        }
                        </div>
                    </div>
                }
                </div>
            </div>
            """;
    }
}

package com.jssr.e2e.app.components;

import com.jssr.core.JssrComponent;

public record FaultTolerantDashboardCard(
    String validServiceStatus,
    boolean triggerFault
) implements JssrComponent {

    @Override
    public String template() {
        return """
            <div class="fault-tolerant-card p-6 bg-slate-900 text-slate-100 rounded-xl border border-slate-800 shadow-xl">
                <h2 class="text-lg font-bold text-slate-200 mb-4">System Resiliency Telemetry</h2>

                <!-- Safe Primary Service Section -->
                <div class="primary-status text-emerald-400 font-semibold mb-4">
                    Primary Service Status: ${validServiceStatus}
                </div>

                <!-- Unsafe Secondary Microservice Widget wrapped in @try: ... @catch(e): ... @finally: Error Boundary -->
                @try:
                    @if (triggerFault)
                        <!-- Intentional property access fault to test template error boundary -->
                        <div class="widget-data">${nonExistentMicroserviceProperty}</div>
                    @else
                        <div class="widget-data text-blue-400 font-medium">
                            ⚡ Secondary Analytics Microservice Connected (Latency: 14ms)
                        </div>
                    @end
                @catch(e):
                    <div class="widget-fallback p-4 bg-amber-500/10 border border-amber-500/20 text-amber-400 rounded-lg text-xs font-mono">
                        ⚠️ Telemetry Warning: Microservice widget isolated cleanly (${e.message})
                    </div>
                @finally:
                    <div class="telemetry-session text-slate-500 text-xs mt-2">
                        Telemetry Audit Checked
                    </div>
                @end
            </div>
            """;
    }
}

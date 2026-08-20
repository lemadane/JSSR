package com.jssr.e2e.app.components;

import com.jssr.core.JssrComponent;

import java.util.List;

public record SystemOverviewDashboardCard(
    Object account,
    List<ProjectInfo> projects,
    boolean triggerTelemetryFault
) implements JssrComponent {

    public record ProjectInfo(String name, String status, boolean starred, int priority) {}

    @Override
    public String render() {
        return """
            <div class="system-overview-card p-8 bg-slate-950 text-slate-100 rounded-2xl border border-slate-800 shadow-2xl space-y-6">
                <!-- Header with Account Polymorphic Type Inspection (@switch typeof) -->
                <div class="header flex items-center justify-between pb-6 border-b border-slate-800">
                    <h1 class="text-xl font-bold text-slate-100">Enterprise Operations Control Center</h1>
                    @switch (typeof(account)) {
                        @case (AdminUser) {
                            <span class="role-pill bg-purple-500/10 text-purple-400 border border-purple-500/20 px-3 py-1 rounded-full text-xs font-semibold">
                                Administrator Access Mode
                            </span>
                        }
                        @case (DeveloperUser) {
                            <span class="role-pill bg-blue-500/10 text-blue-400 border border-blue-500/20 px-3 py-1 rounded-full text-xs font-semibold">
                                Engineer Workspace Mode
                            </span>
                        }
                        @default {
                            <span class="role-pill bg-slate-800 text-slate-400 px-3 py-1 rounded-full text-xs font-semibold">
                                Read-Only Guest Mode
                            </span>
                        }
                    }
                </div>

                <!-- Account Pattern Matching Scope Binding (@if instanceof) -->
                <div class="account-profile bg-slate-900/60 p-4 rounded-xl border border-slate-800">
                    @if (account instanceof com.jssr.e2e.app.model.AdminUser admin) {
                        <div class="admin-profile text-purple-300">
                            Master Admin: ${admin.name} (Permissions: ${admin.permissions})
                        </div>
                    } @elseif (account instanceof com.jssr.e2e.app.model.DeveloperUser dev) {
                        <div class="dev-profile text-blue-300">
                            Lead Dev: ${dev.name} (${dev.githubHandle} - ${dev.primaryLanguage})
                        </div>
                    } @else {
                        <div class="guest-profile text-slate-400">
                            Guest Session: Limited Privileges
                        </div>
                    }
                </div>

                <!-- Template Error Boundary (@try { ... } @catch(e) { ... } @finally { ... }) -->
                <div class="telemetry-widget-section">
                    @try {
                        @if (triggerTelemetryFault) {
                            @throw("Manual Telemetry Fault Triggered via @throw")
                        } @else {
                            <div class="healthy-telemetry p-4 bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 rounded-xl text-sm font-medium">
                                🟢 Telemetry Sensor Cluster: Fully Operational (100% Signal Integrity)
                            </div>
                        }
                    } @catch(e) {
                        <div class="telemetry-fallback p-4 bg-rose-500/10 border border-rose-500/20 text-rose-400 rounded-xl text-sm font-mono">
                            🚨 Sensor Isolation Failure: Captured exception (${e.message})
                        </div>
                    } @finally {
                        <div class="telemetry-audit text-xs text-slate-500 mt-2 font-mono">
                            [Audit Log]: Telemetry lifecycle scan completed at runtime.
                        </div>
                    }
                </div>

                <!-- Project List Iteration (@for ... @else ... with @continue & @break) -->
                <div class="projects-section">
                    <h3 class="text-md font-semibold text-slate-300 mb-3">Active Cluster Projects</h3>
                    <div class="project-list space-y-2">
                        @for (p : projects) {
                            @if (p.priority < 0) {
                                @continue
                            }
                            @if (p.priority > 99) {
                                <div class="project-halt text-rose-400 font-bold text-xs">
                                    🚨 CRITICAL ALARM: Project Priority Overflow (${p.name}) - Halting Processing
                                </div>
                                @break
                            }

                            <div class="project-item flex items-center justify-between p-3 bg-slate-900 rounded-lg border border-slate-800">
                                <div class="flex items-center space-x-3">
                                    <span class="project-name font-mono text-sm text-slate-200">${p.name}</span>
                                    @if (p.starred) {
                                        <span class="star-badge text-amber-400 text-xs font-bold">★ Starred</span>
                                    }
                                </div>
                                <span class="project-status text-xs px-2 py-1 bg-slate-800 rounded text-slate-300">${p.status}</span>
                            </div>
                        } @else {
                            <div class="empty-projects p-4 text-center text-slate-500 text-sm italic">
                                No active projects registered in this cluster context.
                            </div>
                        }
                    </div>
                </div>
            </div>
            """;
    }
}

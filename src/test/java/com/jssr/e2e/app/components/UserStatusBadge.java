package com.jssr.e2e.app.components;

import com.jssr.core.JssrComponent;

public record UserStatusBadge(
    String role,
    String status,
    boolean isActive
) implements JssrComponent {

    @Override
    public String render() {
        return """
            <div class="user-badges flex items-center space-x-2">
                <!-- Role Badge with @if / @elseif / @else control flow -->
                @if (role == 'ADMIN') {
                    <span class="badge badge-admin bg-amber-500/10 text-amber-400 border border-amber-500/20 px-2 py-1 rounded text-xs font-semibold">
                        System Admin
                    </span>
                } @elseif (role == 'DEVELOPER') {
                    <span class="badge badge-dev bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 px-2 py-1 rounded text-xs font-semibold">
                        Core Developer
                    </span>
                } @else {
                    <span class="badge badge-user bg-sky-500/10 text-sky-400 border border-sky-500/20 px-2 py-1 rounded text-xs font-semibold">
                        Standard User (${role})
                    </span>
                }

                <!-- Status Badge with @if / @else control flow -->
                @if (isActive) {
                    <span class="status-indicator status-active text-emerald-400 text-xs flex items-center">
                        <span class="w-2 h-2 rounded-full bg-emerald-400 inline-block mr-1"></span> Active
                    </span>
                } @else {
                    <span class="status-indicator status-inactive text-slate-400 text-xs flex items-center">
                        <span class="w-2 h-2 rounded-full bg-slate-500 inline-block mr-1"></span> Inactive (${status})
                    </span>
                }
            </div>
            """;
    }
}

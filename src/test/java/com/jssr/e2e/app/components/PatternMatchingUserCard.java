package com.jssr.e2e.app.components;

import com.jssr.core.JssrComponent;

public record PatternMatchingUserCard(
    Object user
) implements JssrComponent {

    @Override
    public String template() {
        return """
            <div class="user-pattern-card p-6 bg-slate-900 text-slate-100 rounded-xl border border-slate-800 shadow-xl">
                <!-- @if with Java 17+ pattern matching instanceof and scoped pattern variable binding -->
                @if (user instanceof com.jssr.e2e.app.model.AdminUser admin)
                    <div class="role-badge-admin flex items-center gap-2 text-purple-400 font-bold">
                        <span class="px-3 py-1 bg-purple-500/10 border border-purple-500/20 rounded-full text-xs">
                            👑 System Administrator: ${admin.name} (${admin.permissions})
                        </span>
                    </div>
                @elseif (user instanceof com.jssr.e2e.app.model.DeveloperUser dev)
                    <div class="role-badge-dev flex items-center gap-2 text-blue-400 font-bold">
                        <span class="px-3 py-1 bg-blue-500/10 border border-blue-500/20 rounded-full text-xs">
                            💻 Developer: ${dev.name} (${dev.githubHandle} - ${dev.primaryLanguage})
                        </span>
                    </div>
                @elseif (user instanceof com.jssr.e2e.app.model.StandardUser sUser)
                    <div class="role-badge-user flex items-center gap-2 text-emerald-400 font-bold">
                        <span class="px-3 py-1 bg-emerald-500/10 border border-emerald-500/20 rounded-full text-xs">
                            👤 User: ${sUser.name} (${sUser.planType})
                        </span>
                    </div>
                @else
                    <div class="role-badge-guest text-slate-500 italic text-xs">
                        🔒 Anonymous Guest Account
                    </div>
                @end
            </div>
            """;
    }
}

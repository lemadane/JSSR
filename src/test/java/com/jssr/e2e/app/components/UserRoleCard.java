package com.jssr.e2e.app.components;

import com.jssr.core.JssrComponent;

public record UserRoleCard(
    Object user
) implements JssrComponent {

    @Override
    public String render() {
        return """
            <div class="user-role-card p-6 bg-slate-900 text-slate-100 rounded-xl border border-slate-800 shadow-xl">
                <!-- @switch with typeof(user) type reflection -->
                @switch (typeof(user)) {
                    @case ('AdminUser') {
                        <div class="role-badge-admin flex items-center gap-2 text-purple-400 font-bold">
                            <span class="px-3 py-1 bg-purple-500/10 border border-purple-500/20 rounded-full text-xs">
                                👑 System Administrator: ${user.name} (${user.permissions})
                            </span>
                        </div>
                        @break
                    }
                    @case ('DeveloperUser') {
                        <div class="role-badge-dev flex items-center gap-2 text-blue-400 font-bold">
                            <span class="px-3 py-1 bg-blue-500/10 border border-blue-500/20 rounded-full text-xs">
                                💻 Developer: ${user.name} (${user.githubHandle} - ${user.primaryLanguage})
                            </span>
                        </div>
                        @break
                    }
                    @case ('StandardUser') {
                        <div class="role-badge-user flex items-center gap-2 text-emerald-400 font-bold">
                            <span class="px-3 py-1 bg-emerald-500/10 border border-emerald-500/20 rounded-full text-xs">
                                👤 User: ${user.name} (${user.planType})
                            </span>
                        </div>
                        @break
                    }
                    @default {
                        <div class="role-badge-guest text-slate-500 italic text-xs">
                            🔒 Anonymous Guest Account
                        </div>
                    }
                }
            </div>
            """;
    }
}

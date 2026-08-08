package com.jssr.e2e.app.components;

import com.jssr.core.JssrComponent;
import com.jssr.e2e.app.model.DashboardUser;

public record UserProfileDashboard(DashboardUser user) implements JssrComponent {

    @Override
    public String template() {
        return """
            <div id="user-dashboard" class="dashboard-container max-w-4xl mx-auto p-6 bg-slate-900 text-slate-100 rounded-2xl shadow-xl">
                <!-- 1. Header with Role & Status Directives -->
                <div class="flex justify-between items-center pb-6 border-b border-slate-800">
                    <div>
                        <h1 class="text-2xl font-bold">${user.name}</h1>
                        <p class="text-sm text-slate-400 font-mono">${user.email}</p>
                    </div>

                    <div class="flex items-center space-x-3">
                        <!-- Role Badge (@if / @elseif / @else / @end) -->
                        @if (user.role == 'ADMIN')
                            <span id="role-badge" class="badge-admin bg-amber-500/10 text-amber-400 border border-amber-500/20 px-3 py-1 rounded-full text-xs font-bold">
                                System Administrator
                            </span>
                        @elseif (user.role == 'DEVELOPER')
                            <span id="role-badge" class="badge-dev bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 px-3 py-1 rounded-full text-xs font-bold">
                                Lead Developer
                            </span>
                        @elseif (user.role == 'MANAGER')
                            <span id="role-badge" class="badge-manager bg-purple-500/10 text-purple-400 border border-purple-500/20 px-3 py-1 rounded-full text-xs font-bold">
                                Product Manager
                            </span>
                        @else
                            <span id="role-badge" class="badge-user bg-sky-500/10 text-sky-400 border border-sky-500/20 px-3 py-1 rounded-full text-xs font-bold">
                                Member (${user.role})
                            </span>
                        @end

                        <!-- Status Indicator (@if / @else / @end) -->
                        @if (user.active)
                            <span id="status-badge" class="bg-emerald-500/10 text-emerald-400 px-3 py-1 rounded-full text-xs font-medium flex items-center">
                                <span class="w-2 h-2 rounded-full bg-emerald-400 mr-2 animate-pulse"></span> Active Account
                            </span>
                        @else
                            <span id="status-badge" class="bg-slate-700 text-slate-400 px-3 py-1 rounded-full text-xs font-medium flex items-center">
                                <span class="w-2 h-2 rounded-full bg-slate-500 mr-2"></span> Suspended Account
                            </span>
                        @end
                    </div>
                </div>

                <!-- 2. Security Alerts (@if (!user.emailVerified)) -->
                @if (!user.emailVerified)
                    <div id="email-warning" class="mt-4 p-4 bg-rose-500/10 border border-rose-500/20 rounded-xl text-rose-300 text-sm flex justify-between items-center">
                        <span>⚠️ Your email address is unverified. Please check your inbox.</span>
                        <button class="bg-rose-500/20 hover:bg-rose-500/30 px-3 py-1 rounded text-xs">Resend Verification</button>
                    </div>
                @end

                <!-- 3. Metrics & Relational Operators (>=, >, ==) -->
                <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mt-6">
                    <!-- Storage Metric -->
                    <div class="p-4 bg-slate-800/50 rounded-xl border border-slate-800">
                        <div class="text-xs text-slate-400 uppercase font-semibold">Storage Capacity</div>
                        <div class="text-lg font-bold mt-1">${user.storageUsedMb} MB / 2000 MB</div>

                        @if (user.storageUsedMb >= 1000)
                            <div id="storage-alert" class="text-xs text-rose-400 mt-2 font-medium">Critical Storage Warning: Over 1000MB used!</div>
                        @elseif (user.storageUsedMb > 500)
                            <div id="storage-alert" class="text-xs text-amber-400 mt-2 font-medium">Moderate Storage Warning: Over 500MB used.</div>
                        @else
                            <div id="storage-alert" class="text-xs text-emerald-400 mt-2 font-medium">Optimal Storage Usage.</div>
                        @end
                    </div>

                    <!-- Unread Notifications Metric -->
                    <div class="p-4 bg-slate-800/50 rounded-xl border border-slate-800">
                        <div class="text-xs text-slate-400 uppercase font-semibold">Notifications</div>
                        @if (user.unreadNotifications > 0)
                            <div id="unread-count" class="text-lg font-bold text-indigo-400 mt-1">${user.unreadNotifications} Unread Messages</div>
                        @else
                            <div id="unread-count" class="text-lg font-bold text-slate-500 mt-1">All Caught Up</div>
                        @end
                    </div>
                </div>

                <!-- 4. Nested Subscriptions & Permissions (@if (user.hasProSubscription) @if (user.isTeamOwner)) -->
                <div class="mt-6 p-6 bg-slate-800/30 rounded-xl border border-slate-800">
                    <h3 class="text-base font-semibold mb-3">Subscription Tier</h3>
                    @if (user.hasProSubscription)
                        <div id="subscription-card" class="p-4 bg-indigo-950/40 border border-indigo-500/30 rounded-xl">
                            <div class="text-indigo-300 font-bold">PRO Enterprise Plan Active</div>
                            @if (user.isTeamOwner)
                                <div id="team-role" class="text-xs text-indigo-400 mt-1 font-mono">Team Owner (Unlimited Member Seats Available)</div>
                            @else
                                <div id="team-role" class="text-xs text-slate-400 mt-1 font-mono">Team Member Access</div>
                            @end
                        </div>
                    @else
                        <div id="subscription-card" class="p-4 bg-slate-800 border border-slate-700 rounded-xl flex justify-between items-center">
                            <div>
                                <div class="text-slate-300 font-bold">Free Plan</div>
                                <div id="team-role" class="text-xs text-slate-400 mt-1">Limited features and single seat access.</div>
                            </div>
                            <button class="bg-indigo-600 hover:bg-indigo-500 text-white text-xs px-4 py-2 rounded-lg font-semibold shadow">Upgrade to Pro</button>
                        </div>
                    @end
                </div>

                <!-- 5. Optional Bio & Collection Truthiness (@if (user.bio), @if (user.recentActivities)) -->
                @if (user.bio)
                    <div id="bio-section" class="mt-6 p-4 bg-slate-800/30 rounded-xl">
                        <div class="text-xs text-slate-400 uppercase font-semibold mb-1">About User</div>
                        <p class="text-sm text-slate-300">${user.bio}</p>
                    </div>
                @end

                @if (user.recentActivities)
                    <div id="activity-section" class="mt-6">
                        <div class="text-xs text-slate-400 uppercase font-semibold mb-2">Recent Activity Log</div>
                        <ul class="space-y-2 text-xs font-mono text-slate-300">
                            <li class="p-2 bg-slate-800/40 rounded border border-slate-800">System Activity Registered</li>
                        </ul>
                    </div>
                @end
            </div>
            """;
    }
}

package com.jssr.e2e.app.components;

import com.jssr.core.JssrComponent;

public record UserStats(long total, long active, long admins) implements JssrComponent {

    @Override
    public String template() {
        return """
            <div class="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
                <!-- Total Users Card -->
                <div class="bg-slate-800/80 backdrop-blur-md rounded-2xl p-6 border border-slate-700/60 shadow-xl flex items-center justify-between hover:border-slate-600 transition duration-300">
                    <div>
                        <p class="text-xs font-semibold uppercase tracking-wider text-slate-400">Total Users</p>
                        <h3 class="text-3xl font-extrabold text-white mt-1">%d</h3>
                        <p class="text-xs text-indigo-400 mt-1 font-medium">Registered in system</p>
                    </div>
                    <div class="w-12 h-12 rounded-xl bg-indigo-500/10 border border-indigo-500/20 flex items-center justify-center text-indigo-400">
                        <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z"/>
                        </svg>
                    </div>
                </div>

                <!-- Active Accounts Card -->
                <div class="bg-slate-800/80 backdrop-blur-md rounded-2xl p-6 border border-slate-700/60 shadow-xl flex items-center justify-between hover:border-slate-600 transition duration-300">
                    <div>
                        <p class="text-xs font-semibold uppercase tracking-wider text-slate-400">Active Accounts</p>
                        <h3 class="text-3xl font-extrabold text-emerald-400 mt-1">%d</h3>
                        <p class="text-xs text-emerald-400/80 mt-1 font-medium">Currently operational</p>
                    </div>
                    <div class="w-12 h-12 rounded-xl bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
                        <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/>
                        </svg>
                    </div>
                </div>

                <!-- Administrators Card -->
                <div class="bg-slate-800/80 backdrop-blur-md rounded-2xl p-6 border border-slate-700/60 shadow-xl flex items-center justify-between hover:border-slate-600 transition duration-300">
                    <div>
                        <p class="text-xs font-semibold uppercase tracking-wider text-slate-400">System Admins</p>
                        <h3 class="text-3xl font-extrabold text-amber-400 mt-1">%d</h3>
                        <p class="text-xs text-amber-400/80 mt-1 font-medium">Elevated permissions</p>
                    </div>
                    <div class="w-12 h-12 rounded-xl bg-amber-500/10 border border-amber-500/20 flex items-center justify-center text-amber-400">
                        <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"/>
                        </svg>
                    </div>
                </div>
            </div>
            """.formatted(total, active, admins);
    }
}

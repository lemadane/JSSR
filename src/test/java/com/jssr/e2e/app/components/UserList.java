package com.jssr.e2e.app.components;

import com.jssr.core.JssrComponent;
import com.jssr.e2e.app.model.User;

import java.util.List;

public record UserList(
    List<User> users,
    long totalCount,
    long activeCount,
    long adminCount,
    String searchQuery,
    String toastMessage,
    String toastType
) implements JssrComponent {

    static {
        JssrComponent.register("UserRow", UserRow.class);
    }

    public UserList(List<User> users, long totalCount, long activeCount, long adminCount, String searchQuery) {
        this(users, totalCount, activeCount, adminCount, searchQuery, null, null);
    }

    @Override
    public String render() {
        List<User> userList = users != null ? users : List.of();

        StringBuilder rowsHtml = new StringBuilder();
        if (userList.isEmpty()) {
            rowsHtml.append("""
                <tr>
                    <td colspan="5" class="py-12 text-center text-slate-400">
                        <svg class="w-12 h-12 mx-auto mb-3 text-slate-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M20 13V6a2 2 0 00-2-2H6a2 2 0 00-2 2v7m16 0v5a2 2 0 01-2 2H6a2 2 0 01-2-2v-5m16 0h-2.586a1 1 0 00-.707.293l-2.414 2.414a1 1 0 01-.707.293h-3.172a1 1 0 01-.707-.293l-2.414-2.414A1 1 0 006.586 13H4"/>
                        </svg>
                        <p class="text-base font-medium">No users found matching your criteria</p>
                        <p class="text-xs text-slate-500 mt-1">Try refining your search query or add a new user.</p>
                    </td>
                </tr>
                """);
        } else {
            for (User user : userList) {
                rowsHtml.append("""
                    <UserRow id="%d" name="%s" email="%s" role="%s" status="%s" createdAt="%s" />
                    """.formatted(
                        user.id(),
                        escapeAttr(user.name()),
                        escapeAttr(user.email()),
                        escapeAttr(user.role()),
                        escapeAttr(user.status()),
                        escapeAttr(user.createdAt())
                    ));
            }
        }

        UserStats stats = new UserStats(totalCount, activeCount, adminCount);
        String statsHtml = JssrComponent.render(stats);

        String toastHtml = "";
        if (toastMessage != null && !toastMessage.isBlank()) {
            Toast toast = new Toast(toastMessage, toastType != null ? toastType : "success");
            toastHtml = JssrComponent.render(toast);
        }

        String searchVal = searchQuery != null ? searchQuery : "";

        return """
            <div id="user-list-container" class="w-full">
                %s

                <!-- Top Stats Grid -->
                %s

                <!-- Control Bar: Search & Action -->
                <div class="bg-slate-800/80 backdrop-blur-md rounded-2xl border border-slate-700/60 p-6 shadow-xl mb-6 flex flex-col md:flex-row items-center justify-between gap-4">
                    <!-- Search Input -->
                    <div class="relative w-full md:w-96">
                        <div class="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
                            </svg>
                        </div>
                        <input type="text"
                               name="q"
                               value="%s"
                               placeholder="Search by name, email, or role..."
                               hx-get="/users/search"
                               hx-target="#user-list-container"
                               hx-swap="outerHTML"
                               hx-trigger="keyup changed delay:300ms, search"
                               class="w-full pl-10 pr-4 py-2.5 bg-slate-900/90 border border-slate-700 rounded-xl text-sm text-slate-100 placeholder-slate-500 focus:outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 transition duration-150">
                    </div>

                    <!-- Actions -->
                    <div class="flex items-center space-x-3 w-full md:w-auto justify-end">
                        <button hx-get="/users"
                                hx-target="#user-list-container"
                                hx-swap="outerHTML"
                                class="px-4 py-2.5 rounded-xl border border-slate-700 bg-slate-800 text-slate-300 hover:text-white hover:border-slate-600 text-sm font-semibold transition duration-150 flex items-center">
                            <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"/>
                            </svg>
                            Refresh
                        </button>
                        <button @click="$dispatch('open-create-modal')"
                                class="px-5 py-2.5 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 hover:from-indigo-500 hover:to-purple-500 text-white text-sm font-semibold shadow-lg shadow-indigo-600/30 transition duration-150 flex items-center">
                            <svg class="w-4 h-4 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"/>
                            </svg>
                            New User
                        </button>
                    </div>
                </div>

                <!-- Users Data Table -->
                <div class="bg-slate-800/80 backdrop-blur-md rounded-2xl border border-slate-700/60 shadow-xl overflow-hidden">
                    <div class="overflow-x-auto">
                        <table class="w-full text-left border-collapse">
                            <thead>
                                <tr class="bg-slate-900/60 text-slate-400 text-xs font-semibold uppercase tracking-wider border-b border-slate-700/60">
                                    <th class="py-4 px-6">User</th>
                                    <th class="py-4 px-6">Email</th>
                                    <th class="py-4 px-6">Role</th>
                                    <th class="py-4 px-6">Status</th>
                                    <th class="py-4 px-6 text-right">Actions</th>
                                </tr>
                            </thead>
                            <tbody class="divide-y divide-slate-700/40 text-slate-200">
                                %s
                            </tbody>
                        </table>
                    </div>

                    <!-- Footer Info -->
                    <div class="px-6 py-4 bg-slate-900/40 border-t border-slate-700/40 flex items-center justify-between text-xs text-slate-400 font-medium">
                        <span>Showing %d users</span>
                        <span class="flex items-center text-indigo-400">
                            <span class="w-2 h-2 rounded-full bg-emerald-400 animate-pulse mr-2"></span>
                            HTMX & JSSR Server-Side Rendered
                        </span>
                    </div>
                </div>
            </div>
            """.formatted(
                toastHtml,
                statsHtml,
                escapeAttr(searchVal),
                rowsHtml.toString(),
                userList.size()
            );
    }

    private String escapeAttr(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

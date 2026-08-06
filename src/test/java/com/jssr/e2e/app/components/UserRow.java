package com.jssr.e2e.app.components;

import com.jssr.core.JssrComponent;
import com.jssr.e2e.app.model.User;

public record UserRow(
    Long id,
    String name,
    String email,
    String role,
    String status,
    String createdAt
) implements JssrComponent {

    public UserRow(User user) {
        this(user.id(), user.name(), user.email(), user.role(), user.status(), user.createdAt());
    }

    @Override
    public String template() {
        if (id == null) {
            return "";
        }

        String initials = getInitials(name);
        boolean isActive = "ACTIVE".equalsIgnoreCase(status);
        String statusBadgeClass = isActive 
            ? "bg-emerald-500/10 text-emerald-400 border-emerald-500/20" 
            : "bg-slate-700 text-slate-400 border-slate-600";

        String roleBadgeClass = switch (role != null ? role.toLowerCase() : "") {
            case "admin" -> "bg-amber-500/10 text-amber-400 border-amber-500/20";
            case "developer" -> "bg-indigo-500/10 text-indigo-400 border-indigo-500/20";
            case "designer" -> "bg-rose-500/10 text-rose-400 border-rose-500/20";
            default -> "bg-sky-500/10 text-sky-400 border-sky-500/20";
        };

        return """
            <tr id="user-row-%d" class="hover:bg-slate-700/30 transition duration-150 group border-b border-slate-700/40">
                <!-- User Info & Avatar -->
                <td class="py-4 px-6">
                    <div class="flex items-center space-x-4">
                        <div class="w-10 h-10 rounded-full bg-gradient-to-br from-indigo-500 to-purple-600 text-white font-bold flex items-center justify-center text-sm shadow-md flex-shrink-0">
                            %s
                        </div>
                        <div>
                            <div class="font-semibold text-slate-100 group-hover:text-indigo-300 transition duration-150">%s</div>
                            <div class="text-xs text-slate-400">ID: #%d • Joined %s</div>
                        </div>
                    </div>
                </td>

                <!-- Email -->
                <td class="py-4 px-6 text-sm text-slate-300 font-mono">
                    %s
                </td>

                <!-- Role Badge -->
                <td class="py-4 px-6">
                    <span class="inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold border %s">
                        %s
                    </span>
                </td>

                <!-- Status Toggle Badge -->
                <td class="py-4 px-6">
                    <button hx-post="/users/%d/toggle"
                            hx-target="#user-list-container"
                            hx-swap="outerHTML"
                            title="Click to toggle status"
                            class="inline-flex items-center px-3 py-1 rounded-full text-xs font-semibold border transition duration-200 hover:scale-105 cursor-pointer %s">
                        <span class="w-1.5 h-1.5 rounded-full mr-1.5 %s"></span>
                        %s
                    </button>
                </td>

                <!-- Actions -->
                <td class="py-4 px-6 text-right space-x-2">
                    <!-- Edit Button -->
                    <button @click="$dispatch('open-edit-modal', { id: %d, name: '%s', email: '%s', role: '%s', status: '%s' })"
                            class="inline-flex items-center p-2 rounded-lg text-slate-400 hover:text-indigo-400 hover:bg-slate-700/60 transition duration-150"
                            title="Edit User">
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"/>
                        </svg>
                    </button>

                    <!-- Delete Button with HTMX confirmation -->
                    <button hx-delete="/users/%d"
                            hx-target="#user-list-container"
                            hx-swap="outerHTML"
                            hx-confirm="Are you sure you want to delete user '%s'?"
                            class="inline-flex items-center p-2 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-slate-700/60 transition duration-150"
                            title="Delete User">
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"/>
                        </svg>
                    </button>
                </td>
            </tr>
            """.formatted(
                id,
                initials,
                escapeHtml(name),
                id,
                escapeHtml(createdAt),
                escapeHtml(email),
                roleBadgeClass,
                escapeHtml(role),
                id,
                statusBadgeClass,
                isActive ? "bg-emerald-400" : "bg-slate-500",
                status,
                id,
                escapeJs(name),
                escapeJs(email),
                escapeJs(role),
                escapeJs(status),
                id,
                escapeJs(name)
            );
    }

    private String getInitials(String name) {
        if (name == null || name.isBlank()) return "U";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }

    private String escapeHtml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String escapeJs(String str) {
        if (str == null) return "";
        return str.replace("'", "\\'").replace("\"", "\\\"");
    }
}

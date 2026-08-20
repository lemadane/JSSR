package com.jssr.e2e.app.components;

import com.jssr.core.JssrComponent;
import com.jssr.e2e.app.model.Project;
import java.util.List;

public record UserProjectsCard(
    String userName,
    List<Project> projects
) implements JssrComponent {

    @Override
    public String render() {
        return """
            <div id="user-projects-container" class="max-w-3xl mx-auto p-6 bg-slate-900 text-slate-100 rounded-xl shadow-lg border border-slate-800">
                <h2 class="text-xl font-bold mb-4">Assigned Projects for ${userName}</h2>

                <div class="projects-list space-y-4">
                @for (project : projects) {
                    <div class="project-card p-4 bg-slate-800/60 rounded-lg border border-slate-700">
                        <div class="flex justify-between items-center">
                            <h3 class="font-bold text-slate-200">${project.name}</h3>

                            <!-- Nested @if / @elseif / @else inside @for loop -->
                            @if (project.status == 'COMPLETED') {
                                <span class="badge-status badge-completed bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 px-2 py-1 rounded text-xs font-semibold">
                                    Completed (${project.completionPercentage}%)
                                </span>
                            } @elseif (project.status == 'IN_PROGRESS') {
                                <span class="badge-status badge-progress bg-amber-500/10 text-amber-400 border border-amber-500/20 px-2 py-1 rounded text-xs font-semibold">
                                    In Progress (${project.completionPercentage}%)
                                </span>
                            } @else {
                                <span class="badge-status badge-planning bg-slate-700 text-slate-400 px-2 py-1 rounded text-xs font-semibold">
                                    Planning Phase
                                </span>
                            }
                        </div>
                        <p class="text-xs text-slate-400 mt-2">${project.description}</p>
                    </div>
                } @else {
                    <!-- Empty List @else Fallback Branch -->
                    <div id="no-projects-fallback" class="p-6 bg-slate-800/30 rounded-lg border border-slate-800 text-center text-slate-400 text-sm">
                        📁 No projects currently assigned to ${userName}.
                    </div>
                }
                </div>
            </div>
            """;
    }
}

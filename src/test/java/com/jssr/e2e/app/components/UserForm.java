package com.jssr.e2e.app.components;

import com.jssr.core.JssrComponent;

public record UserForm() implements JssrComponent {

    @Override
    public String template() {
        return """
            <div x-data="{
                    open: false,
                    isEdit: false,
                    userId: null,
                    name: '',
                    email: '',
                    role: 'Developer',
                    status: 'ACTIVE',
                    
                    init() {
                        window.addEventListener('open-create-modal', () => {
                            this.isEdit = false;
                            this.userId = null;
                            this.name = '';
                            this.email = '';
                            this.role = 'Developer';
                            this.status = 'ACTIVE';
                            this.open = true;
                        });
                        
                        window.addEventListener('open-edit-modal', (e) => {
                            this.isEdit = true;
                            this.userId = e.detail.id;
                            this.name = e.detail.name;
                            this.email = e.detail.email;
                            this.role = e.detail.role;
                            this.status = e.detail.status;
                            this.open = true;
                        });
                    }
                 }"
                 x-show="open" 
                 x-cloak
                 class="fixed inset-0 z-50 overflow-y-auto"
                 aria-labelledby="modal-title" 
                 role="dialog" 
                 aria-modal="true">
                 
                <!-- Backdrop -->
                <div x-show="open"
                     x-transition:enter="transition ease-out duration-300"
                     x-transition:enter-start="opacity-0"
                     x-transition:enter-end="opacity-100"
                     x-transition:leave="transition ease-in duration-200"
                     x-transition:leave-start="opacity-100"
                     x-transition:leave-end="opacity-0"
                     @click="open = false"
                     class="fixed inset-0 bg-slate-950/80 backdrop-blur-sm transition-opacity"></div>

                <div class="flex min-h-full items-center justify-center p-4 text-center">
                    <!-- Modal Panel -->
                    <div x-show="open"
                         x-transition:enter="transition ease-out duration-300 transform"
                         x-transition:enter-start="opacity-0 translate-y-4 sm:translate-y-0 sm:scale-95"
                         x-transition:enter-end="opacity-100 translate-y-0 sm:scale-100"
                         x-transition:leave="transition ease-in duration-200 transform"
                         x-transition:leave-start="opacity-100 translate-y-0 sm:scale-100"
                         x-transition:leave-end="opacity-0 translate-y-4 sm:translate-y-0 sm:scale-95"
                         class="relative transform overflow-hidden rounded-2xl bg-slate-800 border border-slate-700/80 text-left shadow-2xl transition-all sm:my-8 sm:w-full sm:max-w-lg">
                        
                        <div class="px-6 py-5 border-b border-slate-700/60 flex items-center justify-between">
                            <h3 class="text-xl font-bold text-slate-100" id="modal-title" x-text="isEdit ? 'Edit User' : 'Create New User'"></h3>
                            <button @click="open = false" class="text-slate-400 hover:text-slate-200 focus:outline-none">
                                <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
                                </svg>
                            </button>
                        </div>

                        <!-- Dynamic HTMX Form -->
                        <form :hx-post="isEdit ? '/users/' + userId : '/users'"
                              hx-target="#user-list-container"
                              hx-swap="outerHTML"
                              @submit="open = false"
                              class="p-6 space-y-4">
                              
                            <div>
                                <label class="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1.5">Full Name</label>
                                <input type="text" 
                                       name="name" 
                                       x-model="name" 
                                       required
                                       placeholder="e.g. Jane Doe"
                                       class="w-full px-4 py-2.5 rounded-xl bg-slate-900 border border-slate-700 text-slate-100 placeholder-slate-500 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 focus:outline-none transition duration-150">
                            </div>

                            <div>
                                <label class="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1.5">Email Address</label>
                                <input type="email" 
                                       name="email" 
                                       x-model="email" 
                                       required
                                       placeholder="e.g. jane.doe@example.com"
                                       class="w-full px-4 py-2.5 rounded-xl bg-slate-900 border border-slate-700 text-slate-100 placeholder-slate-500 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 focus:outline-none transition duration-150">
                            </div>

                            <div class="grid grid-cols-2 gap-4">
                                <div>
                                    <label class="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1.5">Role</label>
                                    <select name="role" 
                                            x-model="role" 
                                            class="w-full px-4 py-2.5 rounded-xl bg-slate-900 border border-slate-700 text-slate-100 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 focus:outline-none transition duration-150">
                                        <option value="Admin">Admin</option>
                                        <option value="Developer">Developer</option>
                                        <option value="Designer">Designer</option>
                                        <option value="Product Lead">Product Lead</option>
                                        <option value="User">User</option>
                                    </select>
                                </div>

                                <div>
                                    <label class="block text-xs font-semibold uppercase tracking-wider text-slate-300 mb-1.5">Status</label>
                                    <select name="status" 
                                            x-model="status" 
                                            class="w-full px-4 py-2.5 rounded-xl bg-slate-900 border border-slate-700 text-slate-100 focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 focus:outline-none transition duration-150">
                                        <option value="ACTIVE">ACTIVE</option>
                                        <option value="INACTIVE">INACTIVE</option>
                                    </select>
                                </div>
                            </div>

                            <div class="pt-4 flex items-center justify-end space-x-3 border-t border-slate-700/60 mt-6">
                                <button type="button" 
                                        @click="open = false" 
                                        class="px-5 py-2.5 rounded-xl text-slate-300 hover:text-white hover:bg-slate-700 transition duration-150 font-medium text-sm">
                                    Cancel
                                </button>
                                <button type="submit" 
                                        class="px-6 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-semibold shadow-lg shadow-indigo-600/30 transition duration-150 text-sm">
                                    <span x-text="isEdit ? 'Save Changes' : 'Create User'"></span>
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            </div>
            """;
    }
}

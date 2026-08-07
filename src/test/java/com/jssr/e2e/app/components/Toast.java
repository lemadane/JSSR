package com.jssr.e2e.app.components;

import com.jssr.core.JssrComponent;

public record Toast(String message, String type) implements JssrComponent {

    public Toast(String message) {
        this(message, "success");
    }

    @Override
    public String template() {
        if (message == null || message.isBlank()) {
            return "";
        }

        String bgClass = "bg-emerald-600 border-emerald-400";
        String icon = """
            <svg class="w-5 h-5 text-white mr-2 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"/>
            </svg>""";

        if ("error".equalsIgnoreCase(type)) {
            bgClass = "bg-rose-600 border-rose-400";
            icon = """
                <svg class="w-5 h-5 text-white mr-2 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
                </svg>""";
        } else if ("info".equalsIgnoreCase(type)) {
            bgClass = "bg-sky-600 border-sky-400";
            icon = """
                <svg class="w-5 h-5 text-white mr-2 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/>
                </svg>""";
        }

        return """
            <div x-data="{ show: true }"
                 x-show="show" 
                 x-init="setTimeout(() => show = false, 4000)"
                 x-transition:enter="transition ease-out duration-300 transform"
                 x-transition:enter-start="opacity-0 translate-y-2 scale-95"
                 x-transition:enter-end="opacity-100 translate-y-0 scale-100"
                 x-transition:leave="transition ease-in duration-200 transform"
                 x-transition:leave-start="opacity-100 translate-y-0 scale-100"
                 x-transition:leave-end="opacity-0 translate-y-2 scale-95"
                 class="fixed bottom-6 right-6 z-50 flex items-center px-4 py-3 text-white text-sm font-medium rounded-xl shadow-2xl border %s">
                %s
                <span>${message}</span>
                <button @click="show = false" class="ml-4 text-white/80 hover:text-white focus:outline-none">
                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"/>
                    </svg>
                </button>
            </div>
            """.formatted(bgClass, icon);
    }
}

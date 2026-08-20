package com.jssr.e2e.app.components;

import com.jssr.core.JssrComponent;
import com.jssr.core.RawHtml;

public record PageLayout(String title, RawHtml contentHtml, RawHtml formModalHtml) implements JssrComponent {

    public PageLayout(String title, String contentHtml) {
        this(title, RawHtml.of(contentHtml), RawHtml.of(JssrComponent.render(new UserForm())));
    }

    public PageLayout(String title, RawHtml contentHtml) {
        this(title, contentHtml, RawHtml.of(JssrComponent.render(new UserForm())));
    }

    @Override
    public String render() {
        return """
            <!DOCTYPE html>
            <html lang="en" class="h-full bg-slate-950">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>${title} - JSSR Library Demo</title>
                
                <!-- Tailwind CSS -->
                <script src="https://cdn.tailwindcss.com"></script>
                <script>
                    tailwind.config = {
                        theme: {
                            extend: {
                                fontFamily: {
                                    sans: ['Inter', 'system-ui', 'sans-serif'],
                                },
                            }
                        }
                    }
                </script>
                
                <!-- Google Fonts -->
                <link rel="preconnect" href="https://fonts.googleapis.com">
                <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">

                <!-- HTMX & Alpine.js -->
                <script src="https://unpkg.com/htmx.org@1.9.10"></script>
                <script defer src="https://unpkg.com/alpinejs@3.x.x/dist/cdn.min.js"></script>
                
                <style>
                    [x-cloak] { display: none !important; }
                    body { font-family: 'Inter', sans-serif; }
                </style>
            </head>
            <body class="h-full bg-slate-950 text-slate-100 flex flex-col antialiased selection:bg-indigo-500 selection:text-white">
                
                <!-- Background Ambient Glow -->
                <div class="fixed top-0 left-1/2 -translate-x-1/2 w-full max-w-7xl h-96 bg-gradient-to-b from-indigo-900/20 via-purple-900/10 to-transparent blur-3xl pointer-events-none -z-10"></div>

                <!-- Navigation Header -->
                <header class="border-b border-slate-800/80 bg-slate-900/50 backdrop-blur-xl sticky top-0 z-40">
                    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
                        <!-- Logo & Branding -->
                        <div class="flex items-center space-x-3">
                            <div class="w-10 h-10 rounded-xl bg-gradient-to-br from-indigo-500 via-purple-500 to-pink-500 flex items-center justify-center font-extrabold text-white text-lg shadow-lg shadow-indigo-500/25">
                                J
                            </div>
                            <div>
                                <span class="text-lg font-bold text-white tracking-tight">JSSR <span class="text-indigo-400 font-normal">UI Library</span></span>
                                <span class="ml-2.5 px-2.5 py-0.5 text-[10px] font-semibold tracking-wide uppercase rounded-full bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">Java 17+ Record SSR</span>
                            </div>
                        </div>

                        <!-- Header Links / Status -->
                        <div class="flex items-center space-x-6 text-sm">
                            <div class="hidden sm:flex items-center space-x-2 text-slate-400">
                                <span class="w-2 h-2 rounded-full bg-emerald-400"></span>
                                <span class="font-medium text-xs">Record Components</span>
                            </div>
                        </div>
                    </div>
                </header>

                <!-- Main Content Area -->
                <main class="flex-grow max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-10">
                    ${contentHtml}
                </main>

                <!-- Global User Form Modal -->
                ${formModalHtml}

                <!-- Global Footer -->
                <footer class="border-t border-slate-800/60 bg-slate-900/30 py-6 text-center text-xs text-slate-500">
                    <p>Powered by <strong class="text-slate-400">JSSR Framework</strong> • Record Components + Java 17 Text Blocks + HTMX + Alpine.js</p>
                </footer>
            </body>
            </html>
            """;
    }
}

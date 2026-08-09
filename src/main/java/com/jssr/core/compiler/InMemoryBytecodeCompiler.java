package com.jssr.core.compiler;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.*;

/**
 * Dynamic in-memory bytecode compiler using JDK's javax.tools.JavaCompiler.
 * Modeled after PTE's (Piped Template Engine) in-memory bytecode compilation architecture.
 */
public final class InMemoryBytecodeCompiler {

    /**
     * Check if javax.tools.JavaCompiler is available in the current JVM environment.
     *
     * @return true if JavaCompiler is accessible, false otherwise
     */
    public static boolean isAvailable() {
        return ToolProvider.getSystemJavaCompiler() != null;
    }

    /**
     * Compile generated Java source code into a JVM Class in memory.
     *
     * @param className Simple class name of the generated class
     * @param javaSource Full Java source code string
     * @return Loaded Class object implementing CompiledTemplateExecutable
     * @throws Exception if compilation fails or JavaCompiler is unavailable
     */
    public Class<?> compile(String className, String javaSource) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JavaCompiler is not available in current JRE/JDK environment.");
        }

        String fullClassName = "com.jssr.core.compiler.generated." + className;
        JavaFileObject fileObject = new StringJavaFileObject(fullClassName, javaSource);

        Map<String, ByteArrayOutputStream> byteCodeMap = new HashMap<>();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager sfm = compiler.getStandardFileManager(diagnostics, null, null);

        List<String> cpElements = new ArrayList<>();
        addClassRoot(com.jssr.core.JssrComponent.class, cpElements);
        addClassRoot(getClass(), cpElements);

        String sysCp = System.getProperty("java.class.path");
        if (sysCp != null && !sysCp.isEmpty()) {
            cpElements.add(sysCp);
        }

        try {
            ClassLoader cl = getClass().getClassLoader();
            while (cl != null) {
                if (cl instanceof java.net.URLClassLoader ucl) {
                    for (java.net.URL url : ucl.getURLs()) {
                        try {
                            cpElements.add(new java.io.File(url.toURI()).getAbsolutePath());
                        } catch (Exception ignored) {}
                    }
                }
                cl = cl.getParent();
            }

            ClassLoader tcl = Thread.currentThread().getContextClassLoader();
            while (tcl != null) {
                if (tcl instanceof java.net.URLClassLoader ucl) {
                    for (java.net.URL url : ucl.getURLs()) {
                        try {
                            cpElements.add(new java.io.File(url.toURI()).getAbsolutePath());
                        } catch (Exception ignored) {}
                    }
                }
                tcl = tcl.getParent();
            }
        } catch (Exception ignored) {}

        try {
            java.io.File buildClasses = new java.io.File("build/classes/java/main");
            if (buildClasses.exists()) {
                cpElements.add(buildClasses.getAbsolutePath());
            }
        } catch (Exception ignored) {}

        List<java.io.File> cpFiles = new ArrayList<>();
        Set<String> added = new HashSet<>();
        for (String path : cpElements) {
            if (path != null && !path.isBlank()) {
                for (String singlePath : path.split(java.io.File.pathSeparator)) {
                    if (!singlePath.isBlank() && added.add(singlePath)) {
                        java.io.File f = new java.io.File(singlePath);
                        if (f.exists()) {
                            cpFiles.add(f);
                        }
                    }
                }
            }
        }

        String fullCp = cpFiles.stream()
                .map(java.io.File::getAbsolutePath)
                .reduce((a, b) -> a + java.io.File.pathSeparator + b)
                .orElse("");
        List<String> options = List.of("-classpath", fullCp);

        try {
            sfm.setLocation(StandardLocation.CLASS_PATH, cpFiles);
        } catch (Exception ignored) {}

        MemoryJavaFileManager fileManager = new MemoryJavaFileManager(sfm, byteCodeMap);
        JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics, options, null, Collections.singletonList(fileObject));

        boolean success = task.call();
        if (!success) {
            StringBuilder errorMsg = new StringBuilder("Compilation failed for " + fullClassName + ":\n");
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                errorMsg.append(diagnostic.toString()).append("\n");
            }
            throw new IllegalStateException(errorMsg.toString());
        }

        MemoryClassLoader classLoader = new MemoryClassLoader(byteCodeMap, getClass().getClassLoader());
        return classLoader.loadClass(fullClassName);
    }

    static class StringJavaFileObject extends SimpleJavaFileObject {
        final String code;

        StringJavaFileObject(String className, String code) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    static class MemoryJavaFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
        final Map<String, ByteArrayOutputStream> byteCodeMap;

        MemoryJavaFileManager(StandardJavaFileManager fileManager, Map<String, ByteArrayOutputStream> byteCodeMap) {
            super(fileManager);
            this.byteCodeMap = byteCodeMap;
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byteCodeMap.put(className, baos);
            return new SimpleJavaFileObject(URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind) {
                @Override
                public OutputStream openOutputStream() {
                    return baos;
                }
            };
        }
    }

    static class MemoryClassLoader extends ClassLoader {
        final Map<String, ByteArrayOutputStream> byteCodeMap;

        MemoryClassLoader(Map<String, ByteArrayOutputStream> byteCodeMap, ClassLoader parent) {
            super(parent);
            this.byteCodeMap = byteCodeMap;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            ByteArrayOutputStream baos = byteCodeMap.get(name);
            if (baos == null) {
                return super.findClass(name);
            }
            byte[] bytes = baos.toByteArray();
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    private static void addClassRoot(Class<?> clazz, List<String> cpElements) {
        try {
            var location = clazz.getProtectionDomain().getCodeSource() != null 
                    ? clazz.getProtectionDomain().getCodeSource().getLocation() : null;
            if (location != null) {
                cpElements.add(new java.io.File(location.toURI()).getAbsolutePath());
            }
        } catch (Exception ignored) {}

        try {
            String classResourceName = clazz.getSimpleName() + ".class";
            java.net.URL url = clazz.getResource(classResourceName);
            if (url != null) {
                String urlStr = url.toExternalForm();
                String pkgPath = clazz.getPackageName().replace('.', '/') + "/" + classResourceName;
                if (urlStr.endsWith(pkgPath)) {
                    String rootUrlStr = urlStr.substring(0, urlStr.length() - pkgPath.length());
                    if (rootUrlStr.startsWith("file:")) {
                        java.io.File file = new java.io.File(new java.net.URI(rootUrlStr));
                        cpElements.add(file.getAbsolutePath());
                    } else if (rootUrlStr.startsWith("jar:file:")) {
                        int bang = rootUrlStr.indexOf("!");
                        if (bang != -1) {
                            String jarUriStr = rootUrlStr.substring(4, bang);
                            java.io.File file = new java.io.File(new java.net.URI(jarUriStr));
                            cpElements.add(file.getAbsolutePath());
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }
}

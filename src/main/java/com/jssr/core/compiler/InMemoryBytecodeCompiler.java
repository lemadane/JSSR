package com.jssr.core.compiler;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Dynamic in-memory bytecode compiler using JDK's javax.tools.JavaCompiler.
 * Modeled after PTE's (Piped Template Engine) in-memory bytecode compilation architecture.
 * Supports Spring Boot executable fat JAR environments (BOOT-INF/classes, BOOT-INF/lib).
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
                    for (URL url : ucl.getURLs()) {
                        try {
                            cpElements.add(new File(url.toURI()).getAbsolutePath());
                        } catch (Exception ignored) {}
                    }
                }
                cl = cl.getParent();
            }

            ClassLoader tcl = Thread.currentThread().getContextClassLoader();
            while (tcl != null) {
                if (tcl instanceof java.net.URLClassLoader ucl) {
                    for (URL url : ucl.getURLs()) {
                        try {
                            cpElements.add(new File(url.toURI()).getAbsolutePath());
                        } catch (Exception ignored) {}
                    }
                }
                tcl = tcl.getParent();
            }
        } catch (Exception ignored) {}

        try {
            File buildClasses = new File("build/classes/java/main");
            if (buildClasses.exists()) {
                cpElements.add(buildClasses.getAbsolutePath());
            }
        } catch (Exception ignored) {}

        List<File> cpFiles = new ArrayList<>();
        Set<String> added = new HashSet<>();
        for (String path : cpElements) {
            if (path != null && !path.isBlank()) {
                for (String singlePath : path.split(File.pathSeparator)) {
                    if (!singlePath.isBlank() && added.add(singlePath)) {
                        File f = new File(singlePath);
                        if (f.exists()) {
                            cpFiles.add(f);
                        }
                    }
                }
            }
        }

        String fullCp = cpFiles.stream()
                .map(File::getAbsolutePath)
                .reduce((a, b) -> a + File.pathSeparator + b)
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
            var codeSource = clazz.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                URL location = codeSource.getLocation();
                String protocol = location.getProtocol();
                if ("file".equalsIgnoreCase(protocol)) {
                    cpElements.add(new File(location.toURI()).getAbsolutePath());
                } else if ("jar".equalsIgnoreCase(protocol) || "nested".equalsIgnoreCase(protocol)) {
                    String urlStr = location.toExternalForm();
                    addJarOrNestedPath(urlStr, cpElements);
                }
            }
        } catch (Exception ignored) {}

        try {
            String classResourceName = clazz.getSimpleName() + ".class";
            URL url = clazz.getResource(classResourceName);
            if (url != null) {
                addJarOrNestedPath(url.toExternalForm(), cpElements);
            }
        } catch (Exception ignored) {}
    }

    private static void addJarOrNestedPath(String urlStr, List<String> cpElements) {
        if (urlStr == null || urlStr.isBlank()) return;
        try {
            File fatJar = null;
            if (urlStr.startsWith("jar:file:")) {
                int bang = urlStr.indexOf("!");
                if (bang != -1) {
                    String fileUriStr = urlStr.substring(4, bang);
                    fatJar = new File(new URI(fileUriStr));
                }
            } else if (urlStr.contains("nested:")) {
                int nestedIdx = urlStr.indexOf("nested:");
                String nestedSub = urlStr.substring(nestedIdx + 7);
                int bang = nestedSub.indexOf("/!");
                if (bang != -1) {
                    fatJar = new File(nestedSub.substring(0, bang));
                }
            } else if (urlStr.startsWith("file:")) {
                int bang = urlStr.indexOf("!");
                String fileUriStr = (bang != -1) ? urlStr.substring(0, bang) : urlStr;
                fatJar = new File(new URI(fileUriStr));
            }

            if (fatJar != null && fatJar.exists()) {
                cpElements.add(fatJar.getAbsolutePath());
                extractSpringBootFatJarIfNeeded(fatJar, cpElements);
            }
        } catch (Exception ignored) {}
    }

    private static final Set<String> EXTRACTED_JARS = Collections.synchronizedSet(new HashSet<>());

    private static void extractSpringBootFatJarIfNeeded(File fatJar, List<String> cpElements) {
        if (fatJar == null || !fatJar.exists() || !fatJar.getName().endsWith(".jar")) return;

        String key = fatJar.getAbsolutePath() + "_" + fatJar.lastModified();
        File extractDir = new File(System.getProperty("java.io.tmpdir"), "jssr-boot-cp-" + Math.abs(key.hashCode()));

        if (EXTRACTED_JARS.add(key) || !extractDir.exists()) {
            extractDir.mkdirs();
            try (JarFile jarFile = new JarFile(fatJar)) {
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if ((name.startsWith("BOOT-INF/classes/") || name.startsWith("BOOT-INF/lib/")) && !entry.isDirectory()) {
                        File target = new File(extractDir, name);
                        target.getParentFile().mkdirs();
                        try (InputStream is = jarFile.getInputStream(entry);
                             FileOutputStream fos = new FileOutputStream(target)) {
                            is.transferTo(fos);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        File bootClasses = new File(extractDir, "BOOT-INF/classes");
        if (bootClasses.exists()) {
            cpElements.add(bootClasses.getAbsolutePath());
        }

        File bootLib = new File(extractDir, "BOOT-INF/lib");
        if (bootLib.exists() && bootLib.isDirectory()) {
            File[] jars = bootLib.listFiles((dir, name) -> name.endsWith(".jar"));
            if (jars != null) {
                for (File j : jars) {
                    cpElements.add(j.getAbsolutePath());
                }
            }
        }
    }
}

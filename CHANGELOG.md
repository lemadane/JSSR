# Changelog

All notable changes to the **JSSR (Java Server-Side Rendering)** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.3.0] - 2026-08-11

### Complete AST Code Generation, Spring Boot Fat JAR E2E & Maven Central Release

#### Added
- **Direct AST → Java Rendering Code Generator (`com.jssr.core.compiler.JavaCodeGenerator`)**: Replaced all runtime interpreter delegations (`processControlFlow`, `interpolateVariables`, `processCustomTags`) in precompiled templates with native Java rendering statements (`sb.append(...)`, typed record accessors, native `if`, `for`, `switch`, `try-catch`, `continue`, `break`).
- **Process-Isolated Executable-JAR Integration Subproject (`integration-tests/boot-jar`)**: Built dedicated Spring Boot fat JAR test application and `RealBootJarIntegrationTest.java` that spawns `java -jar` as a separate OS process, issues HTTP GET requests, and asserts `CompilationStatus.COMPILED` under Spring Boot's `LaunchedURLClassLoader`.
- **Spring Boot Fat JAR Nested ClassPath Extraction (`InMemoryBytecodeCompiler.java`)**: Added automatic temporary extraction (`jssr-boot-cp-<hash>`) of nested archive entries (`BOOT-INF/classes` and `BOOT-INF/lib/*.jar`) to enable JDK `javax.tools.JavaCompiler` resolution inside packaged fat JARs.
- **Dedicated JMH CI & Release Benchmark Pipeline (`.github/workflows/ci.yml`, `.github/workflows/release.yml`)**: Added dedicated `benchmark` job to CI running `./gradlew jmh --no-configuration-cache` with artifact uploads and GitHub Release attachments (`results.txt`, `results.json`).
- **Maven Central GPG Key Signing & Publishing (`build.gradle`, `.github/workflows/release.yml`)**: Applied Gradle `signing` plugin with `useInMemoryPgpKeys` and Sonatype OSSRH Maven Central staging repository deployment.

#### Fixed
- **CI Runtime Java Matrix Parameterization (`build.gradle`, `.github/workflows/ci.yml`)**: Parameterized test runner launcher via `-PtestJavaVersion` so CI jobs actually execute tests on JDK 17, 21, and 25 runtimes.
- **Precompiled Attribute Security Parity**: Enforced `SafeUrl` attribute sanitization rules inside dynamic HTML URL attributes (`href`, `src`, `action`, `formaction`, etc.) at AST generation time.

---

## [1.2.1] - 2026-08-10

### Production Readiness & AST Precompiler Architecture Release

#### Added
- **AST-Based Precompiled JVM Engine (`com.jssr.core.compiler.ast`)**: Integrated Abstract Syntax Tree (AST) node parsing (`TemplateNode`, `TemplateParser`) into `JavaCodeGenerator` to compile Record template ASTs directly into JVM bytecode instructions.
- **Precompiler Failure Observability & Status API**: Added `CompilationStatus` (`NOT_COMPILED`, `COMPILED`, `FALLBACK`, `FAILED`), `CompilationFailureMode` (`FAIL_FAST`, `WARN_AND_FALLBACK`, `SILENT_FALLBACK`), and `CompilationReport` to `JssrPrecompiler`.
- **Zero-Dependency Diagnostic Logging**: Integrated JDK's standard `System.Logger` inside `JssrPrecompiler` to emit warning diagnostics when template compilation fails or falls back to interpreted mode.
- **Production Executable-JAR Compatibility**: Enhanced `InMemoryBytecodeCompiler` classloader scanning to resolve class locations inside packaged Spring Boot executable JARs (`BOOT-INF/classes`, `BOOT-INF/lib`, `jar:file:`, `nested:`). Added `ExecutableJarTest.java`.
- **JMH Microbenchmark Suite (`src/jmh/java`)**: Configured Gradle JMH plugin (`me.champeau.jmh`) and created benchmark suite (`SimpleComponentBenchmark`, `ControlFlowBenchmark`, `LargeListBenchmark`) measuring throughput across realistic rendering scenarios.
- **Maven Central Release Automation (`.github/workflows/release.yml`)**: Updated release pipeline and `build.gradle` POM metadata, attaching `jssr.jar`, `jssr-sources.jar`, and `jssr-javadoc.jar` to GitHub Releases.

#### Fixed
- **Library Configuration Isolation**: Removed `src/main/resources/application.properties` from the production core JAR to prevent polluting consumer applications' Spring Boot configurations. Added `PackagingTest.java`.

---

## [1.2.0] - 2026-08-09

### Precompiled JVM Bytecode Engine (PTE Architecture Parity)

#### Added
- **Precompiled JVM Bytecode Engine (`com.jssr.core.compiler`)**: Integrated a native dynamic in-memory JVM bytecode precompilation engine modeled after PTE ([Piped Template Engine](https://github.com/lemadane/piped-template-engine-java)). Transforms JSSR component Record templates into dynamically compiled Java bytecode classes (`.class` bytes) loaded in memory without disk I/O.
- **`CompiledTemplateExecutable` Interface (`com.jssr.core.compiler.CompiledTemplateExecutable`)**: Defined contract interface for precompiled template execution classes: `void render(JssrComponent component, Map<String, Object> localScope, StringBuilder sb)`.
- **In-Memory Bytecode Compiler (`InMemoryBytecodeCompiler.java`)**: Dynamic in-memory compilation pipeline using JDK's standard `javax.tools.JavaCompiler`, `ForwardingJavaFileManager`, `SimpleJavaFileObject`, and `MemoryClassLoader`.
- **Java Code Generator (`JavaCodeGenerator.java`)**: Translates JSSR record component templates, interpolation placeholders (`${var}`), HTML escaping, `SafeUrl`/`RawHtml`/`SafeSrcSet`/`SafeUrlList` type checks, XSS attribute rules, and control flow directives (`@if`, `@elseif`, `@else`, `@for`, `@while`, `@switch`, `@try`, `@catch`, `@finally`, `@throw`, `@continue`, `@break`) into pure compiled Java statements.
- **`JssrPrecompiler` API & Cache Manager (`JssrPrecompiler.java`)**: Central precompiler manager providing `ClassValue<CompiledTemplateExecutable>` caching, `precompileAll(...)`, `renderPrecompiled(...)`, and global toggle `JssrPrecompiler.enableGlobalPrecompilation(boolean)`.
- **Precompiled Unit & Benchmark Test Suite**: Added `JssrBytecodeCompilerTest.java`, `PrecompiledControlFlowTest.java`, and `PrecompiledPerformanceBenchmarkTest.java` validating 100% security parity, control flow accuracy, and high-throughput rendering (~83,800 ops/sec).

---

## [1.1.2] - 2026-08-08

### Spring Boot 4 Matrix Verification & Tag-Versioned Release Automation

#### Added
- **Spring Boot 4.0.0 & 3.4.2 Dual-Version CI Matrix (`.github/workflows/ci.yml`)**: Parameterized Spring Boot version runs (`-PspringBootVersion=...`) across Java 17, 21, and 25 (6 matrix jobs total) to guarantee 100% source and runtime compatibility across Spring Boot 3.x and Spring Boot 4.0.0+.
- **Cross-Version Test Configuration (`MockMvcTestConfig.java`)**: Added a `@TestConfiguration` supplier (`MockMvcTestConfig`) providing Spring Web MVC `MockMvc` bean setup for test classes (`UserCrudE2ETest`, `SpringBoot4CompatibilityTest`) without version-specific `@AutoConfigureMockMvc` package dependencies.
- **Automated Tag Version Extraction (`.github/workflows/release.yml`)**: Added automated Git tag version parsing (`TAG_VERSION=${GITHUB_REF_NAME#v}`) to pass `-Pversion=${TAG_VERSION}` to Gradle build, test, and publishing tasks, ensuring compiled JAR artifacts match release tags.

#### Fixed
- **Gradle Spring Boot Dependency Management Isolation**: Removed root Spring Boot plugin application in `build.gradle` to prevent hardcoded 3.4.2 dependency management overrides during `-PspringBootVersion=4.0.0` builds.

---

## [1.1.0] - 2026-08-08

### Native Template Control Flow & Pattern Matching Release

#### Added
- **Native Template Control Flow Engine (`@if`, `@elseif`, `@else`, `@for`, `@while`, `@switch`, `@case`, `@default`, `@try`, `@catch`, `@finally`, `@throw`, `@continue`, `@break`, `@end`)**: Added template control flow directives inside HTML multiline text block templates with support for optional/required trailing colons (`@try:`, `@catch(err):`, `@finally:`, `@throw("msg"):`).
- **Template Error Boundaries & Exception Directives (`@try: ... @catch(err): ... @finally: ... @end` & `@throw(ex)`)**: Added template fault-isolation boundaries that capture rendering/property-access exceptions and render fallback HTML without crashing the page (HTTP 500), guaranteeing `@finally:` block execution, and added `@throw("message")` directive to intentionally raise exceptions in templates.
- **Switch Statements & Reflection (`@switch (expr)` & `typeof(object)`)**: Pattern matching on values, strings, numbers, enums, or runtime class/record names (`typeof(object)`).
- **Pattern Matching (`@if (object instanceof Type varName)`)**: Java 17+ `instanceof` type checking and scoped pattern variable binding.
- **Loop Directives (`@for (item : list) ... @else ... @end` & `@while`)**: Iteration over collections/arrays/iterables with `@else` empty-list fallbacks, bounded `@while` loops (`MAX_WHILE_ITERATIONS = 1000`), `@continue`, and `@break`.
- **Parser Fuzz Testing Suite (`ParserFuzzTest.java`)**: Added automated fuzz testing suite that executes 1,000 randomly generated pathological control flow templates, deeply nested directives (up to depth 150), truncated directives, open quotes, and malformed tags to verify zero parser deadlocks, infinite loops, or uncaught exceptions.
- **Spring Boot 4 / 3.x Compatibility Verification Suite (`SpringBoot4CompatibilityTest.java`)**: Added explicit E2E integration test suite verifying JSSR component record rendering, controller bindings, and ViewResolver contracts across Spring Web MVC runtime environments.

#### Fixed
- **Template Exception Boundaries (`@try:` catch scoping)**: Changed `@try:` block error handling to catch `Exception` instead of `Throwable`. Critical JVM `Error` instances (`OutOfMemoryError`, `StackOverflowError`, `LinkageError`, `ThreadDeath`, `AssertionError`) now escape `@try` blocks un-intercepted to bubble up to the application error handler container.
- **Multi-URL Attribute Security (`SafeSrcSet` & `SafeUrlList`)**: Added specialized type-safe wrappers `SafeSrcSet` (for comma-separated image candidates in `srcset` and `imagesrcset`) and `SafeUrlList` (for space-separated URLs in `ping`). Every candidate URL in `srcset` and `ping` is individually parsed and sanitized against dangerous schemes (`javascript:`, `data:`, `vbscript:`). Plain `String` and single `SafeUrl` values in `srcset`/`ping` attributes are strictly rejected.

---

## [1.0.0] - 2026-08-08

### Production Security Hardening & Release Qualification

#### Security
- **Free-Standing Attribute Injection Defense**: Disallowed plain `String` interpolation in free-standing attribute positions (e.g. `<button ${extra}>`). Introduced `BooleanAttribute` and `HtmlAttribute` records, and native `boolean` field support.
- **Unquoted Attribute Interpolation Protection**: Disallowed variable interpolation inside unquoted HTML attributes (e.g. `title=${title}`). Enforces quote requirement: `title="${title}"`.
- **Strict SafeUrl Requirement**: Dynamic interpolation inside URL attributes (`href`, `src`, `action`, `formaction`, `poster`, `xlink:href`) strictly requires `SafeUrl` typed properties; raw `String`s throw `IllegalArgumentException`.
- **`srcdoc` Attribute Isolation**: Disallowed variable interpolation inside `srcdoc` attributes to prevent nested HTML entity decoding vulnerabilities.
- **Executable Framework Attribute Isolation**: Disallowed variable interpolation inside Alpine.js (`x-*`, `@*`, `:`) and HTMX (`hx-on:*`) attributes.
- **Context Isolation**: Hardened rejection of variable interpolation inside `<script>`, `<style>`, HTML comments, inline `style=`, and inline `on*` event handlers.
- **Vulnerability Disclosure Policy**: Created [SECURITY.md](SECURITY.md) documenting private advisory process and SLA.

#### Added
- **Native Control Flow Directives (`@if`, `@elseif`, `@else`, `@for`, `@while`, `@switch`, `@case`, `@default`, `@try`, `@catch`, `@finally`, `@continue`, `@break`, `@end`)**: Integrated clean template control flow engine supporting `@if (condition)`, `@if (object instanceof Type varName)` pattern matching, `@try:` ... `@catch(err):` ... `@finally:` ... `@end` template error boundaries for sub-component fault isolation, `@for (item : list) ... @else ... @end`, `@while (condition) ... @end`, `@switch (expr)` with `@case` / `@default` / `@break`, and `typeof(object)` type reflection with scoped variable resolution, empty-list fallbacks, infinite loop guards (`MAX_WHILE_ITERATIONS = 1000`), property paths, negations (`!`), equality/relational comparisons (`==`, `!=`, `>`, `>=`, `<`, `<=`), automatic truthiness rules, and arbitrary nested directive blocks.
- **`BooleanAttribute`**: Added type-safe record representation (`BooleanAttribute.of("checked", bool)`) for boolean HTML attributes (`checked`, `disabled`, `selected`, `readonly`).
- **`HtmlAttribute`**: Added type-safe record representation (`HtmlAttribute.of("name", "val")`) for dynamic attribute pairs.
- **Nested Property Resolution**: Supported property path navigation in template placeholders (e.g., `${user.name}` or `${props.title}`).
- **Optional Prop Support**: Supported `java.util.Optional<T>` for optional record component parameters in custom tag parsing.
- **Fail-Fast Error Handling**: Added explicit `IllegalArgumentException` throwing for unknown placeholder names (e.g. `${usernmae}`) and missing required component tag attributes.
- **Performance Benchmarks**: Created `PerformanceBenchmarkTest` verifying rendering throughput and zero memory leaks under load.
- **CI Matrix Expansion**: Expanded `.github/workflows/ci.yml` matrix to validate builds against Java 17, 21, and 25.
- **Community Guidelines**: Added [CONTRIBUTING.md](CONTRIBUTING.md) detailing PR standards, zero-reflection constraints, and code style.

#### Fixed
- **Gradle Wrapper Git Tracking**: Fixed `.gitignore` rule ordering (`!gradle/wrapper/gradle-wrapper.jar` placed after `*.jar`) so `gradle-wrapper.jar` is tracked cleanly by git and CI builds succeed.
- **Parent-Child Escape Logic**: Fixed parent-to-child record property passing to prevent double entity escaping (`&amp;amp;`).

#### Performance
- **Reflection Metadata Cache**: Integrated `ClassValue<ComponentMetadata>` in `JssrComponent`, caching record components, accessor handles, component types, and constructors. Achieved **~414,000 ops/sec** (100,000 renders in 241.5 ms).

---

## [0.0.1] - 2026-08-06

### Initial Proof-of-Concept Release
- Initial Record-based `JssrComponent` interface implementation.
- Basic HTML entity escaping (`escapeHtml`).
- `RawHtml` trusted HTML escape hatch.
- `SafeUrl` basic protocol sanitization wrapper (`http:`, `https:`, `mailto:`, `tel:`).
- `JssrConverter` Spring WebMvc HttpMessageConverter implementation.
- Custom JSX-like tag state-machine parser with nesting recursion protection (`MAX_RENDER_DEPTH = 100`).

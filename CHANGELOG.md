# Changelog

All notable changes to the **JSSR (Java Server-Side Rendering)** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.1.0] - 2026-08-08

### Native Template Control Flow & Pattern Matching Release

#### Added
- **Native Template Control Flow Engine (`@if`, `@elseif`, `@else`, `@for`, `@while`, `@switch`, `@case`, `@default`, `@try`, `@catch`, `@finally`, `@throw`, `@continue`, `@break`, `@end`)**: Added template control flow directives inside HTML multiline text block templates with support for optional/required trailing colons (`@try:`, `@catch(err):`, `@finally:`, `@throw("msg"):`).
- **Template Error Boundaries & Exception Directives (`@try: ... @catch(err): ... @finally: ... @end` & `@throw(ex)`)**: Added template fault-isolation boundaries that capture rendering/property-access exceptions and render fallback HTML without crashing the page (HTTP 500), guaranteeing `@finally:` block execution, and added `@throw("message")` directive to intentionally raise exceptions in templates.
- **Switch Statements & Reflection (`@switch (expr)` & `typeof(object)`)**: Pattern matching on values, strings, numbers, enums, or runtime class/record names (`typeof(object)`).
- **Pattern Matching (`@if (object instanceof Type varName)`)**: Java 17+ `instanceof` type checking and scoped pattern variable binding.
- **Loop Directives (`@for (item : list) ... @else ... @end` & `@while`)**: Iteration over collections/arrays/iterables with `@else` empty-list fallbacks, bounded `@while` loops (`MAX_WHILE_ITERATIONS = 1000`), `@continue`, and `@break`.

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

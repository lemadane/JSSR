# Contributing to JSSR

Thank you for your interest in contributing to **JSSR (Java Server-Side Rendering)**! We welcome contributions that maintain our high standards for security, performance, and code quality.

---

## Code of Conduct

All contributors are expected to uphold respectful, inclusive, and professional collaboration across all project interactions.

---

## How to Report Bugs

Before creating a bug report, check existing open issues to avoid duplicates.

When submitting a bug report:
1. Use the **Bug Report** issue template.
2. Provide a clear, descriptive title.
3. Include your Java runtime version (`java -version`), OS, and Gradle version.
4. Supply a minimal reproducible code example or test case demonstrating the bug.
5. If reporting an error, include the un-truncated stack trace.

> [!CAUTION]
> **Security Vulnerabilities**: Do NOT submit security vulnerabilities through public GitHub issues. Follow the private disclosure process in [SECURITY.md](SECURITY.md).

---

## Allocation & Performance Constraints

JSSR is designed for zero-dependency, ultra-fast SSR workloads in production environments. Any code changes must respect the following strict performance constraints:

1. **Zero Runtime Reflection Overhead**: All component reflection must be cached via `ClassValue<ComponentMetadata>`. No repeated reflection calls (`getRecordComponents`, `getMethod`) on render passes.
2. **Minimal Garbage Collection / Memory Allocation**: Avoid allocating transient maps, regex objects, or array buffers during `interpolateVariables` or `render()`.
3. **Single-Pass Parsing**: Scanning and interpolation must complete in a single pass over the template string.
4. **Zero Production Runtime Dependencies**: `jssr-core` must retain ZERO third-party runtime dependencies.

---

## Pull Request Standards

All Pull Requests (PRs) must meet the following criteria before approval:

### 1. Architectural Guidelines
- **Record-First Design**: UI components must be implemented as immutable Java Records implementing `JssrComponent`.
- **Fail-Closed Security**: Any ambiguous or potentially unsafe context interpolation must fail closed with an explicit `IllegalArgumentException`.
- **Explicit Type Escaping**: Use `SafeUrl`, `BooleanAttribute`, `HtmlAttribute`, or `RawHtml` for specialized contexts; never allow plain `String` to produce raw HTML or dynamic attributes.

### 2. Testing Requirements
- Every new feature or bug fix MUST include corresponding unit tests in `src/test/java/com/jssr/core/`.
- Security fixes MUST include regression test cases proving payload neutralization.
- Run the full test suite locally against both Spring Boot 3.4.2 and Spring Boot 4.0.0 before submitting a PR:
  ```bash
  ./gradlew test --rerun-tasks
  ./gradlew test -PspringBootVersion=4.0.0 --rerun-tasks
  ```
- All automated test matrix jobs across Java 17, 21, and 25 $\times$ Spring Boot 3.4.2 & 4.0.0 must pass in CI.

### 3. Code Style & Conventions
- Follow standard Java code formatting conventions.
- Maintain clean docstrings for public APIs (`JssrComponent`, `SafeUrl`, `RawHtml`, `BooleanAttribute`, `HtmlAttribute`).
- Keep lines concise and avoid unnecessary code churn.

---

## Development Setup

1. **Clone the repository**:
   ```bash
   git clone https://github.com/lemadane/JSSR.git
   cd JSSR
   ```
2. **Ensure Java 17+ is installed**:
   ```bash
   export JAVA_HOME=/path/to/jdk-17
   ```
3. **Build and test**:
   ```bash
   ./gradlew build
   ./gradlew test
   ```

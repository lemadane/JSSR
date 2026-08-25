# JSSR (Java Server-Side Rendering)

> [!NOTE]
> **No Node.js or JavaScript Runtime Embedding**: JSSR brings the **React-like component architecture** (JSX-style tags, immutable Record props, nested component trees) to native Java 17+ in **100% pure Java**—with zero dependencies on Node.js, V8 engines, or JavaScript runtimes.

---

## Why Use JSSR?

Traditional Java web development forces developers into a hard choice between traditional template engines (Thymeleaf, JSP, FreeMarker) with untyped HTML templates, or heavy Single Page Application (SPA) frameworks (React, Next.js, Vue) requiring complex Node.js build pipelines and duplicate DTO definitions.

- **React-Like Developer Experience in 100% Pure Java**: JSSR brings the component-driven mental model of React (JSX-style tags, immutable Record props, nested component trees) to modern Java 17+ without embedding Node.js, V8 engines, or JavaScript runtimes.

- **Strongly Typed Components with Runtime-Validated Template Props**: Every UI component is an immutable Java Record. Direct Java component instantiations are 100% compiler verified, and JSX-style template props are validated at runtime to reject typos and missing attributes.

- **Native Java 17 Multiline Text Blocks with ${fieldName} Interpolation**: HTML templates use native Java text blocks (`"""..."""`). Placeholders like `${name}` or `${role}` are automatically interpolated from Record fields with automatic default HTML escaping (`&`, `<`, `>`, `"`, `'`).

- **Safe URL & Raw HTML Protection**: Use `SafeUrl` wrappers to automatically sanitize dangerous URL schemes (`javascript:`, `vbscript:`, `data:`), and `RawHtml` to explicitly bypass HTML escaping for trusted content.

- **Declarative JSX-Style & Paired Component Trees**: Compose self-closing (`<UserCard name="Sarah" />`) or paired (`<Card><h1>Header</h1></Card>`) component trees in Java. JSSR resolves custom tags, passes inner content as `children` props, and renders nested component trees recursively with depth recursion protection.

- **Micro-Granular SSR for HTMX & Alpine.js**: Every component is an independent executable unit. Spring MVC controllers can return single component instances (`return new UserRow(user);`) for microsecond HTMX swaps.

- **Precompiled JVM Bytecode Engine (PTE Architecture)**: Dynamically compile JSSR Record component templates into pure JVM bytecode classes (`.class` bytes) loaded in memory using JDK's standard `javax.tools.JavaCompiler`. Executes templates directly as compiled JVM instructions for maximum rendering throughput.

- **Zero-Dependency Core Engine**: The core JSSR rendering engine (`com.jssr.core.JssrComponent`) is written in 100% pure standard Java with zero mandatory third-party dependencies.

### Quick Comparison

| Feature | Thymeleaf / JSP | React / Next.js + Java | JSSR |
| :--- | :--- | :--- | :--- |
| **Component Architecture** | Weak / Fragment includes | Excellent | **React-like Java Records** |
| **Type Safety** | None (Untyped Strings) | TypeScript (Duplicate DTOs) | **Strongly Typed Records + Prop Validation** |
| **Build Toolchain** | Maven / Gradle | Node.js + npm + Webpack + Maven | **Gradle / Maven Only** |
| **HTMX / Fragment SSR** | Clunky fragments | Not Supported (JSON APIs) | **Native Component Swapping** |
| **Performance** | Template Parsing Overhead | Client-side JS Bundle Overhead | **Precompiled JVM Bytecode AST (~4.3M ops/sec)** |

---

## Todo App Demo (Spring MVC + JSSR Components)

This repository includes a runnable demo subproject at `todo-app` that showcases React-like server component composition using JSSR record components.

### What This Demo Shows

- **Record-as-props component model** (immutable record inputs)
- **Nested JSX-style custom tags** (`<TodoForm />`, `<TodoList />`, `<TodoRow />`, etc.)
- **Server-rendered CRUD-style actions** (add, toggle, edit, delete)
- **Type-preserving custom tag prop passing** (objects/lists passed through custom tags, not degraded to raw strings)

### Component Tree

The page composition mirrors a React-style tree:

`TodoPage` -> `TodoForm` + `TodoList`  
`TodoList` -> `TodoRow`  
`TodoRow` -> `TodoActionBar`  
`TodoActionBar` -> `TodoItemActions`

Domain model:

- `Todo` is a Java record (`id`, `title`, `completed`) with immutable state helpers (`toggle`, `withTitle`)

### Supported Todo Operations

- Add task
- Filter tasks by query
- Toggle done/open state
- Edit task title
- Delete task
- Partial rendering (list-only and stats-only HTML responses)

Delete is rendered as a per-row action button in `TodoItemActions`, and handled by a dedicated controller route.

### HTTP Endpoints

- `GET /` and `GET /todos` -> render full page
- `POST /todos` -> add task (`application/x-www-form-urlencoded`)
- `POST /todos/{id}/toggle` -> toggle completion
- `POST /todos/{id}/edit` -> edit title (`application/x-www-form-urlencoded`)
- `POST /todos/{id}/delete` -> delete task
- `GET /todos/fragment/list?q=...` -> list fragment HTML only
- `GET /todos/fragment/stats` -> stats fragment HTML only
- `GET /todos/partial/list?q=...` -> partial list HTML alias
- `GET /todos/partial/stats` -> partial stats HTML alias

### Partial Rendering (Core, No HTMX Required)

JSSR partial rendering in this demo is handled directly at the Spring MVC controller level by returning individual `JssrComponent` fragments.

Examples:

```bash
# Render only the todo list fragment
curl -s "http://127.0.0.1:8080/todos/partial/list?q=demo"

# Render only the completed/total stats fragment
curl -s "http://127.0.0.1:8080/todos/partial/stats"
```

This works with plain Spring MVC and JSSR converter output, independent of HTMX.

### Run The Demo

From repository root:

```bash
./gradlew :todo-app:bootRun
```

Open:

- `http://127.0.0.1:8080/todos`

If you do not see the latest UI actions (like **Delete**), restart `bootRun` to ensure the running server reflects the current source.

### Run Tests (Including Add/Toggle/Delete Flows)

```bash
./gradlew :todo-app:test
```

Current demo integration coverage includes:

- Page load smoke test
- Add + toggle flow test
- Add + delete flow test

### Key Demo Files

- `todo-app/src/main/java/com/jssr/demo/todo/TodoController.java`
- `todo-app/src/main/java/com/jssr/demo/todo/TodoPage.java`
- `todo-app/src/main/java/com/jssr/demo/todo/TodoForm.java`
- `todo-app/src/main/java/com/jssr/demo/todo/TodoList.java`
- `todo-app/src/main/java/com/jssr/demo/todo/TodoRow.java`
- `todo-app/src/main/java/com/jssr/demo/todo/TodoActionBar.java`
- `todo-app/src/main/java/com/jssr/demo/todo/TodoItemActions.java`
- `todo-app/src/main/java/com/jssr/demo/todo/Todo.java`
- `todo-app/src/test/java/com/jssr/demo/todo/TodoAppApplicationTests.java`

---

## How Variable Interpolation & Security Work

> [!IMPORTANT]
> **Core Security Principle**: *Strings are data, JSX is markup, and trusted HTML must be explicit.*
> Application developers are never required to manually escape values. XSS protection is built directly into JSSR's core interpolation and rendering pipeline by default.

In JSSR component Records, any field (e.g. `name`, `role`, `active`) can be referenced directly using `${fieldName}` syntax inside multiline text blocks:

```java
public record UserCard(String name, String role, boolean active) implements JssrComponent {

    @Override
    public String render() {
        return """
            <div class="user-card border p-4 rounded-xl">
                <h3 class="font-bold text-lg">${name}</h3>
                <p class="text-sm text-gray-500">${role}</p>
                <span class="badge">${active}</span>
            </div>
            """;
    }
}
```

---

## Native Template Control Flow Directives

JSSR provides a rich, native control flow engine designed for Java 17+ record templates. Control flow directives support clean syntax with optional or required trailing colons (`:`), full block nesting, and automatic variable scoping.

### 📋 Control Flow Directives Reference

| Directive Syntax | Key Feature & Purpose | Typical Example |
| :--- | :--- | :--- |
| `@if(cond) { ... } @elseif(cond) { ... } @else { ... }` | Conditional branching with property paths, negations (`!`), and comparisons | `@if (user.role == 'ADMIN') { ... }` |
| `@if(obj instanceof Type var) { ... }` | Java 17+ pattern matching with scoped variable binding | `@if (user instanceof AdminUser admin) { ... }` |
| `@for(item : collection) { ... } @else { ... }` | Collection & array iteration with empty-list fallback rendering | `@for (item : user.projects) { ... }` |
| `@switch(expr) { @case(val) { ... } @default { ... } }` | Value pattern matching & polymorphic runtime type inspection | `@switch (typeof(account)) { ... }` |
| `@try { ... } @catch(err) { ... } @finally { ... }` | Template error boundaries for component fault isolation and safe fallback UI | `@try { ... } @catch(e) { ... }` |
| `@throw("msg")` / `@throw(new Ex("msg"))` / `@throw(ex)` | Intentionally raise custom or instantiated exceptions inside templates | `@throw(new IllegalStateException("Quorum Lost"))` |
| `@continue` / `@break` | Early iteration skipping (`@continue`) or loop/switch termination (`@break`) | `@continue` / `@break` |

---

### 1. Conditional Branching & Pattern Matching (`@if`, `instanceof`)

JSSR supports truthiness evaluation (`boolean`, non-blank `String`, non-zero `Number`, non-empty `Collection`/`Map`, `Optional.isPresent()`), relational operators (`==`, `!=`, `>`, `>=`, `<`, `<=`), and Java 17+ `instanceof` pattern variable scope binding:

```java
public record UserHeaderCard(Object user) implements JssrComponent {

    @Override
    public String render() {
        return """
            <div class="user-card font-sans p-4 bg-slate-900 rounded-xl">
                <!-- Instanceof Pattern Matching with Automatic Local Scope Binding -->
                @if (user instanceof com.jssr.e2e.app.model.AdminUser admin) {
                    <div class="admin-badge text-purple-400 font-semibold">
                        🛡️ System Administrator: ${admin.name} (Permissions: ${admin.permissions})
                    </div>
                } @elseif (user instanceof com.jssr.e2e.app.model.DeveloperUser dev) {
                    <div class="dev-badge text-blue-400">
                        💻 Engineer: ${dev.name} (${dev.githubHandle} - ${dev.primaryLanguage})
                    </div>
                } @else {
                    <div class="guest-badge text-slate-400">
                        👤 Guest Account
                    </div>
                }
            </div>
            """;
    }
}
```

---

### 2. Collection Iteration & Empty-List Fallbacks (`@for`, `@else`, `@continue`, `@break`)

Iterate seamlessly over `Collection`, `Iterable`, `Object[]`, or `Optional<Collection>`. Use `@else:` to render fallback UI when collections are empty or null, and use `@continue` or `@break` for loop control:

```java
public record ProjectListCard(List<Project> projects) implements JssrComponent {

    @Override
    public String render() {
        return """
            <div class="project-container p-6 bg-slate-950 text-slate-100 rounded-xl">
                <h3 class="text-lg font-bold mb-4">Cluster Projects</h3>

                <div class="project-list space-y-2">
                    @for (p : projects) {
                        @if (p.priority < 0) {
                            @continue <!-- Skip internal debug projects -->
                        }
                        @if (p.priority > 99) {
                            <div class="alert text-rose-500 font-bold">Priority Overflow (${p.name})</div>
                            @break <!-- Halt iteration on overflow -->
                        }

                        <div class="project-item flex justify-between p-3 bg-slate-900 rounded border border-slate-800">
                            <span class="font-mono text-sm">${p.name}</span>
                            <span class="text-xs px-2 py-1 bg-slate-800 text-slate-300">${p.status}</span>
                        </div>
                    } @else {
                        <!-- Rendered automatically when projects list is empty or null -->
                        <div class="empty-state p-6 text-center text-slate-500 italic">
                            No active cluster projects found.
                        </div>
                    }
                </div>
            </div>
            """;
    }
}
```

---

### 3. Polymorphic Switch Statements & Runtime Type Reflection (`@switch`, `typeof`)

Pattern match on strings, numbers, enums, or runtime class/record names using `typeof(object)` with support for brace blocks `{ ... }`, colon syntax `:`, and explicit fallthrough rules:

```java
public record RoleBadge(Object account) implements JssrComponent {

    @Override
    public String render() {
        return """
            <div class="role-badge">
                @switch (typeof(account)) {
                    @case ('AdminUser') {
                        <span class="badge bg-purple-500/10 text-purple-400 font-bold px-3 py-1 rounded-full">
                            System Administrator
                        </span>
                        @break
                    }
                    @case ('DeveloperUser') {
                        <span class="badge bg-blue-500/10 text-blue-400 font-bold px-3 py-1 rounded-full">
                            Software Engineer
                        </span>
                        @break
                    }
                    @default {
                        <span class="badge bg-slate-800 text-slate-400 px-3 py-1 rounded-full">
                            Guest Session
                        </span>
                    }
                }
            </div>
            """;
    }
}
```

#### Fallthrough & Syntax Options (`@break`, Colon Syntax)

- **Fallthrough Semantics**: Omitting `@break` allows rendering to fall through into subsequent `@case` and `@default` bodies until `@break` is hit or the `@switch` block finishes.
- **Colon Syntax & Mixed Formats**: Supports brace blocks (`@case(val) { ... }`), colon syntax (`@case(val): ...`), and colon cases inside an outer brace block `@switch (val) { ... }`:

```html
@switch (tier) {
    @case ('PLATINUM'):
        <span>Platinum Perks</span>   <!-- Falls through to GOLD (no @break) -->
    @case ('GOLD'):
        <span>Gold Perks</span>
        @break                       <!-- Stops fallthrough -->
    @default:
        <span>Standard Perks</span>
}
```

---

### 4. Template Error Boundaries & Intentional Exception Raising (`@try:`, `@catch(err):`, `@finally:`, `@throw`)

Isolate sub-component or property evaluation failures without crashing the page (HTTP 500), or intentionally trigger exceptions via `@throw(...)`:

```java
public record ResilientDashboardCard(String validStatus, boolean triggerFault) implements JssrComponent {

    @Override
    public String render() {
        return """
            <div class="resilient-card p-6 bg-slate-900 text-slate-100 rounded-xl">
                <div class="status mb-4 text-emerald-400 font-semibold">
                    Primary Service Status: ${validStatus}
                </div>

                <!-- Template Error Boundary -->
                @try {
                    @if (triggerFault) {
                        <!-- Option A: Standard String Message -->
                        @throw("Manual Telemetry Fault Triggered via @throw")

                        <!-- Option B: Exception Class Instantiation -->
                        <!-- @throw(new java.lang.IllegalStateException("Cluster Quorum Lost")) -->

                        <!-- Option C: Existing Property / Throwable Variable -->
                        <!-- @throw(customThrowableVar) -->
                    } @else {
                        <div class="healthy-widget text-blue-400">
                            ⚡ Secondary Analytics Connected (Latency: 12ms)
                        </div>
                    }
                } @catch(e) {
                    <div class="widget-fallback p-4 bg-amber-500/10 border border-amber-500/20 text-amber-400 text-xs font-mono rounded-lg">
                        ⚠️ Microservice widget isolated cleanly (Type: ${typeof(e)} | ${e.message})
                    </div>
                } @finally {
                    <div class="audit-log text-slate-500 text-xs mt-2 font-mono">
                        [Audit]: Telemetry session verified.
                    </div>
                }
            </div>
            """;
    }
}
```

#### Exception Raising (`@throw`) Modes:

| `@throw` Syntax | Behavior & Type Resolution | Resulting Exception |
| :--- | :--- | :--- |
| **`@throw("Custom message")`** | Throws standard `RuntimeException` with message string. | `new RuntimeException("Custom message")` |
| **`@throw(new MyException("msg"))`** | Dynamically resolves and instantiates the specific `Throwable` class (e.g. `IllegalStateException`, `IllegalArgumentException`, or custom package class). | `new MyException("msg")` |
| **`@throw(exVar)`** | Resolves property or variable `exVar` from local scope or record fields; if `Throwable`, throws it directly. | Throws underlying `Throwable` |

---

## Precompiled JVM Bytecode Engine (PTE Architecture)

JSSR includes a native **Precompiled JVM Bytecode Engine** modeled after **PTE ([Piped Template Engine](https://github.com/lemadane/piped-template-engine-java))**.

Instead of interpreting template strings at runtime on every `.render()` call, JSSR precompiles template definitions into dynamically loaded JVM bytecode classes (`.class` bytes) using JDK's standard `javax.tools.JavaCompiler` (`InMemoryBytecodeCompiler`).

```java
import com.jssr.core.compiler.JssrPrecompiler;

// 1. Enable global precompiled JVM bytecode rendering for all JssrComponent.render() calls
JssrPrecompiler.enableGlobalPrecompilation(true);

// 2. Pre-compile component classes ahead of time (optional warm-up)
JssrPrecompiler.precompileAll(List.of(UserCard.class, ProjectListCard.class));

// 3. Render component - automatically executes precompiled JVM bytecode instructions
UserCard card = new UserCard("Sarah", "Lead Architect", true);
String html = card.render(); // Native compiled JVM execution (~83,800 ops/sec)

// 4. Or invoke precompiled rendering directly per component instance
String precompiledHtml = card.renderPrecompiled();
```

### Key Performance & Security Guarantees
- **In-Memory Compilation**: Zero temporary `.class` files written to disk during application runtime.
- **Microsecond Throughput**: Achieves **~4.3 Million ops/sec** (4,302,200 renders/sec on simple components, 2,625,700 renders/sec on control flow).
- **100% Security Parity**: Precompiled JVM bytecode enforces identical HTML entity escaping, `RawHtml` trusted markup, `SafeUrl` scheme sanitization, `SafeSrcSet` candidate parsing, and attribute XSS rules.
- **Graceful Fallback**: Automatically falls back to standard interpreted execution if running in a minimal JRE environment without `javax.tools.JavaCompiler`.

---

JSSR enforces strict security and rendering rules for variable interpolation:

1. **Default HTML Escaping**: Every `${...}` interpolation escapes special HTML characters by default:
   - `&` $\rightarrow$ `&amp;`
   - `<` $\rightarrow$ `&lt;`
   - `>` $\rightarrow$ `&gt;`
   - `"` $\rightarrow$ `&quot;`
   - `'` $\rightarrow$ `&#39;`

2. **HTML Text Context Handling**: Text node values like `Hello <strong>World</strong>` in `<p>${message}</p>` safely render as `<p>Hello &lt;strong&gt;World&lt;/strong&gt;</p>`.

3. **HTML Attribute Context Protection**: Quotes in attributes like `<input value="${value}">` or `<div title='${title}'>` are converted to `&quot;` and `&#39;`, preventing attribute breakout XSS attacks.

4. **Arbitrary Structure Prevention**: Injected strings containing tags (e.g. `</div><script>alert(1)</script>`) are escaped into text entities so interpolation can never alter the outer HTML tag structure.

5. **Explicit Trusted HTML (`RawHtml`)**: Trusted pre-rendered HTML must explicitly use the `RawHtml.of("<b>bold</b>")` wrapper type. Arbitrary `String` values are never trusted by default.

6. **Nested Component Rendering**: Fields typed as `JssrComponent` (child components and fragments) render their DOM structure recursively without being text-escaped.

7. **Null Value Handling**: `null` values produce empty output (`""`) rather than rendering literal `"null"` text (e.g. `<span>${nullVal}</span>` renders `<span></span>`).

8. **Primitive & Scalar Preservation**: Numbers (`42`, `3.14`), booleans (`true`), enums, and scalar values render as clean, un-mangled text.

9. **Single-Pass Escaping & Double Escaping Prevention**: Interpolation uses a single-pass scanner, guaranteeing values like `Tom & Jerry` are escaped exactly once to `Tom &amp; Jerry` (never `Tom &amp;amp; Jerry`).

10. **HTML Grammar Context Detection**: Interpolation uses an HTML state-machine parser to track tags, quoted attributes, comments, and script/style blocks.

11. **Unsafe Context Rejection**: `${...}` interpolation inside `<script>`, `<style>`, HTML comment blocks (`<!-- ... -->`), inline event handler attributes (`onclick`, `onload`, `on*`), or inline `style="..."` attributes is strictly forbidden and throws a clear `IllegalArgumentException` at render time.

12. **URL Attribute Protection & Sanitization**: `SafeUrl` wrappers enforce a strict scheme allowlist (`http`, `https`, `mailto`, `tel`, relative URLs) while sanitizing dangerous protocols (`javascript:`, `vbscript:`, `data:`) to `about:blank` and escaping quote characters.

13. **Standard Java 17 Syntax Preservation**: Developers write native Java 17 multiline text blocks (`"""..."""`) and `${fieldName}` syntax without extra pre-processors, macros, or custom DSL extensions.

14. **Simple 3-Pattern Developer API**:
    - *Normal data* (`String`, numbers, booleans) $\rightarrow$ automatically safe and escaped.
    - *Child components* (`JssrComponent`) $\rightarrow$ rendered recursively as raw DOM markup.
    - *Trusted HTML* (`RawHtml.trustedHtml(html)`) $\rightarrow$ explicitly marked raw HTML.

15. **Strong Value Typing (No String Guessing)**: Unescaped markup rendering is strictly type-bound (`RawHtml` or `JssrComponent`). Plain Java `String` values are **always** treated as untrusted data and are never pattern-guessed (e.g. checking if a string starts with `<`).

16. **Comprehensive XSS Test Suite**: Protected by automated regression tests covering high-risk XSS attack vectors (`<script>`, `<img onerror>`, quote breakout, `</textarea>`).

---

## Automatic Escaping

In JSSR, every `${...}` variable interpolation is **escaped by default**.

### Why Automatic Escaping Prevents XSS

Cross-Site Scripting (XSS) vulnerabilities occur when untrusted user input containing HTML or JavaScript tags (such as `<script>`) is injected directly into web pages and executed by the browser. By automatically converting special characters (`&`, `<`, `>`, `"`, `'`) into safe HTML entities (`&amp;`, `&lt;`, `&gt;`, `&quot;`, `&#39;`), JSSR ensures user input is rendered strictly as plain visible text rather than executable markup.

### Code Example

```java
public record GreetingProps(String name) {}

public record Greeting(GreetingProps props) implements JssrComponent {
    @Override
    public String render() {
        return """
            <h1>Hello ${props.name}</h1>
            """;
    }
}
```

Passing a malicious string payload:

```java
new Greeting(new GreetingProps("<script>alert(1)</script>")).render();
```

Renders the following safe HTML output:

```html
<h1>Hello &lt;script&gt;alert(1)&lt;/script&gt;</h1>
```

The browser displays the literal text `Hello <script>alert(1)</script>` on screen and **does NOT execute JavaScript**.

### Key Rules for Rendered Content

1. **Child Components are Rendered as JSSR Markup**: Fields typed as `JssrComponent` (child components and DOM fragments) are recognized by JSSR's type system and rendered recursively as raw HTML element markup without being escaped as text.
2. **How to Intentionally Insert Trusted HTML**: When you intentionally need to insert already-safe HTML (e.g. sanitized rich text from a trusted CMS), explicitly wrap the string using `RawHtml.trustedHtml(html)` or `RawHtml.of(html)`:
   ```java
   public record Article(RawHtml content) implements JssrComponent {
       @Override
       public String render() {
           return """
               <article>${content}</article>
               """;
       }
   }

   // Usage:
   new Article(RawHtml.trustedHtml("<b>Trusted rich text</b>")).render();
   ```
3. **Security Responsibility**: Trusted HTML (`RawHtml`) bypasses escaping and should **only** be used for content the developer explicitly knows is safe. Standard `String` fields should always be used for user-supplied data.

---

## Data Flow and Component Immutability

Because JSSR components are Java Records, they are **immutable and stateless**. Components are never mutated in-place on the server. Data flows through a 3-step unidirectional cycle:

1. **Form Rendering (Server to Browser)**: The controller instantiates an immutable record `new UserForm("Sarah Connor", ...)` which renders an HTML form containing pre-filled input values (`<input name="name" value="Sarah Connor" />`).

2. **User Interaction (Browser)**: The user modifies the input field in their browser (e.g. typing `"Sarah Connor Revised"`) and submits the form via HTMX or standard HTTP POST.

3. **Controller Response (Server)**: The Spring MVC controller handler receives the HTTP request parameters (`@RequestParam("name") String name`), updates database entities, and instantiates a **new Record component instance** (`new UserList(...)` or `new UserRow(...)`) with the updated data.

---

## Importing from JitPack

JSSR can be referenced from JitPack (`com.github.lemadane:JSSR`) for both Gradle and Maven projects.

### Gradle (Groovy)

Add the JitPack repository to `build.gradle`:

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.lemadane:JSSR:v1.1.2'
}
```

### Maven

Add the JitPack repository and dependency to `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.lemadane</groupId>
        <artifactId>JSSR</artifactId>
        <version>v1.1.2</version>
    </dependency>
</dependencies>
```

### Publishing Releases on GitHub

To publish an official version tag (e.g. `v1.1.2`) on GitHub:

```bash
git tag -a v1.1.2 -m "Release v1.1.2"
git push origin v1.1.2
```

---

## How to Use JSSR in a Spring Boot MVC Application

### Step 1: Enable JSSR Spring MVC Auto-Configuration

Import `JssrMvcConfig` into your main Spring Boot application class to automatically register `JssrConverter` into Spring MVC's converter chain:

```java
package com.example.demo;

import com.jssr.spring.JssrMvcConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(JssrMvcConfig.class)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### Step 2: Define UI Components as Java Records

Every component is an immutable Java Record implementing `JssrComponent`. Implement the `template()` method returning a Java 17 multiline text block string:

```java
package com.example.demo.components;

import com.jssr.core.JssrComponent;

public record UserCard(String name, String role, boolean active) implements JssrComponent {

    @Override
    public String render() {
        return """
            <div class="user-card border p-4 rounded-xl">
                <h3 class="font-bold text-lg">${name}</h3>
                <p class="text-sm text-gray-500">${role}</p>
                <span class="badge">${active}</span>
            </div>
            """;
    }
}
```

### Step 3: Compose Components with Custom Tags

Child component tags can be statically registered and composed directly inside parent component text blocks (both self-closing and paired tags):

```java
package com.example.demo.components;

import com.jssr.core.JssrComponent;
import java.util.List;

public record UserList(List<UserCard> users) implements JssrComponent {
    static {
        JssrComponent.register("UserCard", UserCard.class);
    }

    @Override
    public String render() {
        return """
            <div id="user-list" class="space-y-4">
                <UserCard name="Sarah Connor" role="Admin" active="true" />
                <UserCard name="Alex Mercer" role="Developer" active="false" />
            </div>
            """;
    }
}
```

### Step 4: Return Components from Spring MVC Controllers

Return `JssrComponent` instances directly from Spring `@Controller` handler methods. `JssrConverter` automatically serializes the returned component into a UTF-8 HTML HTTP response stream:

```java
package com.example.demo.controllers;

import com.jssr.core.JssrComponent;
import com.example.demo.components.UserCard;
import com.example.demo.components.UserList;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@ResponseBody
@RequestMapping(produces = "text/html;charset=UTF-8")
public class UserController {

    @GetMapping("/users/{id}")
    public JssrComponent getUser(@PathVariable Long id) {
        return new UserCard("Sarah Connor", "Admin", true);
    }

    @GetMapping("/users")
    public JssrComponent listUsers() {
        return new UserList(List.of());
    }
}
```

---

---

## 🚀 Server-Driven Micro-Component Architecture

JSSR enables a modern **Server-Driven Micro-Component Architecture** combining **Spring MVC Controllers**, **JSSR Layout Components**, **HTMX**, and **Alpine.js**.

This architecture brings SPA-like responsiveness, microsecond partial DOM updates, and full browser history navigation to Java applications—**with zero Node.js, npm, or JavaScript build pipelines**.

```text
┌───────────────────────────────────────────────────────────┐
│                     BACKEND (Server)                      │
│  • Spring Boot     → Controllers & Route Handlers         │
│  • JSSR            → Strongly-typed Java Record HTML      │
│                      Component Rendering Engine           │
└─────────────────────────────┬─────────────────────────────┘
                              │
                    HTTP / HTML Fragments
                              │
┌─────────────────────────────▼─────────────────────────────┐
│                    FRONTEND (Browser)                     │
│  • HTMX            → Server-driven AJAX & partial DOM     │
│                      swaps (hx-get, hx-post, hx-push-url)│
│  • Alpine.js       → Micro client-side reactivity       │
│                      (dropdowns, modals, tabs, x-model)   │
│  • Tailwind CSS    → Utility-first styling via CDN      │
└───────────────────────────────────────────────────────────┘
```

### Key Architectural Pillars

1. **Spring MVC Controllers (Route Handlers)**: Server endpoints inspect incoming headers (e.g. `HX-Request`). Full page visits return a `JssrComponent` wrapped in an `AppLayout`, while HTMX AJAX swaps return **only the micro-component fragment**.
2. **JSSR Layout Components (React Router `<Outlet />` Equivalent)**: Wrap page views with shared headers, navigation bars, and page metadata via slot composition (`new AppLayout("Users", new UserTable(users))`).
3. **HTMX (Server-Driven Partial DOM Swaps)**: Background AJAX requests fetch micro HTML fragments from JSSR and update the browser URL bar with `hx-push-url="true"`.
4. **Alpine.js (Micro-Client Reactivity)**: Handles instant in-browser state (modals, dropdowns, client input formatting) directly inside JSSR template text blocks.

### Architecture Code Pattern

```java
// 1. Reusable Page Layout Component
public record AppLayout(String title, JssrComponent content) implements JssrComponent {
    @Override
    public String render() {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <title>${title}</title>
                <script src="https://cdn.tailwindcss.com"></script>
                <script src="https://unpkg.com/htmx.org@1.9.10"></script>
            </head>
            <body class="bg-slate-950 text-slate-100">
                <main id="page-container" class="max-w-6xl mx-auto p-6">
                    ${content}
                </main>
            </body>
            </html>
            """;
    }
}

// 2. Dual Full-Page & Fragment Controller Handler
@Controller
@RequestMapping("/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    public JssrComponent listUsers(
            @RequestParam(required = false) String query,
            @RequestHeader(value = "HX-Request", required = false) boolean isHtmx) {
        
        List<User> users = (query != null && !query.isBlank()) 
                ? userService.search(query) 
                : userService.findAll();

        JssrComponent userTable = new UserTableComponent(users);

        // HTMX request -> Return ONLY micro HTML fragment (< 1KB)
        // Direct Browser Visit / Bookmark -> Wrap in full AppLayout HTML
        return isHtmx ? userTable : new AppLayout("Users Directory", userTable);
    }
}
```

---

## Working with HTML Forms & Spring MVC

JSSR makes handling HTML form submission, input pre-filling, validation error feedback, and HTMX swaps seamless and type-safe.

### 1. Form Component Definition

Define the form UI as an immutable `JssrComponent` Record:

```java
package com.example.demo.components;

import com.jssr.core.JssrComponent;

public record UserForm(String name, String email, String errorMessage) implements JssrComponent {

    @Override
    public String render() {
        return """
            <div id="form-container" class="max-w-md mx-auto p-6 bg-white rounded-xl shadow-md">
                <h2 class="text-xl font-bold mb-4">Create User Account</h2>
                
                <p class="error text-red-500 font-semibold">${errorMessage}</p>
                
                <form hx-post="/users" hx-target="#form-container" hx-swap="outerHTML" class="space-y-4">
                    <div>
                        <label class="block text-sm font-medium text-gray-700">Name</label>
                        <input type="text" name="name" value="${name}" required class="mt-1 block w-full rounded-md border-gray-300 shadow-sm" />
                    </div>
                    
                    <div>
                        <label class="block text-sm font-medium text-gray-700">Email</label>
                        <input type="email" name="email" value="${email}" required class="mt-1 block w-full rounded-md border-gray-300 shadow-sm" />
                    </div>
                    
                    <button type="submit" class="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700">
                        Create Account
                    </button>
                </form>
            </div>
            """;
    }
}
```

### 2. Spring MVC Form Controller Handler

Spring MVC handlers receive HTTP request params and return updated `JssrComponent` instances:

```java
package com.example.demo.controllers;

import com.jssr.core.JssrComponent;
import com.example.demo.components.UserForm;
import com.example.demo.components.UserCard;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@ResponseBody
@RequestMapping(produces = "text/html;charset=UTF-8")
public class UserFormController {

    // GET /users/new -> Renders blank initial form
    @GetMapping("/users/new")
    public JssrComponent renderNewUserForm() {
        return new UserForm("", "", "");
    }

    // POST /users -> Processes submission & handles validation feedback
    @PostMapping("/users")
    public JssrComponent processUserForm(
            @RequestParam("name") String name,
            @RequestParam("email") String email) {
        
        // Validation check
        if (email.contains("existing")) {
            // Re-render form with pre-filled inputs and error message
            return new UserForm(name, email, "An account with this email already exists.");
        }

        // Success -> Return created user card component (HTMX partial swap)
        return new UserCard(name, "User", true);
    }
}
```

### 3. Handling Checkboxes and Radio Buttons

Checkbox and Radio Button `checked` state attributes are handled cleanly by supplying calculated strings or Record component properties:

```java
public record PreferencesForm(
    BooleanAttribute subscribeChecked, 
    BooleanAttribute adminRoleChecked, 
    BooleanAttribute userRoleChecked
) implements JssrComponent {

    public static PreferencesForm of(boolean subscribe, String role) {
        return new PreferencesForm(
            BooleanAttribute.of("checked", subscribe),
            BooleanAttribute.of("checked", "ADMIN".equalsIgnoreCase(role)),
            BooleanAttribute.of("checked", "USER".equalsIgnoreCase(role))
        );
    }

    @Override
    public String render() {
        return """
            <form hx-post="/preferences">
                <!-- Checkbox -->
                <label>
                    <input type="checkbox" name="subscribe" value="true" ${subscribeChecked} />
                    Subscribe to Newsletter
                </label>
                
                <!-- Radio Group -->
                <label>
                    <input type="radio" name="role" value="ADMIN" ${adminRoleChecked} /> Admin
                </label>
                <label>
                    <input type="radio" name="role" value="USER" ${userRoleChecked} /> User
                </label>
            </form>
            """;
    }
}
```

### Key Form Protection Features in JSSR:
* **Attribute Quote Protection**: Pre-filled values inside `<input value="${name}">` containing quotes (e.g. `Sarah "The Boss" Connor`) are automatically converted to `&quot;`, keeping the input value safely enclosed inside the attribute quotes.
* **Stateless Unidirectional Flow**: Input state is managed via immutable Java Record components created by Spring controllers.
* **Microsecond HTMX Partial Swaps**: Form submission re-renders only the target HTML component fragment (`#form-container`) without full page reloads.

---

## Precompiled AST Engine & Failure Observability

JSSR includes a production-grade dynamic in-memory JVM precompiler that parses Record component HTML templates into an Abstract Syntax Tree (AST) (`TemplateNode`, `TemplateParser`) and compiles them into pure JVM bytecode classes (`.class` bytes) loaded in memory using JDK's standard `javax.tools.JavaCompiler`.

### 1. Failure Observability & Status Diagnostics

To prevent hidden operational fallbacks, JSSR provides explicit compilation status tracking and failure policies:

```java
// Check compilation status for a component (COMPILED, FALLBACK, NOT_COMPILED, FAILED)
CompilationStatus status = JssrPrecompiler.status(UserCard.class);

// Configure compilation failure policy
JssrPrecompiler.setFailureMode(CompilationFailureMode.WARN_AND_FALLBACK); // Default
// Modes: FAIL_FAST, WARN_AND_FALLBACK, SILENT_FALLBACK

// Batch precompile components and receive a diagnostic report
CompilationReport report = JssrPrecompiler.precompileAll(List.of(UserCard.class, UserForm.class));
System.out.printf("Compiled: %d, Fallback: %d, Failed: %d (Time: %d ms)%n",
        report.compiledCount(), report.fallbackCount(), report.failedCount(), report.elapsedTimeMs());
```

* **Zero-Dependency Logging**: Failure diagnostics are logged via Java's native `System.Logger` without requiring external logging dependencies.
* **Spring Boot Executable JAR Compatibility**: The dynamic compiler automatically handles `BOOT-INF/classes`, `BOOT-INF/lib`, `jar:file:`, and `nested:` URI classloader protocols when running inside packaged Spring Boot executable JARs (`java -jar application.jar`).

---

## 📊 JMH Performance Benchmarking Suite

JSSR includes a dedicated [JMH (Java Microbenchmark Harness)](https://github.com/openjdk/jmh) benchmarking suite to measure exact rendering throughput under controlled JVM environments (warm-up, JIT compilation, GC control).

### Running JMH Benchmarks

Execute the JMH benchmark suite via Gradle:

```bash
# Compile and run JMH benchmark suite
./gradlew jmh
```

Benchmarks under `src/jmh/java/com/jssr/benchmark/` measure:
- `SimpleComponentBenchmark`: Single component template rendering throughput.
- `ControlFlowBenchmark`: `@if`/`@else` conditional directive throughput.
- `LargeListBenchmark`: 100-row table iteration throughput.

---

## 🛠️ VS Code Extension & IDE Tooling

JSSR includes a native VS Code extension located in [`editors/vscode/`](file:///home/lem/Projects/java/JSSR/editors/vscode) providing automatic HTML/JSX syntax highlighting, template auto-formatting (`Shift+Alt+F`), JSSR control flow directive colorization, and code snippets (`jssr-comp`, `j-if`, `j-for`, `j-switch`, `j-try`) inside Java 17 multiline text blocks (`"""..."""`).

### Quick Local Installation
Copy the extension folder directly to your VS Code extensions directory:

```bash
# Linux / macOS
mkdir -p ~/.vscode/extensions/jssr-vscode-1.0.0
cp -r editors/vscode/* ~/.vscode/extensions/jssr-vscode-1.0.0/

# Windows (PowerShell)
New-Item -ItemType Directory -Path "$env:USERPROFILE\.vscode\extensions\jssr-vscode-1.0.0" -Force
Copy-Item -Path "editors\vscode\*" -Destination "$env:USERPROFILE\.vscode\extensions\jssr-vscode-1.0.0" -Recurse
```

Reload VS Code to enable instant HTML/JSX syntax highlighting and snippets for JSSR!

---

## Running Tests

Run unit, parser fuzz testing, packaging isolation, executable JAR verification, and Spring Boot E2E integration tests:

```bash
# Run full test suite
./gradlew test

# Run JMH microbenchmark throughput suite
./gradlew jmh

# Verify test suite under Spring Boot 4.0.0+
./gradlew test -PspringBootVersion=4.0.0
```

---

## License

This project is licensed under the [MIT License](file:///home/lem/Projects/java/JSSR/LICENSE).


# JSSR (Java Server-Side Rendering)

A high-performance, zero-dependency Java server-side rendering framework and UI library. JSSR brings a strongly typed, Record-based component model (`public record MyComponent(...) implements JssrComponent`) to Java 17+, serving as an immutable companion library for HTMX, Alpine.js, and Spring Boot MVC.

---

## Why Use JSSR?

Traditional Java web development forces developers into a hard choice between traditional template engines (Thymeleaf, JSP, FreeMarker) with untyped HTML templates, or heavy Single Page Application (SPA) frameworks (React, Next.js, Vue) requiring complex Node.js build pipelines and duplicate DTO definitions.

JSSR combines the best of both worlds by bringing React-like component architecture to modern Java 17+:

- **Strongly Typed Components with Runtime-Validated Template Props**: Every UI component is an immutable Java Record. Direct Java component instantiations are 100% compiler verified, and JSX-style template props are validated at runtime to reject typos and missing attributes.
- **Native Java 17 Multiline Text Blocks with ${fieldName} Interpolation**: HTML templates use native Java text blocks (`"""..."""`). Placeholders like `${name}` or `${role}` are automatically interpolated from Record fields with automatic default HTML escaping (`&`, `<`, `>`, `"`, `'`).
- **Safe URL & Raw HTML Protection**: Use `SafeUrl` wrappers to automatically sanitize dangerous URL schemes (`javascript:`, `vbscript:`, `data:`), and `RawHtml` to explicitly bypass HTML escaping for trusted content.
- **Declarative JSX-Style & Paired Component Trees**: Compose self-closing (`<UserCard name="Sarah" />`) or paired (`<Card><h1>Header</h1></Card>`) component trees in Java. JSSR resolves custom tags, passes inner content as `children` props, and renders nested component trees recursively with depth recursion protection.
- **Micro-Granular SSR for HTMX & Alpine.js**: Every component is an independent executable unit. Spring MVC controllers can return single component instances (`return new UserRow(user);`) for microsecond HTMX swaps.
- **Zero-Dependency Core Engine**: The core JSSR rendering engine (`com.jssr.core.JssrComponent`) is written in 100% pure standard Java with zero mandatory third-party dependencies.

### Quick Comparison

| Feature | Thymeleaf / JSP | React / Next.js + Java | JSSR |
| :--- | :--- | :--- | :--- |
| **Component Architecture** | Weak / Fragment includes | Excellent | **React-like Java Records** |
| **Type Safety** | None (Untyped Strings) | TypeScript (Duplicate DTOs) | **Strongly Typed Records + Prop Validation** |
| **Build Toolchain** | Maven / Gradle | Node.js + npm + Webpack + Maven | **Gradle / Maven Only** |
| **HTMX / Fragment SSR** | Clunky fragments | Not Supported (JSON APIs) | **Native Component Swapping** |
| **Performance** | Template Parsing Overhead | Client-side JS Bundle Overhead | **JVM Microsecond Rendering** |

---

## How Variable Interpolation & Security Work

> [!IMPORTANT]
> **Core Security Principle**: *Strings are data, JSX is markup, and trusted HTML must be explicit.*
> Application developers are never required to manually escape values. XSS protection is built directly into JSSR's core interpolation and rendering pipeline by default.

In JSSR component Records, any field (e.g. `name`, `role`, `active`) can be referenced directly using `${fieldName}` syntax inside multiline text blocks:

```java
public record UserCard(String name, String role, boolean active) implements JssrComponent {

    @Override
    public String template() {
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
11. **Unsafe Context Rejection**: `${...}` interpolation inside `<script>`, `<style>`, or `<!-- comment -->` blocks is strictly forbidden and throws a clear `IllegalArgumentException` at render time.
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
    public String template() {
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
       public String template() {
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
    implementation 'com.github.lemadane:JSSR:v0.1.0'
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
        <version>v0.1.0</version>
    </dependency>
</dependencies>
```

### Publishing Releases on GitHub

To publish a official version tag (e.g. `v0.1.0`) on GitHub:

```bash
git tag -a v0.1.0 -m "Release v0.1.0"
git push origin v0.1.0
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
    public String template() {
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
    public String template() {
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

## Library Architecture

`src/main/java` contains the pure, minimal JSSR framework:

```
JSSR/
├── .github/workflows/ci.yml              # GitHub Actions CI workflow
├── build.gradle                          # Groovy-Gradle configuration
├── jitpack.yml                           # JitPack build configuration
├── LICENSE                               # MIT License
├── README.md                             # Documentation
└── src/
    ├── main/
    │   └── java/com/jssr/                # PURE REUSABLE JSSR UI LIBRARY
    │       ├── core/
    │       │   ├── JssrComponent.java    # Interface for Record-based components & tag engine
    │       │   ├── RawHtml.java          # Wrapper for trusted unescaped HTML
    │       │   └── SafeUrl.java          # Wrapper for URL protocol sanitization
    │       └── spring/
    │           ├── JssrConverter.java    # Converts JssrComponent to HTML HTTP responses
    │           └── JssrMvcConfig.java    # Configures Spring MVC converter
    └── test/
        └── java/com/jssr/                # UNIT & E2E INTEGRATION TEST SUITE
```

---

## Running Tests

Run unit and E2E integration tests:

```bash
gradle test
```

---

## License

This project is licensed under the [MIT License](file:///home/lem/Projects/java/JSSR/LICENSE).

# JSSR (Java Server-Side Rendering)

A high-performance, zero-dependency Java server-side rendering framework and UI library. JSSR brings a strongly typed, Record-based component model (`public record MyComponent(...) implements JssrComponent`) to Java 17+, serving as an immutable companion library for HTMX, Alpine.js, and Spring Boot MVC.

---

## Why Use JSSR?

Traditional Java web development forces developers into a hard choice between traditional template engines (Thymeleaf, JSP, FreeMarker) with untyped HTML templates, or heavy Single Page Application (SPA) frameworks (React, Next.js, Vue) requiring complex Node.js build pipelines and duplicate DTO definitions.

JSSR combines the best of both worlds by bringing React-like component architecture to modern Java 17+:

- **100% Compile-Time Type Safety**: Every UI component is an immutable Java Record. Component props are validated by the Java compiler, providing full IDE autocomplete and safe refactoring across your codebase.
- **Context-Aware Default HTML Escaping**: `${fieldName}` interpolations are HTML-escaped by default (`&`, `<`, `>`, `"`, `'`) to automatically protect your application from Cross-Site Scripting (XSS) vulnerabilities.
- **Native Java 17 Multiline Text Blocks with ${fieldName} Interpolation**: HTML templates use native Java text blocks (`"""..."""`). Placeholders like `${name}` or `${role}` are automatically interpolated from Record fields, or formatted with standard `.formatted(...)`.
- **Declarative JSX-Style Component Trees**: Compose nested component trees in Java (`<UserCard name="Sarah" role="Admin" />`). JSSR automatically resolves custom tags, handles attribute URLs (`href="/users/42"`, `hx-get="/tasks/42"`), converts attributes to record scalar types (`double`, `float`, `int`, `long`, `boolean`, `Enum`), and renders nested component trees recursively.
- **Micro-Granular SSR for HTMX & Alpine.js**: Every component is an independent executable unit. Spring MVC controllers can return single component instances (`return new UserRow(user);`) for microsecond HTMX swaps.
- **Zero Build Toolchain Overhead**: No `node_modules`, no npm, no Webpack, no Vite, and no JavaScript build steps. Pure Java 17+ packaged into a standard JAR.
- **Zero Third-Party Core Dependencies**: The core JSSR engine (`com.jssr.core.JssrComponent`) is written in 100% pure standard Java.

### Quick Comparison

| Feature | Thymeleaf / JSP | React / Next.js + Java | JSSR |
| :--- | :--- | :--- | :--- |
| **Component Architecture** | Weak / Fragment includes | Excellent | **React-like Java Records** |
| **Type Safety** | None (Untyped Strings) | TypeScript (Duplicate DTOs) | **100% Java Compiler Safe** |
| **XSS Protection** | Manual / Escape functions | Built-in JSX escaping | **Automatic Context-Aware Escaping** |
| **Build Toolchain** | Maven / Gradle | Node.js + npm + Webpack + Maven | **Gradle / Maven Only** |
| **HTMX / Fragment SSR** | Clunky fragments | Not Supported (JSON APIs) | **Native Component Swapping** |
| **Performance** | Template Parsing Overhead | Client-side JS Bundle Overhead | **JVM Microsecond Rendering** |

---

## How Variable Interpolation & HTML Escaping Work

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

JSSR's `render()` method automatically interpolates all `${fieldName}` placeholders using reflection on the Record's field values.

### XSS Prevention & Trusted Raw HTML (`RawHtml`)

All string and primitive values interpolated via `${fieldName}` are **HTML-escaped by default**. For example:

```java
UserCard card = new UserCard("<script>alert(1)</script>", "Admin", true);
```

renders safely as:

```html
<h3 class="font-bold text-lg">&lt;script&gt;alert(1)&lt;/script&gt;</h3>
```

When you need to intentionally render trusted, unescaped HTML content (such as rich text editor output), wrap the value in `RawHtml` or pass a nested `JssrComponent`:

```java
import com.jssr.core.JssrComponent;
import com.jssr.core.RawHtml;

public record ArticlePage(String title, RawHtml content) implements JssrComponent {

    @Override
    public String template() {
        return """
            <article>
                <h1>${title}</h1>
                <div class="prose">${content}</div>
            </article>
            """;
    }
}

// Usage:
ArticlePage article = new ArticlePage("Safety Guide", RawHtml.of("<p>Trusted <b>HTML</b> content</p>"));
```

---

## Data Flow and Component Immutability

Because JSSR components are Java Records, they are **immutable and stateless**. Components are never mutated in-place on the server. Data flows through a 3-step unidirectional cycle:

1. **Form Rendering (Server to Browser)**: The controller instantiates an immutable record `new UserForm("Sarah Connor", ...)` which renders an HTML form containing pre-filled input values (`<input name="name" value="Sarah Connor" />`).
2. **User Interaction (Browser)**: The user modifies the input field in their browser (e.g. typing `"Sarah Connor Revised"`) and submits the form via HTMX or standard HTTP POST.
3. **Controller Response (Server)**: The Spring MVC controller handler receives the HTTP request parameters (`@RequestParam("name") String name`), updates database entities, and instantiates a **new Record component instance** (`new UserList(...)` or `new UserRow(...)`) with the updated data.

Creating Java Records on the JVM takes nanoseconds, providing microsecond server-side rendering speeds with zero state mutation bugs.

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
    implementation 'com.github.lemadane:JSSR:v1.0.0'
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
        <version>v1.0.0</version>
    </dependency>
</dependencies>
```

---

## How to Use JSSR in a Spring Boot MVC Application

### Step 1: Enable JSSR Spring MVC Auto-Configuration

Import `JssrMvcConfig` into your main Spring Boot application class to automatically register `JssrConverter` into Spring MVC's converter chain via `extendMessageConverters()` (preserving all default Spring MVC converters like Jackson JSON):

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

Child component tags can be statically registered and composed directly inside parent component text blocks (supporting URLs in attributes such as `href="/users/42"` or `hx-get="/tasks/42"`):

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
├── build.gradle                          # Groovy-Gradle configuration
├── jitpack.yml                           # JitPack build configuration
├── LICENSE                               # MIT License
├── README.md                             # Documentation
└── src/
    ├── main/
    │   └── java/com/jssr/                # PURE REUSABLE JSSR UI LIBRARY
    │       ├── core/
    │       │   ├── JssrComponent.java    # Interface for Record-based components
    │       │   └── RawHtml.java          # Wrapper for trusted raw HTML content
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

This project is licensed under the [MIT License](LICENSE).

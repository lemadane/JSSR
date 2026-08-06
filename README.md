# JSSR (Java Server-Side Rendering)

A high-performance, zero-dependency Java server-side rendering framework and UI library. JSSR brings a strongly typed, Record-based component model (`public record MyComponent(...) implements JssrComponent`) to Java 17+, serving as an immutable companion library for HTMX, Alpine.js, and Spring Boot MVC.

---

## Why Use JSSR?

Traditional Java web development forces developers into a hard choice between traditional template engines (Thymeleaf, JSP, FreeMarker) with untyped HTML templates, or heavy Single Page Application (SPA) frameworks (React, Next.js, Vue) requiring complex Node.js build pipelines and duplicate DTO definitions.

JSSR combines the best of both worlds by bringing React-like component architecture to modern Java 17+:

- **100% Compile-Time Type Safety**: Every UI component is an immutable Java Record. Component props are validated by the Java compiler, providing full IDE autocomplete and safe refactoring across your codebase.
- **Native Java 17 Multiline Text Blocks with ${fieldName} Interpolation**: HTML templates use native Java text blocks (`"""..."""`). Placeholders like `${name}` or `${role}` are automatically interpolated from Record fields, or formatted with standard `.formatted(...)`.
- **Declarative JSX-Style Component Trees**: Compose nested component trees in Java (`<UserCard name="Sarah" role="Admin" />`). JSSR automatically resolves custom tags, converts attributes to record parameter types, and renders nested component trees recursively.
- **Micro-Granular SSR for HTMX & Alpine.js**: Every component is an independent executable unit. Spring MVC controllers can return single component instances (`return new UserRow(user);`) for microsecond HTMX swaps.
- **Zero Build Toolchain Overhead**: No `node_modules`, no npm, no Webpack, no Vite, and no JavaScript build steps. Pure Java 17+ packaged into a standard JAR.
- **Zero Third-Party Core Dependencies**: The core JSSR engine (`com.jssr.core.JssrComponent`) is written in 100% pure standard Java.

---

## How Variable Interpolation Works

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

JSSR's `render()` method automatically interpolates all `${fieldName}` placeholders using reflection on the Record's field values before processing custom tags.

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

Child component tags can be statically registered and composed directly inside parent component text blocks:

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
    │       │   └── JssrComponent.java    # Interface for Record-based components
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

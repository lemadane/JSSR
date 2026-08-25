# Todo App Demo (JSSR Subproject)

A tiny Spring Boot MVC todo demo rendered with JSSR components.

## Run

From the repository root:

```bash
./gradlew :todo-app:bootRun
```

Open: http://localhost:8080/todos

## What It Demonstrates

- JSSR component rendering in Spring MVC responses
- Record-based UI components (`TodoPage`, `TodoRow`)
- In-memory todo state with add + toggle actions
- Server-rendered HTML forms without front-end build tooling

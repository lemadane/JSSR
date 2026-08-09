# JSSR VS Code Extension (`jssr-vscode`)

Native VS Code extension for **JSSR (Java Server-Side Rendering)**. Provides automatic HTML/JSX syntax highlighting, JSSR control flow directive colorization, and code snippets inside Java 17 multiline text blocks (`"""..."""`).

---

## Features

- **Automatic Text Block Highlighting**: Automatically highlights HTML tags, CSS classes, and attributes inside Java 17 `"""..."""` text blocks.
- **Directive Colorization**: Highlights JSSR directives (`@if`, `@elseif`, `@else`, `@for`, `@switch`, `@case`, `@default`, `@try`, `@catch`, `@finally`, `@throw`, `@continue`, `@break`, `@end`).
- **Variable Placeholder Highlighting**: Colorizes `${variableName}` expressions inside templates.
- **Handy Code Snippets**:
  - `jssr-comp`: Create a new `JssrComponent` Record.
  - `j-if`: `@if (condition) ... @end`
  - `j-ifelse`: `@if (condition) ... @else ... @end`
  - `j-for`: `@for (item : collection) ... @else ... @end`
  - `j-switch`: `@switch (expr) ... @case (val) ... @end`
  - `j-try`: `@try ... @catch(err) ... @end`

---

## Installation Options

### Option 1: Direct Local Installation (Quickest)

Copy the `editors/vscode` directory to your local VS Code extensions folder:

```bash
# Linux / macOS
mkdir -p ~/.vscode/extensions/jssr-vscode-1.0.0
cp -r editors/vscode/* ~/.vscode/extensions/jssr-vscode-1.0.0/

# Windows PowerShell
New-Item -ItemType Directory -Path "$env:USERPROFILE\.vscode\extensions\jssr-vscode-1.0.0" -Force
Copy-Item -Path "editors\vscode\*" -Destination "$env:USERPROFILE\.vscode\extensions\jssr-vscode-1.0.0" -Recurse
```

Restart VS Code or run **Developer: Reload Window**.

### Option 2: Package `.vsix` file using `@vscode/vsce`

To build a standalone `.vsix` installer package:

```bash
cd editors/vscode
npx @vscode/vsce package
code --install-extension jssr-vscode-1.0.0.vsix
```

---

## License

[MIT](LICENSE)

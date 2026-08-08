# Security Policy & Architecture Guidelines

## Core Security Design Philosophy

> **Strings are data, components are markup, and raw HTML requires an explicit type.**

JSSR is designed for internet-facing, security-critical Java applications. It enforces context-aware XSS prevention rules at runtime.

---

## Supported Versions

| Version | Supported          | Security Maintenance |
| ------- | ------------------ | -------------------- |
| 1.1.x   | :white_check_mark: | Active Release       |
| 1.0.x   | :white_check_mark: | Production Release   |
| < 1.0.0 | :x:                | Unsupported          |

---

## Security Guarantees & Enforcement Rules

### 1. Default HTML Escaping
- All standard `String` and primitive record properties interpolated into templates are automatically escaped:
  - `&` &rarr; `&amp;`
  - `<` &rarr; `&lt;`
  - `>` &rarr; `&gt;`
  - `"` &rarr; `&quot;`
  - `'` &rarr; `&#39;`

### 2. Trusted Escape Hatches (`RawHtml` & `JssrComponent`)
- `RawHtml` explicitly marks trusted pre-formatted HTML that should not be re-escaped.
- Nested `JssrComponent` records render their structured templates directly without double-escaping.

### 3. Comprehensive URL Attribute Protection (`SafeUrl`)
- Dynamic interpolation in all URL-bearing HTML attributes (`href`, `src`, `action`, `formaction`, `poster`, `data`, `srcset`, `imagesrcset`, `codebase`, `icon`, `manifest`, `profile`, `cite`, `longdesc`, `usemap`, `xlink:href`) strictly requires `SafeUrl` typed fields; raw `String` properties throw an `IllegalArgumentException`.
- `SafeUrl` validates schemes against an allowlist (`http:`, `https:`, `mailto:`, `tel:`, relative paths, `#`, `?`). Dangerous schemes (e.g. `javascript:`, `vbscript:`, `data:`) are sanitized to `about:blank`.

### 4. Free-Standing Attribute Protection (`BooleanAttribute` & `HtmlAttribute`)
- Plain `String` interpolation between HTML tag attributes is forbidden to prevent attribute injection.
- Dynamic attributes must be typed using `boolean` fields, `BooleanAttribute`, or `HtmlAttribute`.

### 5. Quoted Attribute Value Enforcement
- Dynamic attribute interpolation MUST be enclosed in quotes (e.g., `title="${title}"`). Unquoted interpolation (e.g. `title=${title}`) throws an exception.

### 6. Executable Framework & Event Attribute Isolation
- Variable interpolation inside inline event handlers (`onclick`, `onmouseover`, etc.) and `style=` attributes is strictly prohibited.
- Variable interpolation inside Alpine.js (`x-*`, `@*`, `:`) and HTMX (`hx-on:*`) attributes is strictly prohibited.
- Variable interpolation inside `srcdoc` attributes is strictly prohibited due to nested HTML decoding risks.

### 7. Context Isolation (`<script>`, `<style>`, HTML Comments)
- Variable interpolation inside `<script>` blocks, `<style>` blocks, and `<!-- HTML comments -->` is rejected to prevent context escape.

---

## Vulnerability Reporting Guidelines

If you discover a security vulnerability in JSSR, please do **NOT** open a public issue.

### Private Disclosure Process

1. **Submit via GitHub Private Vulnerability Reporting**:
   Navigate to the [Security Tab](../../security/advisories/new) of the repository and click **Report a vulnerability**.
2. **Alternative Email Contact**:
   If private reporting is unavailable, send details to `security@jssr.dev` with encrypted payload (if available).

### Response SLA & Timeline

- **Initial Response**: Within **48 hours** acknowledging receipt of the report.
- **Triage & Assessment**: Within **5 business days** confirming vulnerability status and impact.
- **Fix & Patch Timeline**: Critical/High severity issues patched within **14 calendar days**.
- **Coordinated Public Disclosure**: Security Advisory published via GitHub Security Advisories and Maven Central patch release upon fix release.

### Reporter Credit
We credit researchers in our security advisories and changelog for responsibly disclosed vulnerabilities.

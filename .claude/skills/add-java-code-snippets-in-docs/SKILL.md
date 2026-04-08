---
name: add-java-code-snippets-in-docs
description: Use when the user asks to add Java tabs/examples to mkdocs docs that already have Kotlin examples (files under docs/docs/**.md using === "Kotlin" / Knit INCLUDE+SUFFIX blocks). Handles mkdocs-material tabs, Knit directives, @JavaAPI bridge methods, and JVM factory classes.
allowed-tools: Read, Edit, Write, Bash, Grep, Glob
---

## When to use

Adding Java code blocks alongside existing Kotlin blocks in mkdocs documentation (`docs/docs/**.md`). Typical triggers: "add Java examples to <file>", "add Java tabs", "complete the Java side of <doc>", "fix the Java block in <doc>".

**Do NOT use this skill for:**

- Writing brand-new Kotlin docs (no precursor Kotlin block to mirror).
- Splitting source files between JVM and non-JVM source sets — use the **`split-jvm-nonjvm`** skill instead.
- Java examples outside the mkdocs/Knit pipeline.

## Prerequisites

JDK 17. Gradle 8.x fails cryptically on newer JDKs. If the default JDK is newer, prefix every `./gradlew` command in this workflow with `JAVA_HOME=/path/to/jdk-17`.

## Before starting

Read **one** reference doc to anchor on the established pattern:

- `docs/docs/features/tracing.md` — full Tracing feature (blocks 1–6).
- `docs/docs/agent-events.md` — custom `FeatureMessageProcessor` subclass (suspend bridge methods `handleMessage` / `handleClose`).
- `docs/docs/testing.md` — `MockPromptExecutor` builder, tool mocking, comment-only blocks for Kotlin-only DSL.
- `docs/docs/features/open-telemetry/index.md` — OpenTelemetry examples.
- `docs/docs/features/open-telemetry/opentelemetry-langfuse-exporter.md` — Langfuse exporter.
- `docs/docs/features/open-telemetry/opentelemetry-weave-exporter.md` — Weave exporter.

## Workflow

For each Kotlin code block in the target `.md`:

1. **Read** the Kotlin block: INCLUDE, visible code, SUFFIX, KNIT directive.
2. **Check existing Java tab.** If absent → add one. If it has empty `/** */` placeholder content → replace entirely. If it has real content → update.
3. **Consider Kotlin-side alignment.** If the Kotlin block uses Kotlin-only APIs (`kotlinx.io`, `MutableStateFlow`, `KLogger`, inline `Duration`), consider whether the Kotlin example should switch to a JVM `create()` factory (e.g., `TraceFeatureMessageFileWriter.create(path)`). This keeps the two examples structurally aligned. See `reference/factory-classes.md`.
4. **Add `@JavaAPI` bridge methods if missing.** If the Java code needs a bridge that doesn't exist yet (e.g., a builder method on `MockExecutorBuilder`), add it before writing the Java tab. This may pause the doc work for a source-code change — flag it to the user before proceeding.
5. **Write the Java tab** immediately after the Kotlin tab, using the template below.
6. **If renaming Knit files** (e.g., hyphenated `example-feature-java-01.java` → camelCase `exampleFeatureJava01.java`), delete the old generated files first.
7. **Generate and compile** (see Verification at the bottom).
8. **Read the generated `.java`** to confirm correctness. Fix and repeat 7–8 on failure.

## Mkdocs / Knit tab structure

Java tabs go immediately after the Kotlin tab. All content inside a tab is indented 4 spaces. `<!--- INCLUDE -->`, `<!--- SUFFIX -->`, `<!--- KNIT -->` are Knit directives (HTML comments — invisible when rendered), not mkdocs syntax.

```markdown
=== "Kotlin"

    <!--- INCLUDE
    import ...
    -->
    ```kotlin
    // visible code
    ```
    <!--- KNIT example-feature-01.kt -->

=== "Java"

    <!--- INCLUDE
    import ...;
    public class exampleFeatureJava01 {
        public static void main(String[] args) {
    -->
    <!--- SUFFIX
        }
    }
    -->
    ```java
    // visible code
    ```
    <!--- KNIT exampleFeatureJava01.java -->
```

### KNIT naming — capital "Java" matters

`docs/knit.code.include` has the check `<#if !knit.name?contains("Java")>package ...`. Files with capital "Java" in the name skip the `package` declaration; lowercase "java" or hyphenated names emit a `package` line that breaks compilation.

- ✅ `exampleFeatureJava01.java`, `exampleTestingJava13.java`
- ❌ `example-feature-java-01.java`, `examplefeaturejava01.java`

### Knit rules

- INCLUDE + visible code + SUFFIX must concatenate into a valid Java file.
- Hidden variables in Kotlin INCLUDE (`outputPath`, `input`, etc.) must also appear in Java INCLUDE.
- Visible-code indentation in `.md` is 4 spaces (tab content). Knit strips that when generating.
- Always import in INCLUDE; never use FQNs in visible code.
- Never use file-level annotations (`@file:Suppress`, `@file:OptIn`) — scope to specific declarations.

## Structural matching (the most important rule)

**The Java visible code must match the Kotlin visible code in scope and structure.**

- If Kotlin shows no `main()`, Java must hide `main()` in INCLUDE/SUFFIX.
- If Kotlin shows `install(Feature) { ... }`, Java must show `.install(Feature.Feature, config -> { })`.
- If Kotlin creates a writer before the agent, Java must too.
- If Kotlin shows `agent.run(input)`, Java must too.

Read both visible blocks side-by-side after writing. They should read as equivalent code in their respective languages.

### When Kotlin uses APIs with no Java bridge

Some Kotlin DSLs (graph testing: `testGraph`, `assertSubgraphByName`, `assertNodes`, `assertEdges`, `verifySubgraph`, `assertReachable`) have no `@JavaAPI` bridges. To check: grep for `@JavaAPI` in the source file — if absent, treat as Kotlin-only.

When the Kotlin block uses an entirely Kotlin-only API:

1. **Do NOT leave the Java tab empty.** Write a compilable Java class with explanatory comments.
2. The class must have a valid `main` so it compiles.
3. Comments explain what the Kotlin DSL does and the recommended Java alternative.

```java
// Graph structure testing (testGraph, assertSubgraphByName, assertEdges,
// verifySubgraph, assertReachable) is available through the Kotlin testing DSL.
//
// In Java, test agent behavior by running and asserting on the result:
//   String result = agent.run("test input");
//   assert "expected".equals(result);
```

## Deep references

Load these only when you need them:

- **`reference/interop-tables.md`** — Kotlin↔Java mapping table, suspend bridges (`@JvmName("run")`, `handleMessage`, `handleClose`), abstract-property rules, key source files to grep for `@JavaAPI`.
- **`reference/factory-classes.md`** — JVM `create()` factory pattern, two-overload skeleton, type-conversion cheat sheet, when needed vs not, anti-patterns. Cross-references the `split-jvm-nonjvm` skill.
- **`reference/pitfalls.md`** — `var`-vs-field, brace-counting in SUFFIX, `is`-prefixed property getters, `@JvmOverloads` requirement, docs classpath limits (no JUnit), legacy `/** */` placeholder, `MockExecutorBuilder` API.

## Verification

After each Java block:

```bash
./gradlew :docs:knit
./gradlew :docs:compileJava
```

On failure, read the generated file to diagnose:

```bash
cat docs/src/main/kotlin/exampleFeatureJavaNN.java
```

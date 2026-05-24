# JVM `create()` factory classes

Load this file when the Kotlin block uses types that aren't callable from Java and you need to expose (or audit) a Java-friendly factory.

## When a factory is needed

Kotlin constructors / APIs Java can't call cleanly:

- Uses `kotlinx.io` types (`Sink`, `Path`, `SystemFileSystem`).
- Uses inline value classes (`Duration`) — the constructor becomes private from Java.
- Uses `KLogger` (kotlin-logging) instead of SLF4J `Logger`.
- Has default parameters without `@JvmOverloads` — generates synthetic constructors with `DefaultConstructorMarker`.

If the Kotlin constructor uses only Java-compatible types and has `@JvmOverloads` (or no default params), Java can call it directly with `new`. Example:

```java
// No factory needed — constructor is Java-accessible
new TraceFeatureMessageRemoteWriter()
new TraceFeatureMessageRemoteWriter(connectionConfig)
```

## Pattern

Add a `companion object` with `create(...)` methods **on the class itself**, in the `jvmCommonMain` source set. The class must already live in `jvmCommonMain` (or have a JVM actual) for this to apply.

> **Prerequisite:** if the class is still in `commonMain`, run the **`split-jvm-nonjvm`** skill first to produce the `expect class` + `jvmCommonMain` actual scaffolding. `split-jvm-nonjvm` requires the class to have no companion object at split time — so the order is: (1) split, then (2) add the companion `create()` on the JVM actual. If the class already has a companion in `commonMain`, raise it with the user before splitting.

Provide two overloads of `create()`:

1. **Kotlin-facing** — accepts the original Kotlin-only types (`kotlinx.io.Path`, `Sink`, `KLogger`, `Duration`). No JVM annotations needed; convenient sugar for Kotlin callers and keeps API symmetry.
2. **Java-facing** — accepts only Java-compatible types (`java.nio.file.Path`, `java.io.OutputStream`, `org.slf4j.Logger`, primitives, etc.). Annotate with `@JavaAPI` (intent), `@JvmStatic` (static call site), and `@JvmOverloads` (one method per default-parameter combination). Convert to Kotlin-only types inside the body, then delegate to the primary constructor.

## Skeleton

Substitute `Xxx` with the class name, `KotlinT` / `JavaT` with the actual type pair:

```kotlin
// In: jvmCommonMain/.../Xxx.kt
public class Xxx(/* primary constructor uses Kotlin-only types */) {
    public companion object {
        // (1) Kotlin-facing
        public fun create(
            param: KotlinT,
            // ...defaulted params with Kotlin types
        ): Xxx = Xxx(/* pass through */)

        // (2) Java-facing
        @JavaAPI
        @JvmStatic
        @JvmOverloads
        public fun create(
            param: JavaT,
            // ...defaulted params with Java-friendly types
        ): Xxx {
            val kotlinParam = /* convert JavaT -> KotlinT */
            return Xxx(/* pass converted values */)
        }
    }
}
```

Java callers then write `Xxx.create(javaParam)` — no separate class, no `Continuation`, no `DefaultConstructorMarker`.

## Conversion cheat sheet

| Kotlin-only type | Java-facing replacement | Conversion inside `create()` |
|------------------|-------------------------|------------------------------|
| `kotlinx.io.files.Path` | `java.nio.file.Path` | `Path.of(javaPath.toString())` or use `SystemFileSystem` |
| `kotlinx.io.Sink` | `java.io.OutputStream` (via opener lambda) | `outputStream.asSink().buffered()` |
| `KLogger` (kotlin-logging) | `org.slf4j.Logger` | `KotlinLogging.logger(slf4jLogger)` |
| `kotlin.time.Duration` | `long millis` / `java.time.Duration` | `millis.milliseconds` / `Duration.ofMillis(...).toKotlinDuration()` |
| `(T) -> R` with Kotlin types | `Function<T, R>` or SAM with Java types | `{ t -> javaFn.apply(t) }` |

## Reference implementations

- `agents/agents-features/agents-features-trace/src/jvmCommonMain/.../TraceFeatureMessageFileWriter.kt` — `Path` / `Sink` ↔ `java.nio.file.Path` / `OutputStream`.
- `agents/agents-features/agents-features-trace/src/jvmCommonMain/.../TraceFeatureMessageLogWriter.kt` — `KLogger` ↔ SLF4J `Logger`.

## Anti-pattern: separate `*Jvm` object

Do **not** introduce a separate `public object XxxJvm { ... }` alongside the original class. The convention is to put the Java-facing `create()` on the original class's companion. A `*Jvm` companion class would be redundant and inconsistent with the existing `TraceFeatureMessage*Writer` classes.

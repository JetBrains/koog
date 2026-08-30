# Module prompt-pii-model

Core interfaces and implementation for transparent PII anonymization around `PromptExecutor`.

### Overview

The `prompt-pii-model` module provides:
- `PiiType` and `PiiDetection` for typed PII spans
- `PiiDetector` and `RegexPiiDetector` for detection
- `PiiPromptExecutor` as a transparent wrapper over a nested `PromptExecutor`
- `PiiTagFixingParser` for optional non-streaming unknown-tag repair
- strict unknown-tag validation with `UnknownPiiTagsException`

`RegexPiiDetector.default()` includes deterministic regex+checksum coverage for core entities
and country-specific identifiers (US, UK, ES, IT, PL, SG, AU, IN, FI, KR, TH) such as
IBAN, crypto wallets, MAC addresses, tax identifiers, and national IDs.
It intentionally excludes heuristic-only entities like `PERSON`, `NRP`, and `MEDICAL_LICENSE`.

### Using in your project

To use the prompt pii model in your project, add the following dependency to your `build.gradle.kts` file:

```kotlin
dependencies {
    implementation("ai.koog.prompt:prompt-pii-model:$version")
}
```

You can then wrap your existing executor:

```kotlin
val detector = RegexPiiDetector.default()
val piiExecutor = PiiPromptExecutor(detector = detector, nested = promptExecutor)
```

### Using in tests

The regex detector is useful for tests:

```kotlin
class MyTest {
    private lateinit var detector: PiiDetector

    @BeforeTest
    fun setup() {
        detector = RegexPiiDetector.default()
    }
}
```

### Example of usage

```kotlin
fun main() = runBlocking {
    val detector = RegexPiiDetector.default()
    val piiExecutor = PiiPromptExecutor(detector = detector, nested = promptExecutor)

    val prompt = Prompt(
        messages = listOf(Message.User("Contact John Doe at john@example.com", RequestMetaInfo.Empty)),
        id = "pii-example"
    )

    val response = piiExecutor.execute(prompt, model)
    println(response.first().content)
}
```

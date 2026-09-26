# Module prompt:prompt-pii

The prompt-pii module provides transparent PII anonymization around prompt execution.

### Overview

This module currently contains:
- **prompt-pii-model**: PII data types, detectors, unknown-tag fixer, and `PiiPromptExecutor`
- **prompt-pii-detector-structured**: LLM structured detector returning substring+type and resolving spans locally

The module anonymizes detected PII values into stable tags (for example `[[person 1]]`), sends anonymized content to the LLM, validates tag correctness in the response, and deanonymizes known tags back to real values.

`RegexPiiDetector.default()` is a deterministic profile (regex + checksum validators) with
high-confidence Presidio-aligned identifier coverage. It does not include heuristic name/entity
matching for types such as `PERSON`, `NRP`, or `MEDICAL_LICENSE`.

### Using in your project

Add the following dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("ai.koog.prompt:prompt-pii-model:$version")
    implementation("ai.koog.prompt:prompt-pii-detector-structured:$version")
}
```

### Using in tests

For tests and local development, use the regex-based detector:

```kotlin
val detector = RegexPiiDetector.default()
```

For LLM-assisted typed extraction with local span resolution:

```kotlin
val detector = StructuredPiiDetector(
    executor = promptExecutor,
    model = model
)
```

### Example of usage

```kotlin
val detector = RegexPiiDetector.default()

val piiExecutor = PiiPromptExecutor(
    detector = detector,
    nested = promptExecutor,
)

val prompt = Prompt(
    messages = listOf(Message.User("Email me at john@example.com", RequestMetaInfo.Empty)),
    id = "example"
)

val response = piiExecutor.execute(prompt, model)
println(response.first().content)
```

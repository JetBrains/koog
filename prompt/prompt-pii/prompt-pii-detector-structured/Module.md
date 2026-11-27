# Module prompt-pii-detector-structured

LLM-backed structured PII detector for `prompt-pii`.

### Overview

This module provides:
- `StructuredPiiDetector` implementation of `PiiDetector`
- `StructuredPiiDetectorConfig` for prompt id, fixing retries, and prompt template customization

The detector asks the LLM for typed entries with:
- `substring`
- `type` (`PiiType` enum)

It then resolves `start` and `endExclusive` locally by exact case-sensitive substring matching against the original text.
Each returned substring expands to all matching occurrences.

### Using in your project

```kotlin
dependencies {
    implementation("ai.koog.prompt:prompt-pii-detector-structured:$version")
}
```

### Example of usage

```kotlin
val detector = StructuredPiiDetector(
    executor = promptExecutor,
    model = model,
    config = StructuredPiiDetectorConfig(
        promptId = "pii-structured-detection",
        fixingRetries = 1
    )
)

val detections = detector.detect("Contact John Doe at john@example.com")
```

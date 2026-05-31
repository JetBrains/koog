# Module prompt-executor-foundationmodels-client

On-device LLM client for Apple platforms backed by the Foundation Models framework
(iOS 26+). Provides `FoundationModelsLLMClient`, a turnkey `LLMClient` that runs
single-shot inference locally via `SystemLanguageModel`, with graceful availability
gating. iOS-only; requires Apple Intelligence and an eligible device.

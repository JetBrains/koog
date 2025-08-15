package ai.koog.agents.features.tracing.mock

import ai.koog.prompt.llm.LLMProvider

class MockLLMProvider(
    id: String = "test-llm-provider",
    display: String = "test-llm-display"
) : LLMProvider(id, display)

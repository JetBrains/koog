package ai.koog.agents.features.tracing

import ai.koog.prompt.llm.LLMProvider

class TestLLMProvider(
    id: String = "test-llm-provider",
    display: String = "test-llm-display"
) : LLMProvider(id, display)

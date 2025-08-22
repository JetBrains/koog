package ai.koog.agents.features.debugger.mock

import ai.koog.prompt.llm.LLMProvider

class MockLLMProvider(
    id: String = "test-llm-provider",
    display: String = "test-llm-display"
) : LLMProvider(id, display)

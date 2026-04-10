package ai.koog.agents.features.opentelemetry.mock

import ai.koog.prompt.llm.LLMProvider

class MockLLMProvider : LLMProvider(
    "test-provider-id",
    "test-provider-name",
)

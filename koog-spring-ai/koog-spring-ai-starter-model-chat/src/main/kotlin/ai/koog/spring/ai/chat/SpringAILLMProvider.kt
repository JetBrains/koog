package ai.koog.spring.ai.chat

import ai.koog.prompt.llm.LLMProvider

/**
 * An [ai.koog.prompt.llm.LLMProvider] representing a Spring AI-backed provider.
 *
 * This is a singleton object. Java callers can access it via `SpringAILLMProvider.INSTANCE`.
 */
public object SpringAILLMProvider : LLMProvider("spring-ai", "Spring AI")

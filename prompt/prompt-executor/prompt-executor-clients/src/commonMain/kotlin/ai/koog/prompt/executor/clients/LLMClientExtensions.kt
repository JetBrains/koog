package ai.koog.prompt.executor.clients

import ai.koog.prompt.llm.LLModel

@InternalLLMClientApi
public fun LLMClient.requireMatchingProvider(model: LLModel) {
    require(model.provider == llmProvider()) {
        "Model provider mismatch: ${model.id}.provider=${model.provider}, " +
            "${this.clientName}.llmProvider=${llmProvider()}"
    }
}

package ai.grazie.code.agents.local.agent

import ai.grazie.code.agents.core.model.agent.AIAgentConfig
import ai.grazie.model.auth.GrazieAgent
import ai.jetbrains.code.prompt.dsl.Prompt
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.llm.LLModel
import ai.jetbrains.code.prompt.llm.OllamaModels

open class LocalAgentConfig(
    val prompt: Prompt,
    val maxAgentIterations: Int,
    val grazieAgent: GrazieAgent = GrazieAgent("code-engine-agents", "dev"),
) : AIAgentConfig {
    companion object {
        fun withSystemPrompt(
            prompt: String,
            llm: LLModel = OllamaModels.Meta.LLAMA_3_2,
            id: String = "code-engine-agents",
            maxAgentIterations: Int = 3,
            agent: GrazieAgent = GrazieAgent("code-engine-agents", "dev"),
        ): LocalAgentConfig {
            return LocalAgentConfig(
                prompt = prompt(llm, id) {
                    system(prompt)
                },
                maxAgentIterations = maxAgentIterations,
                grazieAgent = agent
            )
        }
    }
}
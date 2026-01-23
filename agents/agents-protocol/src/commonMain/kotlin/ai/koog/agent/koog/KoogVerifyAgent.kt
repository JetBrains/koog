package ai.koog.agent.koog

import ai.koog.agent.FlowAgentConfig
import ai.koog.agent.FlowAgentKind
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.model.FlowAgentInput
import ai.koog.model.FlowAgentPrompt

private const val DEFAULT_MAX_ITERATIONS = 10
private const val DEFAULT_VERIFY_PROMPT = "You are a verification agent. Verify the task was completed correctly. Return 'PASS' if correct, or describe the issues if not."

/**
 * Koog verification agent that verifies task completion.
 * This agent is created when type is "verify" in the flow configuration.
 */
public class KoogVerifyAgent(
    name: String,
    model: String? = null,
    prompt: FlowAgentPrompt? = null,
    input: FlowAgentInput,
    config: FlowAgentConfig
) : KoogFlowAgent(
    name = name,
    type = FlowAgentKind.VERIFY,
    model = model,
    prompt = prompt,
    input = input,
    config = config
) {
    override suspend fun execute(): String {
        return PromptExecutorFactory.createExecutor(model).use { executor ->
            val llmModel = PromptExecutorFactory.resolveModel(model)

            val agent = AIAgent(
                promptExecutor = executor,
                llmModel = llmModel,
                systemPrompt = prompt?.system ?: DEFAULT_VERIFY_PROMPT,
                maxIterations = config.maxIterations ?: DEFAULT_MAX_ITERATIONS,
                strategy = singleRunStrategy(),
                toolRegistry = ToolRegistry.EMPTY
            )

            agent.run(input.task ?: "")
        }
    }
}

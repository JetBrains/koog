package ai.koog.agents.example.funApi

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.extension.asAssistantMessage
import ai.koog.agents.core.dsl.extension.compressHistory
import ai.koog.agents.core.dsl.extension.containsToolCalls
import ai.koog.agents.core.dsl.extension.executeMultipleTools
import ai.koog.agents.core.dsl.extension.extractToolCalls
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.dsl.extension.latestTokenUsage
import ai.koog.agents.core.dsl.extension.requestLLMMultiple
import ai.koog.agents.core.dsl.extension.sendMultipleToolResults
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.runBlocking
import kotlin.uuid.ExperimentalUuidApi

@OptIn(ExperimentalUuidApi::class)
fun main(): Unit = runBlocking {
    val promptExec = simpleOllamaAIExecutor()
    val switch = Switch()
    val toolRegistry = ToolRegistry {
        tools(SwitchTools(switch).asTools())
    }

    val functionalAgent = AIAgent<String, String>(
        systemPrompt = "You're responsible for running a Switch device and perform operations on it by request.",
        promptExecutor = promptExec,
        strategy = functionalStrategy {
            var responses = requestLLMMultiple(it)

            while (responses.containsToolCalls()) {
                val tools = extractToolCalls(responses)

                if (latestTokenUsage() > 100500) {
                    compressHistory()
                }

                val results = executeMultipleTools(tools)
                responses = sendMultipleToolResults(results)
            }

            // Result:
            responses.single().asAssistantMessage().content
        },
        llmModel = OllamaModels.Meta.LLAMA_3_2,
        toolRegistry = toolRegistry
    ) {
        install(EventHandler) {
            onToolCallStarting { eventContext ->
                println("Tool called: tool ${eventContext.toolName}, args ${eventContext.toolArgs}")
            }
        }
    }

    functionalAgent.run("Turn switch on")
    println("Switch is ${if (switch.isOn()) "on" else "off"}")
}

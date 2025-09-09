package ai.koog.agents.example.funApi

import ai.koog.agents.core.agent.asAssistantMessage
import ai.koog.agents.core.agent.compressHistory
import ai.koog.agents.core.agent.containsToolCalls
import ai.koog.agents.core.agent.executeMultipleTools
import ai.koog.agents.core.agent.extractToolCalls
import ai.koog.agents.core.agent.functionalAIAgent
import ai.koog.agents.core.agent.latestTokenUsage
import ai.koog.agents.core.agent.requestLLMMultiple
import ai.koog.agents.core.agent.sendMultipleToolResults
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

    val functionalAgent = functionalAIAgent<String, String>(
        prompt = "You're responsible for running a Switch device and perform operations on it by request.",
        promptExecutor = promptExec,
        model = OllamaModels.Meta.LLAMA_3_2,
        toolRegistry = toolRegistry,
        featureContext = {
            install(EventHandler) {
                onToolCall { eventContext ->
                    println("Tool called: tool ${eventContext.tool.name}, args ${eventContext.toolArgs}")
                }
            }
        }
    ) {
        var responses = requestLLMMultiple(it)

        while (responses.containsToolCalls()) {
            val tools = extractToolCalls(responses)

            if (latestTokenUsage() > 100500) {
                compressHistory()
            }

            val results = executeMultipleTools(tools)
            responses = sendMultipleToolResults(results)
        }

        return@functionalAIAgent responses.single().asAssistantMessage().content
    }

    functionalAgent.run("Turn switch on")
    println("Switch is ${if (switch.isOn()) "on" else "off"}")
}

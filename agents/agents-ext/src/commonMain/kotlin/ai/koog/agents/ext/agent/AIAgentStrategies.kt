package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.result
import ai.koog.prompt.message.Message

/**
 * Creates and configures a [ai.koog.agents.core.agent.entity.AIAgentStrategy] for executing a chat interaction process.
 * The agent orchestrates interactions between different stages, nodes, and tools to
 * handle user input, execute tools, and provide responses.
 * Allows the agent to interact with the user in a chat-like manner.
 */
public fun chatAgentStrategy(): AIAgentStrategy = strategy("chat") {
    val nodeCallLLM by nodeLLMRequest("sendInput")
    val nodeExecuteTool by nodeExecuteTool("nodeExecuteTool")
    val nodeSendToolResult by nodeLLMSendToolResult("nodeSendToolResult")

    val giveFeedbackToCallTools by node<String, Message.Response> { input ->
        llm.writeSession {
            updatePrompt {
                user("Don't chat with plain text! Call one of the available tools, instead: ${tools.joinToString(", ") { it.name }}")
            }

            requestLLM()
        }
    }

    edge(nodeStart forwardTo nodeCallLLM)

    edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
    edge(nodeCallLLM forwardTo giveFeedbackToCallTools onAssistantMessage { true })

    edge(giveFeedbackToCallTools forwardTo giveFeedbackToCallTools onAssistantMessage { true })
    edge(giveFeedbackToCallTools forwardTo nodeExecuteTool onToolCall { true })

    edge(nodeExecuteTool forwardTo nodeSendToolResult)

    edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeSendToolResult forwardTo nodeFinish onToolCall { tc -> tc.tool == "__exit__" } transformed { "Chat finished" })
    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
}

/**
 * Creates a KotlinAgent instance configured to execute a sequence of operations for a single run process
 * involving stages for sending an input, calling tools, and returning the final result.
 *
 * Sometimes, it also called "one-shot" strategy.
 * Useful if you need to run a straightforward process that doesn't require a lot of additional logic.
 */
public fun singleRunStrategy(): AIAgentStrategy = strategy("single_run") {
    val nodeCallLLM by nodeLLMRequest("sendInput")
    val nodeExecuteTool by nodeExecuteTool("nodeExecuteTool")
    val nodeSendToolResult by nodeLLMSendToolResult("nodeSendToolResult")

    edge(nodeStart forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
    edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeExecuteTool forwardTo nodeSendToolResult)
    edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
}

/**
 * Creates a ReAct AI agent strategy that alternates between reasoning and execution stages
 * to dynamically process tasks and request outputs from an LLM.
 *
 * @param reasoningInterval Specifies the interval for reasoning steps.
 * @return An instance of [AIAgentStrategy] that defines the ReAct strategy.
 */
public fun reActStrategy(reasoningInterval: Int = 1): AIAgentStrategy = strategy("re_act") {
    val nodeCallLLM by node<Unit, Message.Response> {
        llm.writeSession {
            println(prompt.messages.size)
            requestLLM()
        }
    }
    val nodeExecuteTool by nodeExecuteTool("nodeExecuteTool")

    var reasoningStep = 0
    val reasoningPrompt = "Please give your thoughts about the task and plan the next steps."
    val nodeReasonInput by node<String, Unit> { stageInput ->
        llm.writeSession {
            updatePrompt {
                user(stageInput)
                user(reasoningPrompt)
            }

            requestLLMWithoutTools().content
        }
    }
    val nodeReasonAfterToolResult by node<ReceivedToolResult, Unit> { result ->
        reasoningStep++
        llm.writeSession {
            updatePrompt {
                tool {
                    result(result)
                }
            }

            if (reasoningStep % reasoningInterval == 0) {
                updatePrompt {
                    user(reasoningPrompt)
                }
                requestLLMWithoutTools().content
            }
        }
    }

    edge(nodeStart forwardTo nodeReasonInput)
    edge(nodeReasonInput forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
    edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeExecuteTool forwardTo nodeReasonAfterToolResult)
    edge(nodeReasonAfterToolResult forwardTo nodeCallLLM)
}

package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.entity.AIAgentStrategy
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.result
import ai.koog.prompt.message.Message

// FIXME improve this strategy to use Message.Assistant to chat, it works better than tools

/**
 * Creates and configures a [ai.koog.agents.core.agent.entity.AIAgentStrategy] for executing a chat interaction process.
 * The agent orchestrates interactions between different stages, nodes, and tools to
 * handle user input, execute tools, and provide responses.
 * Allows the agent to interact with the user in a chat-like manner.
 */
public fun chatAgentStrategy(): AIAgentStrategy<String, String> = strategy("chat") {
    val nodeCallLLM by nodeLLMRequest("sendInput")
    val nodeExecuteTool by nodeExecuteTool("nodeExecuteTool")
    val nodeSendToolResult by nodeLLMSendToolResult("nodeSendToolResult")

    val giveFeedbackToCallTools by node<String, Message.Response> { input ->
        llm.writeSession {
            updatePrompt {
                user(
                    "Don't chat with plain text! Call one of the available tools, instead: ${tools.joinToString(", ") {
                        it.name
                    }}"
                )
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
    edge(
        nodeSendToolResult forwardTo nodeFinish onToolCall { tc -> tc.tool == "__exit__" } transformed
            { "Chat finished" }
    )
    edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
}

/**
 * Creates a ReAct AI agent strategy that alternates between reasoning and execution stages
 * to dynamically process tasks and request outputs from an LLM.
 *
 * @param reasoningInterval Specifies the interval for reasoning steps.
 * @return An instance of [AIAgentStrategy] that defines the ReAct strategy.
 *
 *
 * +-------+             +---------------+             +---------------+             +--------+
 * | Start | ----------> | CallLLMReason | ----------> | CallLLMAction | ----------> | Finish |
 * +-------+             +---------------+             +---------------+             +--------+
 *                                   ^                       | Finished?     Yes
 *                                   |                       | No
 *                                   |                       v
 *                                   +-----------------------+
 *                                   |      ExecuteTool      |
 *                                   +-----------------------+
 *
 * Example execution flow of a banking agent with ReAct strategy:
 *
 * 1. Start: User asks "How much did I spend last month?"
 *
 * 2. Reasoning Phase:
 *    CallLLMReason: "I need to follow these steps:
 *    1. Get all transactions from last month
 *    2. Filter out deposits (positive amounts)
 *    3. Calculate total spending"
 *
 * 3. Action & Execution Phase 1:
 *    CallLLMAction: {tool: "get_transactions", args: {startDate: "2025-05-19", endDate: "2025-06-18"}}
 *    ExecuteTool Result: [
 *      {date: "2025-05-25", amount: -100.00, description: "Grocery Store"},
 *      {date: "2025-05-31", amount: +1000.00, description: "Salary Deposit"},
 *      {date: "2025-06-10", amount: -500.00, description: "Rent Payment"},
 *      {date: "2025-06-13", amount: -200.00, description: "Utilities"}
 *    ]
 *
 * 4. Reasoning Phase:
 *    CallLLMReason: "I have the transactions. Now I need to:
 *    1. Remove the salary deposit of +1000.00
 *    2. Sum up the remaining transactions"
 *
 * 5. Action & Execution Phase 2:
 *    CallLLMAction: {tool: "calculate_sum", args: {amounts: [-100.00, -500.00, -200.00]}}
 *    ExecuteTool Result: -800.00
 *
 * 6. Final Response:
 *    Assistant: "You spent $800.00 last month on groceries, rent, and utilities."
 *
 * 7. Finish: Execution complete
 */
public fun reActStrategy(
    reasoningInterval: Int = 1,
    name: String = "re_act"
): AIAgentStrategy<String, String> = strategy(name) {
    require(reasoningInterval > 0) { "Reasoning interval must be greater than 0" }
    val reasoningStepKey = createStorageKey<Int>("reasoning_step")
    val nodeSetup by node<String, String> {
        storage.set(reasoningStepKey, 0)
        it
    }
    val nodeCallLLM by node<Unit, Message.Response> {
        llm.writeSession {
            requestLLM()
        }
    }
    val nodeExecuteTool by nodeExecuteTool()

    val reasoningPrompt = "Please give your thoughts about the task and plan the next steps."
    val nodeCallLLMReasonInput by node<String, Unit> { stageInput ->
        llm.writeSession {
            updatePrompt {
                user(stageInput)
                user(reasoningPrompt)
            }

            requestLLMWithoutTools()
        }
    }
    val nodeCallLLMReason by node<ReceivedToolResult, Unit> { result ->
        val reasoningStep = storage.getValue(reasoningStepKey)
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
                requestLLMWithoutTools()
            }
        }
        storage.set(reasoningStepKey, reasoningStep + 1)
    }

    edge(nodeStart forwardTo nodeSetup)
    edge(nodeSetup forwardTo nodeCallLLMReasonInput)
    edge(nodeCallLLMReasonInput forwardTo nodeCallLLM)
    edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
    edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
    edge(nodeExecuteTool forwardTo nodeCallLLMReason)
    edge(nodeCallLLMReason forwardTo nodeCallLLM)
}

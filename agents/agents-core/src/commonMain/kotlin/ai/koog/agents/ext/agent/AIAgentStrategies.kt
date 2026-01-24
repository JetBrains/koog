package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteMultipleTools
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMRequestMultiple
import ai.koog.agents.core.dsl.extension.nodeLLMSendMultipleToolResults
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.nodeSetStructuredOutput
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onMultipleAssistantMessages
import ai.koog.agents.core.dsl.extension.onMultipleToolCalls
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.result
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructuredRequestConfig
import kotlin.jvm.JvmOverloads

// FIXME improve this strategy to use Message.Assistant to chat, it works better than tools

/**
 * Creates and configures a [ai.koog.agents.core.agent.entity.AIAgentGraphStrategy] for executing a chat interaction process.
 * The agent orchestrates interactions between different stages, nodes, and tools to
 * handle user input, execute tools, and provide responses.
 * Allows the agent to interact with the user in a chat-like manner.
 */
public fun chatAgentStrategy(): AIAgentGraphStrategy<String, String> = strategy("chat") {
    val nodeCallLLM by nodeLLMRequest("sendInput")
    val nodeExecuteTool by nodeExecuteTool("nodeExecuteTool")
    val nodeSendToolResult by nodeLLMSendToolResult("nodeSendToolResult")

    val giveFeedbackToCallTools by node<String, Message.Response> { input ->
        llm.writeSession {
            appendPrompt {
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
 * @return An instance of [AIAgentGraphStrategy] that defines the ReAct strategy.
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
@JvmOverloads
public fun reActStrategy(
    reasoningInterval: Int = 1,
    name: String = "re_act"
): AIAgentGraphStrategy<String, String> = strategy(name) {
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
            appendPrompt {
                user(stageInput)
                user(reasoningPrompt)
            }

            requestLLMWithoutTools()
        }
    }
    val nodeCallLLMReason by node<ReceivedToolResult, Unit> { result ->
        val reasoningStep = storage.getValue(reasoningStepKey)
        llm.writeSession {
            appendPrompt {
                tool {
                    result(result)
                }
            }

            if (reasoningStep % reasoningInterval == 0) {
                appendPrompt {
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

/**
 * Defines a strategy for handling structured output with tools integration using specified configuration and execution logic.
 *
 * This strategy facilitates a structured pipeline for generating outputs using tools and large language models (LLMs),
 * enabling transformations between input, intermediate results, and structured output based on the provided configuration and execution behavior.
 *
 * @param Output The type of the structured output generated by the strategy.
 * @param config The configuration for structured output processing, specifying schema, providers, and optional error handling mechanisms.
 */
@JvmOverloads
public inline fun <reified Output> structuredOutputWithToolsStrategy(
    config: StructuredRequestConfig<Output>,
    parallelTools: Boolean = false
): AIAgentGraphStrategy<String, Output> = structuredOutputWithToolsStrategy(
    config,
    parallelTools
) { it }

/**
 * Defines a strategy for handling structured output with tools integration using specified configuration and execution logic.
 *
 * This strategy facilitates a structured pipeline for generating outputs using tools and large language models (LLMs),
 * enabling transformations between input, intermediate results, and structured output based on the provided configuration and execution behavior.
 *
 * @param Input The type of the input to be processed by the strategy.
 * @param Output The type of the structured output generated by the strategy.
 * @param config The configuration for structured output processing, specifying schema, providers, and optional error handling mechanisms.
 * @param transform A suspendable function that accepts the input of type `Input` and produces a string output
 *                that serves as the input for further processing in the structured output pipeline.
 */
@JvmOverloads
public inline fun <reified Input, reified Output> structuredOutputWithToolsStrategy(
    config: StructuredRequestConfig<Output>,
    parallelTools: Boolean = false,
    noinline transform: suspend AIAgentGraphContextBase.(input: Input) -> String
): AIAgentGraphStrategy<Input, Output> = strategy<Input, Output>("structured_output_with_tools_strategy") {
    val setStructuredOutput by nodeSetStructuredOutput<Input, Output>(config = config)
    val transformInput by node<Input, String> { transform(it) }
    val callLLM by nodeLLMRequestMultiple()
    val executeTools by nodeExecuteMultipleTools(parallelTools = parallelTools)
    val sendToolResult by nodeLLMSendMultipleToolResults()
    val transformToStructuredOutput by node<Message.Assistant, Output> { response ->
        llm.writeSession {
            parseResponseToStructuredResponse(response, config).data
        }
    }

    // Set the structured output, get the input and then call the llm
    nodeStart then setStructuredOutput then transformInput then callLLM

    // On tools
    edge(callLLM forwardTo executeTools onMultipleToolCalls { true })
    edge(executeTools forwardTo sendToolResult)

    // On assistant messages
    edge(
        callLLM forwardTo transformToStructuredOutput
            onMultipleAssistantMessages { true }
            transformed { it.single() }
    )

    // Post tool result
    edge(sendToolResult forwardTo executeTools onMultipleToolCalls { true })
    edge(
        sendToolResult forwardTo transformToStructuredOutput
            onMultipleAssistantMessages { true }
            transformed { it.first() }
    )

    // Finish
    transformToStructuredOutput then nodeFinish
}

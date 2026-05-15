@file:JvmName("AIAgentStrategies")

package ai.koog.agents.ext.agent

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.asUserMessage
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeExecuteToolsAndGetResults
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResults
import ai.koog.agents.core.dsl.extension.nodeSetStructuredOutput
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.dsl.extension.onToolResults
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.structure.StructuredRequestConfig
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

// FIXME improve this strategy to use Message.Assistant to chat, it works better than tools

/**
 * Creates and configures a [ai.koog.agents.core.agent.entity.AIAgentGraphStrategy] for executing a chat interaction process.
 * The agent orchestrates interactions between different stages, nodes, and tools to
 * handle user input, execute tools, and provide responses.
 * Allows the agent to interact with the user in a chat-like manner.
 */
public fun chatAgentStrategy(): AIAgentGraphStrategy<String, String> = strategy("chat") {
    val nodeLLMRequest by nodeLLMRequest("sendInput")
    val nodeExecuteTools by nodeExecuteTools("nodeExecuteTool")

    val giveFeedbackToCallTools by node<String, String> { input ->
        llm.writeSession {
            "Don't chat with plain text! Call one of the available tools, instead: ${
                tools.joinToString(", ") {
                    it.name
                }
            }"
        }
    }

    edge(nodeStart forwardTo nodeLLMRequest asUserMessage { it })

    edge(nodeLLMRequest forwardTo nodeExecuteTools onToolCalls { true })
    edge(nodeLLMRequest forwardTo giveFeedbackToCallTools onTextMessage { true })
    edge(
        nodeExecuteTools forwardTo nodeFinish
            onToolResults { it.tool == "__exit__" }
            transformed { "Chat finished" }
    )
    edge(nodeExecuteTools forwardTo nodeLLMRequest)
    edge(giveFeedbackToCallTools forwardTo nodeLLMRequest asUserMessage { it })
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
    name: String = "re_act",
    reasoningPrompt: String = "Please give your thoughts about the task and plan the next steps."
): AIAgentGraphStrategy<String, String> = strategy(name) {
    require(reasoningInterval > 0) { "Reasoning interval must be greater than 0" }
    val reasoningStepKey = createStorageKey<Int>("reasoning_step")
    val nodeSetup by node<String, String> {
        storage.set(reasoningStepKey, 0)
        it
    }
    val nodeRequestLLMWithTools by node<Unit, Message.Assistant> {
        llm.writeSession {
            requestLLM()
        }
    }

    val nodeExecuteTools by nodeExecuteTools()

    val nodeRequestLLMReason by node<Message.User, Unit> { result ->
        val reasoningStep = storage.getValue(reasoningStepKey)
        llm.writeSession {
            appendPrompt {
                message(result)
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
    edge(nodeSetup forwardTo nodeRequestLLMReason asUserMessage { "$it\n$reasoningPrompt" })
    edge(nodeRequestLLMReason forwardTo nodeRequestLLMWithTools)
    edge(nodeRequestLLMWithTools forwardTo nodeExecuteTools onToolCalls { true })
    edge(nodeRequestLLMWithTools forwardTo nodeFinish onTextMessage { true })
    edge(nodeExecuteTools forwardTo nodeRequestLLMReason)
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
    fixingParser: StructureFixingParser? = null,
    parallelTools: Boolean = false,
): AIAgentGraphStrategy<String, Output> = structuredOutputWithToolsStrategy(
    config,
    fixingParser,
    parallelTools,
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
    fixingParser: StructureFixingParser? = null,
    parallelTools: Boolean = false,
    noinline transform: suspend (input: Input) -> String,
): AIAgentGraphStrategy<Input, Output> = strategy<Input, Output>("structured_output_with_tools_strategy") {
    val setStructuredOutput by nodeSetStructuredOutput<Input, Output>(config = config)
    val transformInput by node<Input, String> { transform(it) }
    val callLLM by nodeLLMRequest()
    val executeTools by nodeExecuteToolsAndGetResults(parallel = parallelTools)
    val sendToolResult by nodeLLMSendToolResults()
    val transformToStructuredOutput by node<Message.Assistant, Output> { response ->
        llm.writeSession {
            parseResponseToStructuredResponse(response, config, fixingParser).data
        }
    }

    // Set the structured output, transform the input to a String, then call the LLM as a user message
    nodeStart then setStructuredOutput then transformInput
    edge(transformInput forwardTo callLLM asUserMessage { it })

    // If the LLM responded with tool calls, execute them and feed the results back
    edge(callLLM forwardTo executeTools onToolCalls { true })
    edge(executeTools forwardTo sendToolResult)

    // If the LLM responded with a plain text answer, parse it into the structured output
    edge(
        callLLM forwardTo transformToStructuredOutput
            onCondition { msg -> msg.parts.none { it is MessagePart.Tool.Call } }
    )

    // After sending tool results, the LLM may continue calling tools or produce the final text answer
    edge(sendToolResult forwardTo executeTools onToolCalls { true })
    edge(
        sendToolResult forwardTo transformToStructuredOutput
            onCondition { msg -> msg.parts.none { it is MessagePart.Tool.Call } }
    )

    transformToStructuredOutput then nodeFinish
}

package ai.koog.agents.example.calculator

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

@Suppress("unused")
@LLMDescription("Tools for basic calculator operations")
class CalculatorTools : ToolSet {

    @Tool
    @LLMDescription("Adds two numbers")
    fun plus(
        @LLMDescription("First number")
        a: Float,

        @LLMDescription("Second number")
        b: Float
    ): String {
        return (a + b).toString()
    }

    @Tool
    @LLMDescription("Subtracts the second number from the first")
    fun minus(
        @LLMDescription("First number")
        a: Float,

        @LLMDescription("Second number")
        b: Float
    ): String {
        return (a - b).toString()
    }

    @Tool
    @LLMDescription("Divides the first number by the second")
    fun divide(
        @LLMDescription("First number")
        a: Float,

        @LLMDescription("Second number")
        b: Float
    ): String {
        return (a / b).toString()
    }

    @Tool
    @LLMDescription("Multiplies two numbers")
    fun multiply(
        @LLMDescription("First number")
        a: Float,

        @LLMDescription("Second number")
        b: Float
    ): String {
        return (a * b).toString()
    }
}

object CalculatorStrategy {
    private const val MAX_TOKENS_THRESHOLD = 1000

    val strategy = strategy<String, String>("test") {
        val nodeCallLLM by nodeLLMRequestMultiple()
        val nodeExecuteToolMultiple by nodeExecuteMultipleTools(parallelTools = true)
        val nodeSendToolResultMultiple by nodeLLMSendMultipleToolResults()
        val nodeCompressHistory by nodeLLMCompressHistory<List<ReceivedToolResult>>()

        edge(nodeStart forwardTo nodeCallLLM)

        edge(
            (nodeCallLLM forwardTo nodeFinish)
                    transformed { it.first() }
                    onAssistantMessage { true }
        )

        edge(
            (nodeCallLLM forwardTo nodeExecuteToolMultiple)
                    onMultipleToolCalls { true }
        )

        edge(
            (nodeExecuteToolMultiple forwardTo nodeCompressHistory)
                    onCondition { llm.readSession { prompt.latestTokenUsage > MAX_TOKENS_THRESHOLD } }
        )

        edge(nodeCompressHistory forwardTo nodeSendToolResultMultiple)

        edge(
            (nodeExecuteToolMultiple forwardTo nodeSendToolResultMultiple)
                    onCondition { llm.readSession { prompt.latestTokenUsage <= MAX_TOKENS_THRESHOLD } }
        )

        edge(
            (nodeSendToolResultMultiple forwardTo nodeExecuteToolMultiple)
                    onMultipleToolCalls { true }
        )

        edge(
            (nodeSendToolResultMultiple forwardTo nodeFinish)
                    transformed { it.first() }
                    onAssistantMessage { true }
        )
    }
}
package ai.koog.agents.example.chess.choice

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.feature.choice.nodeSelectLLMChoice
import ai.koog.agents.core.feature.choice.nodeLLMSendResultsMultipleChoices
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.example.chess.ChessGame
import ai.koog.agents.example.chess.Move
import ai.koog.agents.example.chess.nodeTrimHistory
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.message.Message
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    val game = ChessGame()

    val toolRegistry = ToolRegistry {
        tools(listOf(Move(game)))
    }

    val askChoiceStrategy = AskUserChoiceSelectionStrategy(promptShowToUser = { prompt ->
        val lastMessage = prompt.messages.last()
        if (lastMessage is Message.Tool.Call) {
            lastMessage.content
        } else {
            ""
        }
    })

    val strategy = strategy<String, String>("chess_strategy") {
        val nodeCallLLM by nodeLLMRequest("sendInput")
        val nodeExecuteTool by nodeExecuteTool("nodeExecuteTool")
        val nodeSendToolResult by nodeLLMSendResultsMultipleChoices("nodeSendToolResult")
        val nodeSelectLLMChoice by nodeSelectLLMChoice(askChoiceStrategy, "chooseLLMChoice")
        val nodeTrimHistory by nodeTrimHistory<ReceivedToolResult>()

        edge(nodeStart forwardTo nodeCallLLM)
        edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
        edge(nodeExecuteTool forwardTo nodeTrimHistory)
        edge(nodeTrimHistory forwardTo nodeSendToolResult transformed { listOf(it) })
        edge(nodeSendToolResult forwardTo nodeSelectLLMChoice)
        edge(nodeSelectLLMChoice forwardTo nodeFinish transformed { it.first() } onAssistantMessage { true })
        edge(nodeSelectLLMChoice forwardTo nodeExecuteTool transformed { it.first() } onToolCall { true })
    }

    // Create a chat agent with a system prompt and the tool registry
    val agent = AIAgent(
        executor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
        strategy = strategy,
        llmModel = OpenAIModels.Reasoning.O3Mini,
        systemPrompt = """
            You are an agent who plays chess.
            You should always propose a move in response to the "Your move!" message.
            
            DO NOT HALLUCINATE!!!
            DO NOT PLAY ILLEGAL MOVES!!!
            YOU CAN SEND A MESSAGE ONLY IF IT IS A RESIGNATION OR A CHECKMATE!!!
        """.trimMargin(),
        temperature = 1.0,
        toolRegistry = toolRegistry,
        maxIterations = 200,
        numberOfChoices = 3,
    )

    println("Chess Game started!")

    val initialMessage = "Starting position is ${game.getBoard()}. White to move!"

    agent.run(initialMessage)
}
package ai.koog.agents.example.chess.choice

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.example.chess.ChessGame
import ai.koog.agents.example.chess.Move
import ai.koog.agents.example.chess.nodeTrimHistory
import ai.koog.agents.ext.llm.choice.PromptExecutorWithChoiceSelection
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    val game = ChessGame()

    val toolRegistry = ToolRegistry {
        tools(listOf(Move(game)))
    }

    val strategy = strategy<String, String>("chess_strategy") {
        val nodeCallLLM by nodeLLMRequest("sendInput")
        val nodeExecuteTool by nodeExecuteTool("nodeExecuteTool")
        val nodeSendToolResult by nodeLLMSendToolResult("nodeSendToolResult")
        val nodeTrimHistory by nodeTrimHistory<ReceivedToolResult>()

        edge(nodeStart forwardTo nodeCallLLM)
        edge(nodeCallLLM forwardTo nodeExecuteTool onToolCall { true })
        edge(nodeCallLLM forwardTo nodeFinish onAssistantMessage { true })
        edge(nodeExecuteTool forwardTo nodeTrimHistory)
        edge(nodeTrimHistory forwardTo nodeSendToolResult)
        edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
        edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
    }

    val askChoiceStrategy = AskUserChoiceSelectionStrategy(promptShowToUser = { prompt ->
        val lastMessage = prompt.messages.last()
        if (lastMessage is Message.Tool.Call) {
            lastMessage.content
        } else {
            ""
        }
    })

    val basePromptExecutor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey)
    val promptExecutor = PromptExecutorWithChoiceSelection(basePromptExecutor, askChoiceStrategy)

    val agentConfig = AIAgentConfig(
        prompt = prompt("chess", LLMParams(temperature = 1.0, numberOfChoices = 3)) {
            system(
                """
                You are an agent who plays chess.
                You should always propose a move in response to the "Your move!" message.
                
                DO NOT HALLUCINATE!!!
                DO NOT PLAY ILLEGAL MOVES!!!
                YOU CAN SEND A MESSAGE ONLY IF IT IS A RESIGNATION OR A CHECKMATE!!!
                """.trimIndent()
            )
        },
        model = OpenAIModels.Chat.O3Mini,
        maxAgentIterations = 200,
    )

    val agent = AIAgent(
        promptExecutor = promptExecutor,
        agentConfig = agentConfig,
        strategy = strategy,
        toolRegistry = toolRegistry,
    )

    println("Chess Game started!")

    val initialMessage = "Starting position is ${game.getBoard()}. White to move!"

    agent.run(initialMessage)
}

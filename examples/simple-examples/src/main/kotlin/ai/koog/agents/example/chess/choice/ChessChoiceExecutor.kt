package ai.koog.agents.example.chess.choice

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.ReceivedToolResults
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResults
import ai.koog.agents.core.dsl.extension.onTextMessage
import ai.koog.agents.core.dsl.extension.onToolCalls
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
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.params.LLMParams
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
    val game = ChessGame()

    val toolRegistry = ToolRegistry {
        tools(listOf(Move(game)))
    }

    val strategy = strategy<String, String>("chess_strategy") {
        val nodeCallLLM by nodeLLMRequest("sendInput")
        val nodeExecuteTool by nodeExecuteTools("nodeExecuteTool")
        val nodeSendToolResult by nodeLLMSendToolResults("nodeSendToolResult")
        val nodeTrimHistory by nodeTrimHistory<ReceivedToolResults>()

        edge(nodeStart forwardTo nodeCallLLM)
        edge(nodeCallLLM forwardTo nodeExecuteTool onToolCalls { true })
        edge(nodeCallLLM forwardTo nodeFinish onTextMessage { true })
        edge(nodeExecuteTool forwardTo nodeTrimHistory)
        edge(nodeTrimHistory forwardTo nodeSendToolResult)
        edge(nodeSendToolResult forwardTo nodeFinish onTextMessage { true })
        edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCalls { true })
    }

    val askChoiceStrategy = AskUserChoiceSelectionStrategy(promptShowToUser = { prompt ->
        val lastMessage = prompt.messages.last()
        if (lastMessage is Message.Assistant) {
            lastMessage.parts.filterIsInstance<MessagePart.Tool.Call>().joinToString("\n") { it.args }
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

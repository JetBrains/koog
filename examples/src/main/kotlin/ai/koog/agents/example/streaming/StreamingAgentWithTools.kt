package ai.koog.agents.example.streaming

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.entity.AIAgentSubgraph
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteMultipleTools
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStreamingAndSendResults
import ai.koog.agents.core.dsl.extension.onMultipleToolCalls
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.example.simpleapi.Switch
import ai.koog.agents.example.simpleapi.SwitchTools
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

fun main(): Unit = runBlocking {
    val switch = Switch()

    /**
     * Turning off the logging for this agent
     */
    listOf(
        AIAgentSubgraph::class.qualifiedName
    ).forEach {
        val logbackLogger = LoggerFactory.getLogger(it) as Logger
        logbackLogger.level = Level.OFF
    }

    /*
     *
     * You can also use the DSL to create a tool registry:
     *   val toolRegistry = SimpleToolRegistry {
     *       tool(SwitchTool(switch))
     *       tool(SwitchStateTool(switch))
     *   }
     * */
    val toolRegistry = ToolRegistry {
        tools(SwitchTools(switch).asTools())
    }
    val agent = AIAgent(
        promptExecutor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
        strategy = streamingWithToolsStrategy(),
        llmModel = OpenAIModels.CostOptimized.GPT4oMini,
        systemPrompt = "You're responsible for running a Switch and perform operations on it by request",
        temperature = 0.0,
        toolRegistry = toolRegistry
    ) {
        handleEvents {
            onStreamFrame { context ->
                (context.streamFrame as? StreamFrame.Append)?.let { frame ->
                    print(frame.text)
                }
            }
            onAfterStream {
                println("")
            }
        }
    }

    println("Streaming chat agent started\nUse /quit to quit\nEnter your message:")
    var input = ""
    while(input != "/quit") {
        input = readln()

        // Example message:
        // Tell me if the switch if on or off. Elaborate on how you will determine that. After that, if it was off, turn it on. Be very verbose in all the steps

        agent.run(input)

        println()
        println("Enter your message:")
    }
}

fun streamingWithToolsStrategy() = strategy("streaming_loop") {
    val executeMultipleTools by nodeExecuteMultipleTools(parallelTools = true)
    val nodeStreaming by nodeLLMRequestStreamingAndSendResults()

    val mapStringToRequests by node<String, List<Message.Request>> { input ->
        listOf(Message.User(content = input, metaInfo = RequestMetaInfo.Empty))
    }

    val applyRequestToSession by node<List<Message.Request>, List<Message.Request>> { input ->
        llm.writeSession {
            updatePrompt {
                input.filterIsInstance<Message.User>()
                    .forEach {
                        user(it.content)
                    }

                tool {
                    input.filterIsInstance<Message.Tool.Result>()
                        .forEach {
                            result(it)
                        }
                }
            }
            input
        }
    }

    val mapToolCallsToRequests by node<List<ReceivedToolResult>, List<Message.Request>> { input ->
        input.map { it.toMessage() }
    }

    edge(nodeStart forwardTo mapStringToRequests)
    edge(mapStringToRequests forwardTo applyRequestToSession)
    edge(applyRequestToSession forwardTo nodeStreaming)
    edge(nodeStreaming forwardTo executeMultipleTools onMultipleToolCalls { true })
    edge(executeMultipleTools forwardTo mapToolCallsToRequests)
    edge(mapToolCallsToRequests forwardTo applyRequestToSession)
    edge(nodeStreaming forwardTo nodeFinish onCondition {
        it.filterIsInstance<Message.Tool.Call>().isEmpty()
    })
}

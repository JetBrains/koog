package ai.koog.agents.example.a2a.advancedjoke

import ai.koog.a2a.exceptions.A2AUnsupportedOperationException
import ai.koog.a2a.model.Artifact
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskArtifactUpdateEvent
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatus
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.server.agent.AgentExecutor
import ai.koog.a2a.server.session.RequestContext
import ai.koog.a2a.server.session.SessionEventProcessor
import ai.koog.agents.a2a.core.A2AMessage
import ai.koog.agents.a2a.core.toKoogMessage
import ai.koog.agents.a2a.server.feature.A2AAgentServer
import ai.koog.agents.a2a.server.feature.withA2AAgentServer
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.agentInput
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStructured
import ai.koog.agents.core.dsl.extension.onIsInstance
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.example.ApiKeyService
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.text.text
import ai.koog.prompt.xml.xml
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.typeOf
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * An advanced A2A agent that demonstrates:
 * - Task-based conversation flow with state management
 * - Interactive clarification questions (InputRequired state)
 * - Structured output via sealed interfaces
 * - Artifact delivery for final results
 */
class JokeWriterAgentExecutor : AgentExecutor {
    private val promptExecutor = MultiLLMPromptExecutor(
        LLMProvider.OpenAI to OpenAILLMClient(ApiKeyService.openAIApiKey),
        LLMProvider.Anthropic to AnthropicLLMClient(ApiKeyService.anthropicApiKey),
        LLMProvider.Google to GoogleLLMClient(ApiKeyService.googleApiKey),
    )

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun execute(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor
    ) {
        val agent = jokeWriterAgent(promptExecutor, context, eventProcessor)
        agent.run(context.params.message)
    }
}

private fun jokeWriterAgent(
    promptExecutor: PromptExecutor,
    context: RequestContext<MessageSendParams>,
    eventProcessor: SessionEventProcessor
): GraphAIAgent<A2AMessage, Unit> {
    val agentConfig = AIAgentConfig(
        prompt = prompt("joke-generation") {
            system {
                +"You are a very funny sarcastic assistant. You must help users generate funny jokes."
                +"When asked for something else, sarcastically decline the request because you can only assist with jokes."
            }
        },
        model = GoogleModels.Gemini2_5Flash,
        maxAgentIterations = 20
    )

    return GraphAIAgent(
        inputType = typeOf<A2AMessage>(),
        outputType = typeOf<Unit>(),
        promptExecutor = promptExecutor,
        strategy = jokeWriterStrategy(),
        agentConfig = agentConfig,
        toolRegistry = ToolRegistry.EMPTY,
    ) {
        install(A2AAgentServer) {
            this.context = context
            this.eventProcessor = eventProcessor
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun jokeWriterStrategy() = strategy<A2AMessage, Unit>("joke-writer") {
    // Node: Load conversation history from message storage
    val setupMessageContext by node<A2AMessage, A2AMessage> { userInput ->
        if (!userInput.referenceTaskIds.isNullOrEmpty()) {
            throw A2AUnsupportedOperationException("This agent doesn't understand task references in referenceTaskIds yet.")
        }

        // Load current context messages
        val contextMessages: List<A2AMessage> = withA2AAgentServer {
            context.messageStorage.getAll()
        }

        // Append the current context messages to prompt
        llm.writeSession {
            appendPrompt {
                messages(contextMessages.map { it.toKoogMessage() })
            }
        }

        userInput
    }

    // Node: Load existing task (if continuing) or prepare for new task creation
    val setupTaskContext by node<A2AMessage, Task?> { userInput ->
        // Check if the message continues the task that already exists
        val currentTask: Task? = withA2AAgentServer {
            context.task?.id?.let { id ->
                // Load task with full conversation history to continue working on it
                context.taskStorage.get(id, historyLength = null)
            }
        }

        currentTask?.let { task ->
            val currentTaskMessages = (task.history.orEmpty() + listOfNotNull(task.status.message) + userInput)
                .map { it.toKoogMessage() }

            llm.writeSession {
                appendPrompt {
                    user {
                        +"There's an ongoing task, the next messages contain conversation history for this task"
                    }

                    messages(currentTaskMessages)
                }
            }
        }

        /*
         If task exists then the message belongs to the task, send event to update the task.
         Otherwise, put it in general message storage for the current context.
         */
        withA2AAgentServer {
            if (currentTask != null) {
                val updateEvent = TaskStatusUpdateEvent(
                    taskId = currentTask.id,
                    contextId = currentTask.contextId,
                    status = TaskStatus(
                        state = TaskState.Working,
                        message = userInput,
                        timestamp = Clock.System.now(),
                    ),
                    final = false,
                )

                eventProcessor.sendTaskEvent(updateEvent)
            } else {
                context.messageStorage.save(userInput)
            }
        }

        currentTask
    }

    // Node: Ask LLM to classify if this is a joke request or something else
    val classifyNewRequest by nodeLLMRequestStructured<UserRequestClassification>()

    // Node: Send a polite decline message if the request is not about jokes
    val respondFallbackMessage by node<UserRequestClassification, Unit> { classification ->
        withA2AAgentServer {
            val message = A2AMessage(
                messageId = Uuid.random().toString(),
                role = Role.Agent,
                parts = listOf(
                    TextPart(classification.response)
                ),
                contextId = context.contextId,
                taskId = context.taskId,
            )

            // Store reply in message storage to preserve context
            context.messageStorage.save(message)
            // Reply with message
            eventProcessor.sendMessage(message)
        }
    }

    // Node: Create a new task for the joke request
    val createTask by node<UserRequestClassification, Unit> {
        val userInput = agentInput<A2AMessage>()

        withA2AAgentServer {
            val task = Task(
                id = context.taskId,
                contextId = context.contextId,
                status = TaskStatus(
                    state = TaskState.Submitted,
                    message = userInput,
                    timestamp = Clock.System.now(),
                ),

            )

            eventProcessor.sendTaskEvent(task)
        }
    }

    // Node: Ask LLM to classify joke details (or request clarification)
    val classifyJokeRequest by nodeLLMRequestStructured<JokeRequestClassification>()

    // Node: Generate the actual joke based on classified parameters
    val generateJoke by node<JokeRequestClassification.Ready, Message.Assistant> { request ->
        llm.writeSession {
            appendPrompt {
                user {
                    +"Generate a joke based on the following user request:"
                    xml {
                        tag("subject") {
                            +request.subject
                        }
                        tag("targetAudience") {
                            +request.targetAudience
                        }
                        tag("isSwearingAllowed") {
                            +request.isSwearingAllowed.toString()
                        }
                    }
                }
            }

            val message = requestLLMWithoutTools()
            message as? Message.Assistant ?: throw IllegalStateException("Unexpected message type: $message")
        }
    }

    // Node: Send InputRequired event to ask the user for more information
    val askMoreInfo by node<JokeRequestClassification.NeedsClarification, Unit> { clarification ->
        withA2AAgentServer {
            val taskUpdate = TaskStatusUpdateEvent(
                taskId = context.taskId,
                contextId = context.contextId,
                status = TaskStatus(
                    state = TaskState.InputRequired,
                    message = A2AMessage(
                        role = Role.User,
                        parts = listOf(
                            TextPart(clarification.question)
                        ),
                        messageId = Uuid.random().toString(),
                        taskId = context.taskId,
                        contextId = context.contextId
                    ),
                    timestamp = Clock.System.now(),
                ),
                final = true,
            )

            eventProcessor.sendTaskEvent(taskUpdate)
        }
    }

    // Node: Send the joke as an artifact and mark task as completed
    val respondWithJoke by node<Message, Unit> { jokeMessage ->
        withA2AAgentServer {
            val artifactUpdate = TaskArtifactUpdateEvent(
                taskId = context.taskId,
                contextId = context.contextId,
                artifact = Artifact(
                    artifactId = "joke",
                    parts = listOf(
                        TextPart(jokeMessage.content)
                    )
                ),
            )

            eventProcessor.sendTaskEvent(artifactUpdate)

            val taskStatusUpdate = TaskStatusUpdateEvent(
                taskId = context.taskId,
                contextId = context.contextId,
                status = TaskStatus(
                    state = TaskState.Completed,
                ),
                final = true,
            )

            eventProcessor.sendTaskEvent(taskStatusUpdate)
        }
    }

    // --- Graph Flow Definition ---

    // Always start by loading context and checking for existing tasks
    nodeStart then setupMessageContext then setupTaskContext

    // If no task exists, classify whether this is a joke request
    edge(
        setupTaskContext forwardTo classifyNewRequest
            onCondition { task -> task == null }
            transformed { agentInput<A2AMessage>().content() }
    )
    // If task exists, continue processing the joke request
    edge(
        setupTaskContext forwardTo classifyJokeRequest
            onCondition { task -> task != null }
            transformed { agentInput<A2AMessage>().content() }
    )

    // New request classification: If not a joke request, decline politely
    edge(
        classifyNewRequest forwardTo respondFallbackMessage
            transformed { it.getOrThrow().structure }
            onCondition { !it.isJokeRequest }
    )
    // New request classification: If joke request, create a task
    edge(
        classifyNewRequest forwardTo createTask
            transformed { it.getOrThrow().structure }
            onCondition { it.isJokeRequest }
    )

    edge(respondFallbackMessage forwardTo nodeFinish)

    // After creating task, classify the joke details
    edge(
        createTask forwardTo classifyJokeRequest
            transformed { agentInput<A2AMessage>().content() }
    )

    // Joke classification: Ask for clarification if needed
    edge(
        classifyJokeRequest forwardTo askMoreInfo
            transformed { it.getOrThrow().structure }
            onIsInstance JokeRequestClassification.NeedsClarification::class
    )
    // Joke classification: Generate joke if we have all details
    edge(
        classifyJokeRequest forwardTo generateJoke
            transformed { it.getOrThrow().structure }
            onIsInstance JokeRequestClassification.Ready::class
    )

    // After asking for info, wait for user response (finish this iteration)
    edge(askMoreInfo forwardTo nodeFinish)

    // After generating joke, send it as an artifact
    edge(generateJoke forwardTo respondWithJoke)
    edge(respondWithJoke forwardTo nodeFinish)
}

private fun A2AMessage.content(): String {
    return parts.filterIsInstance<TextPart>().joinToString(separator = "\n") { it.text }
}

// --- Structured Output Models ---

@Serializable
@LLMDescription("Initial incoming user message classification, to determine if this is a joke request or not.")
private data class UserRequestClassification(
    @property:LLMDescription("Whether the incoming message is a joke request or not")
    val isJokeRequest: Boolean,
    @property:LLMDescription(
        "In case the message is not a joke request, polite reply to the user that the agent cannot assist." +
            "Default is empty"
    )
    val response: String = "",
)

@LLMDescription("The classification of the joke request")
@Serializable
@SerialName("JokeRequestClassification")
private sealed interface JokeRequestClassification {
    @Serializable
    @SerialName("NeedsClarification")
    @LLMDescription("The joke request needs clarification")
    data class NeedsClarification(
        @property:LLMDescription("The question that needs clarification")
        val question: String
    ) : JokeRequestClassification

    @LLMDescription("The joke request is ready to be processed")
    @Serializable
    @SerialName("Ready")
    data class Ready(
        @property:LLMDescription("The joke subject")
        val subject: String,
        @property:LLMDescription("The joke target audience")
        val targetAudience: String,
        @property:LLMDescription("Whether the swearing is allowed in the joke")
        val isSwearingAllowed: Boolean,
    ) : JokeRequestClassification
}

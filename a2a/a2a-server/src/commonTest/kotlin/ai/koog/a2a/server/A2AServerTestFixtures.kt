package ai.koog.a2a.server

import ai.koog.a2a.model.AgentCapabilities
import ai.koog.a2a.model.AgentCard
import ai.koog.a2a.model.AgentInterface
import ai.koog.a2a.model.CancelTaskRequest
import ai.koog.a2a.model.Event
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.SendMessageRequest
import ai.koog.a2a.model.SubscribeToTaskRequest
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.model.TransportProtocol
import ai.koog.a2a.server.agent.AgentExecutor
import ai.koog.a2a.server.turn.IdGenerator
import ai.koog.a2a.server.turn.RequestContext
import ai.koog.a2a.server.turn.TurnEventProcessor
import ai.koog.a2a.transport.ServerCallContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// Shared fixtures of the A2AServer-level tests.

internal const val TASK_ID = "task-1"
internal const val CONTEXT_ID = "context-1"
internal val TIMESTAMP = Instant.parse("2025-01-01T10:00:00Z")
internal val TEST_TIMEOUT = 30.seconds

internal fun testAgentCard(capabilities: AgentCapabilities = AgentCapabilities(), name: String = "Test agent") =
    AgentCard(
        name = name,
        description = "Test agent",
        supportedInterfaces = listOf(
            AgentInterface(
                url = "http://localhost/",
                protocolBinding = TransportProtocol.JSONRPC,
                protocolVersion = "1.0"
            )
        ),
        version = "1.0.0",
        defaultInputModes = listOf("text"),
        defaultOutputModes = listOf("text"),
        skills = emptyList(),
        capabilities = capabilities,
    )

/** An executor for tests that must not reach the agent. */
internal object UnusedAgentExecutor : AgentExecutor {
    override suspend fun execute(context: RequestContext<SendMessageRequest>, eventProcessor: TurnEventProcessor) =
        error("Not expected to be called")
}

internal class FixedIdGenerator(
    private val contextId: String = CONTEXT_ID,
    private val messageId: String? = null,
) : IdGenerator {
    override suspend fun generateContextId(message: Message): String = contextId
    override suspend fun generateTaskId(message: Message): String = TASK_ID
    override suspend fun generateMessageId(taskId: String, contextId: String): String =
        messageId ?: super.generateMessageId(taskId, contextId)
}

internal fun interface AgentScript {
    suspend fun run(context: RequestContext<SendMessageRequest>, processor: TurnEventProcessor)
}

internal fun interface CancelHandler {
    suspend fun cancel(processor: TurnEventProcessor, agentJob: Deferred<Unit>?)
}

/**
 * Runs the [AgentScript] registered for the text of the incoming message, and the [cancelHandler] on cancellation.
 * Records the request contexts it has been executed with.
 */
internal class ScriptedAgentExecutor(
    private val scripts: Map<String, AgentScript>,
    private val cancelHandler: CancelHandler? = null,
) : AgentExecutor {
    val executed = mutableListOf<RequestContext<SendMessageRequest>>()

    override suspend fun execute(context: RequestContext<SendMessageRequest>, eventProcessor: TurnEventProcessor) {
        executed += context
        val text = context.params.message.parts.filterIsInstance<TextPart>().joinToString(" ") { it.text }
        (scripts[text] ?: error("No script for '$text'")).run(context, eventProcessor)
    }

    override suspend fun cancel(
        context: RequestContext<CancelTaskRequest>,
        eventProcessor: TurnEventProcessor,
        agentJob: Deferred<Unit>?,
    ) {
        val handler = cancelHandler ?: return super.cancel(context, eventProcessor, agentJob)
        handler.cancel(eventProcessor, agentJob)
    }
}

internal suspend fun Flow<Event>.collectExpectingFailure(): Pair<Throwable, List<Event>> {
    val received = mutableListOf<Event>()
    val failure = runCatching { collect { received += it } }.exceptionOrNull()
    return assertNotNull(failure, "Collection was expected to fail") to received
}

internal class Subscription(val events: MutableList<Event>, val firstEvent: CompletableDeferred<Unit>, val job: Job)

/** Subscribes to [TASK_ID] in a new coroutine, recording the received events. */
internal fun CoroutineScope.subscribe(
    server: A2AServer,
    onEvent: suspend (Event) -> Unit = {},
): Subscription {
    val events = mutableListOf<Event>()
    val firstEvent = CompletableDeferred<Unit>()

    val job = launch {
        server.onSubscribeToTask(SubscribeToTaskRequest(TASK_ID), ServerCallContext()).collect {
            events += it
            firstEvent.complete(Unit)
            onEvent(it)
        }
    }

    return Subscription(events, firstEvent, job)
}

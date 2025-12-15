package ai.koog.agents.core.feature.model.events

import ai.koog.agents.core.agent.execution.AgentExecutionInfo
import ai.koog.agents.core.feature.model.AIAgentError
import ai.koog.agents.utils.ModelInfo
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.streaming.StreamFrame
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Represents an event triggered when a language model (LLM) streaming operation is starting.
 *
 * This event holds metadata related to the initiation of the LLM streaming process, including
 * details about the run, the input prompt, the model used, and the tools involved.
 *
 * @property eventId A unique identifier for the event or a group of events;
 * @property executionInfo Provides contextual information about the execution associated with this event.
 * @property runId Unique identifier for the LLM run;
 * @property prompt The input prompt provided for the LLM operation;
 * @property model The description of the LLM model used during the call. Use the format: 'llm_provider:model_id';
 * @property tools A list of associated tools or resources that are part of the operation;
 * @property timestamp The time when the event occurred, represented in epoch milliseconds.
 */
@Serializable
public data class LLMStreamingStartingEvent(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val runId: String,
    val prompt: Prompt,
    val model: ModelInfo,
    val tools: List<String>,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent() {

    /**
     * @deprecated Use constructor with executionInfo parameter and model parameter of type [ModelInfo]:
     *             LLMStreamingStartingEvent(executionInfo, runId, prompt, model, tools, timestamp)
     */
    @Deprecated(
        message = "Please use constructor with executionInfo parameter and model parameter of type [ModelInfo]: LLMStreamingStartingEvent(executionInfo, runId, prompt, model, tools, timestamp)",
        replaceWith = ReplaceWith("LLMStreamingStartingEvent(executionInfo, runId, prompt, model, tools, timestamp)")
    )
    public constructor(
        runId: String,
        prompt: Prompt,
        model: String,
        tools: List<String>,
        timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : this(
        eventId = LLMStreamingStartingEvent::class.simpleName.toString(),
        executionInfo = AgentExecutionInfo(
            parent = null,
            partName = LLMStreamingStartingEvent::class.simpleName.toString(),
        ),
        runId = runId,
        prompt = prompt,
        model = ModelInfo.fromString(model),
        tools = tools,
        timestamp = timestamp
    )
}

/**
 * Event representing the receipt of a streaming frame from a Language Learning Model (LLM).
 *
 * This event occurs as part of the streaming interaction with the LLM, where individual
 * frames of data are sent incrementally. The event contains details about the specific
 * frame received, as well as metadata related to the event's timing and identity.
 *
 * @property eventId A unique identifier for the event or a group of events;
 * @property executionInfo Provides contextual information about the execution associated with this event.
 * @property runId The unique identifier for the LLM run or session associated with this event;
 * @property frame The frame data received as part of the streaming response. This can include textual
 *                 content, tool invocations, or signaling the end of the stream;
 * @property timestamp The timestamp of when the event was created, represented in milliseconds since the Unix epoch.
 */
@Serializable
public data class LLMStreamingFrameReceivedEvent(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val runId: String,
    val prompt: Prompt,
    val model: ModelInfo,
    val frame: StreamFrame,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent() {

    /**
     * @deprecated Use constructor with executionInfo parameter
     */
    @Deprecated(
        message = "Please use constructor with executionInfo parameter: LLMStreamingFrameReceivedEvent(executionInfo, runId, prompt, model, frame, timestamp)",
        replaceWith = ReplaceWith("LLMStreamingFrameReceivedEvent(executionInfo, runId, prompt, model, frame, timestamp)")
    )
    public constructor(
        runId: String,
        frame: StreamFrame,
        timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : this(
        eventId = LLMStreamingFrameReceivedEvent::class.simpleName.toString(),
        executionInfo = AgentExecutionInfo(
            parent = null,
            partName = LLMStreamingFrameReceivedEvent::class.simpleName.toString(),
        ),
        runId = runId,
        prompt = Prompt(emptyList(), ""),
        model = ModelInfo("", ""),
        frame = frame,
        timestamp = timestamp
    )
}

/**
 * Represents an event indicating a failure in the streaming process of a Language Learning Model (LLM).
 *
 * This event captures details of the failure encountered during the streaming operation.
 * It includes information such as the unique identifier of the operation run, a detailed
 * error description, and inherits common properties such as event ID and timestamp.
 *
 * @property eventId A unique identifier for the event or a group of events;
 * @property executionInfo Provides contextual information about the execution associated with this event.
 * @property runId A unique identifier representing the specific operation or run in which the failure occurred;
 * @property error An instance of [AIAgentError], containing information about the error encountered, including its
 *                 message, stack trace, and cause, if available;
 * @property timestamp A timestamp indicating when the event occurred, represented in milliseconds since the Unix epoch.
 */
@Serializable
public data class LLMStreamingFailedEvent(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val runId: String,
    val prompt: Prompt,
    val model: ModelInfo,
    val error: AIAgentError,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent() {

    /**
     * @deprecated Use constructor with executionInfo parameter
     */
    @Deprecated(
        message = "Please use constructor with executionInfo parameter: LLMStreamingFailedEvent(executionInfo, runId, prompt, model, error, timestamp)",
        replaceWith = ReplaceWith("LLMStreamingFailedEvent(executionInfo, runId, prompt, model, error, timestamp)")
    )
    public constructor(
        runId: String,
        error: AIAgentError,
        timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : this(
        eventId = LLMStreamingFailedEvent::class.simpleName.toString(),
        executionInfo = AgentExecutionInfo(
            parent = null,
            partName = LLMStreamingFailedEvent::class.simpleName.toString(),
        ),
        runId = runId,
        prompt = Prompt(emptyList(), ""),
        model = ModelInfo("", ""),
        error = error,
        timestamp = timestamp
    )
}

/**
 * Represents an event that occurs when the streaming process of a Large Language Model (LLM) call is completed.
 *
 * @property eventId A unique identifier for the event or a group of events;
 * @property executionInfo Provides contextual information about the execution associated with this event.
 * @property runId The unique identifier of the LLM run;
 * @property prompt The prompt associated with the LLM call;
 * @property model The description of the LLM model used during the call. Use the format: 'llm_provider:model_id';
 * @property tools A list of tools used or invoked during the LLM call;
 * @property timestamp The timestamp indicating when the event occurred, represented in milliseconds since the epoch, defaulting to the current system time.
 */
@Serializable
public data class LLMStreamingCompletedEvent(
    override val eventId: String,
    override val executionInfo: AgentExecutionInfo,
    val runId: String,
    val prompt: Prompt,
    val model: ModelInfo,
    val tools: List<String>,
    override val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
) : DefinedFeatureEvent() {

    /**
     * @deprecated Use constructor with executionInfo parameter and model parameter of type [ModelInfo]:
     *             LLMStreamingCompletedEvent(executionInfo, runId, prompt, model, tools, timestamp)
     */
    @Deprecated(
        message = "Please use constructor with executionInfo parameter and model parameter of type [ModelInfo]: LLMStreamingCompletedEvent(executionInfo, runId, prompt, model, tools, timestamp)",
        replaceWith = ReplaceWith("LLMStreamingCompletedEvent(executionInfo, runId, prompt, model, tools, timestamp)")
    )
    public constructor(
        runId: String,
        prompt: Prompt,
        model: String,
        tools: List<String>,
        timestamp: Long = Clock.System.now().toEpochMilliseconds()
    ) : this(
        eventId = LLMStreamingCompletedEvent::class.simpleName.toString(),
        executionInfo = AgentExecutionInfo(
            parent = null,
            partName = LLMStreamingCompletedEvent::class.simpleName.toString(),
        ),
        runId = runId,
        prompt = prompt,
        model = ModelInfo.fromString(model),
        tools = tools,
        timestamp = timestamp
    )
}

package ai.koog.a2a.model

import ai.koog.a2a.serialization.SendMessageResponseSerializer
import ai.koog.a2a.serialization.StreamResponseSerializer
import kotlinx.serialization.Serializable

/**
 * Represents the response for the `SendMessage` method.
 * This sealed hierarchy models the protocol-defined `oneof` `payload`.
 */
@Serializable(with = SendMessageResponseSerializer::class)
public sealed interface SendMessageResponse {
    @Serializable
    public data class TaskResponse(val task: Task) : SendMessageResponse

    @Serializable
    public data class MessageResponse(val message: Message) : SendMessageResponse
}

/**
 * Convert `oneof` representation [SendMessageResponse] to an actual instance of [ResponseEvent].
 */
public fun SendMessageResponse.toResponseEvent(): ResponseEvent = when (this) {
    is SendMessageResponse.TaskResponse -> task
    is SendMessageResponse.MessageResponse -> message
}

/**
 * Convert [ResponseEvent] to `oneof` representation [SendMessageResponse].
 */
public fun ResponseEvent.toSendMessageResponse(): SendMessageResponse = when (this) {
    is Task -> SendMessageResponse.TaskResponse(this)
    is Message -> SendMessageResponse.MessageResponse(this)
}

/**
 * Represents the response for the `SendStreamingMessage` method.
 * This sealed hierarchy models the protocol-defined `oneof` `payload`.
 */
@Serializable(with = StreamResponseSerializer::class)
public sealed interface StreamResponse {
    @Serializable
    public data class TaskResponse(val task: Task) : StreamResponse

    @Serializable
    public data class MessageResponse(val message: Message) : StreamResponse

    @Serializable
    public data class TaskStatusUpdateEventResponse(val statusUpdate: TaskStatusUpdateEvent) : StreamResponse

    @Serializable
    public data class TaskArtifactUpdateEventResponse(val artifactUpdate: TaskArtifactUpdateEvent) : StreamResponse
}

/**
 * Convert `oneof` representation [StreamResponse] to an actual instance of [Event].
 */
public fun StreamResponse.toEvent(): Event = when (this) {
    is StreamResponse.TaskResponse -> task
    is StreamResponse.MessageResponse -> message
    is StreamResponse.TaskStatusUpdateEventResponse -> statusUpdate
    is StreamResponse.TaskArtifactUpdateEventResponse -> artifactUpdate
}

/**
 * Convert [Event] to `oneof` representation [StreamResponse].
 */
public fun Event.toStreamResponse(): StreamResponse = when (this) {
    is Task -> StreamResponse.TaskResponse(this)
    is Message -> StreamResponse.MessageResponse(this)
    is TaskStatusUpdateEvent -> StreamResponse.TaskStatusUpdateEventResponse(this)
    is TaskArtifactUpdateEvent -> StreamResponse.TaskArtifactUpdateEventResponse(this)
}

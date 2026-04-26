package ai.koog.agents.core.environment

import ai.koog.agents.core.feature.model.AIAgentError
import ai.koog.agents.core.feature.model.toAgentError
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Represents the possible result types for a tool operation.
 */
@Serializable
public sealed class ToolResultKind {

    /**
     * Represents a successful result in the context of a tool operation.
     */
    @Serializable
    public object Success : ToolResultKind()

    /**
     * Represents a failure result in the context of a tool operation.
     *
     * @property error The exception that caused the failure, or `null` if no exception is available.
     *                 Encoded as an [AIAgentError] over the wire.
     */
    @Serializable
    public data class Failure(
        @Serializable(with = ThrowableAsAgentErrorSerializer::class)
        public val error: Throwable?,
    ) : ToolResultKind()

    /**
     * Represents a validation error result in the context of a tool operation.
     *
     * @property error The exception describing the validation failure.
     *                 Encoded as an [AIAgentError] over the wire.
     */
    @Serializable
    public data class ValidationError(
        @Serializable(with = ThrowableAsAgentErrorSerializer::class)
        public val error: Throwable,
    ) : ToolResultKind()
}

/**
 * Serializes a [Throwable] field by encoding it through [AIAgentError]
 *
 * The original [AIAgentError] is used to preserve the original [Throwable] to use it in agent event context.
 *
 * Note: Decoding is intentionally lossy.
 *      [ReceivedToolResult] is not deserialized anywhere in the codebase today,
 *      and full round-trip fidelity can be added (via a wrapper) if a real decoded use case appears.
 *      It returns a plain [Throwable] with the original message but no stack/cause/type.
 */
internal object ThrowableAsAgentErrorSerializer : KSerializer<Throwable> {
    private val delegate = AIAgentError.serializer()
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Throwable) {
        delegate.serialize(encoder, value.toAgentError())
    }

    override fun deserialize(decoder: Decoder): Throwable =
        Throwable(delegate.deserialize(decoder).message)
}

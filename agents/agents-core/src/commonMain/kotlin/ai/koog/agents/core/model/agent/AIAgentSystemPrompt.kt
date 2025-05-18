package ai.koog.agents.core.model.agent

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Represents a system-level prompt used within an agent-based system.
 * This class is designed to be serialized and deserialized using Kotlin serialization.
 * The prompt is encapsulated as a non-mutable string.
 */
@Serializable(with = AIAgentSystemPrompt.Serializer::class)
public class AIAgentSystemPrompt(private val prompt: String) {
    public object Serializer : KSerializer<AIAgentSystemPrompt> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("SystemPrompt", PrimitiveKind.STRING)

        override fun serialize(
            encoder: Encoder,
            value: AIAgentSystemPrompt
        ) {
            encoder.encodeString(value.prompt)
        }

        override fun deserialize(decoder: Decoder): AIAgentSystemPrompt = AIAgentSystemPrompt(decoder.decodeString())
    }
}

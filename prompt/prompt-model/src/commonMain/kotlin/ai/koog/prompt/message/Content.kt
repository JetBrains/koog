package ai.koog.prompt.message

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 *
 */
public sealed interface Content {
    /** The simple text content of the message. */
    public fun text(): String

    /**
     * The contents of the message.
     */
    @Serializable
    @JvmInline
    public value class Text(public val value: String) : Content {
        override fun text(): String = value
    }

    /**
     * An array of content parts with a defined type.
     * Supported options differ based on the model being used to generate the response.
     * Can contain text, image or audio inputs.
     */
    @Serializable
    @JvmInline
    public value class Parts(public val value: List<ContentPart>) : Content {
        override fun text(): String = value
            .filterIsInstance<ContentPart.Text>()
            .joinToString("\n") { it.text }
    }
}

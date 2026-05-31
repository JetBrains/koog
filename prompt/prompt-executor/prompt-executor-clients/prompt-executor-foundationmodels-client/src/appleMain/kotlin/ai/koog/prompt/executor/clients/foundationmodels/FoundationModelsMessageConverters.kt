package ai.koog.prompt.executor.clients.foundationmodels

import ai.koog.prompt.Prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.utils.time.KoogClock
import kotlin.time.ExperimentalTime

/** Flattened Foundation Models input: system text becomes [instructions], the rest becomes [text]. */
internal data class FoundationModelsInput(val instructions: String?, val text: String)

/**
 * Flattens a [Prompt] into Foundation Models inputs:
 * - all [Message.System] text → [FoundationModelsInput.instructions] (null when blank/absent);
 * - every other message's text parts → a single newline-joined [FoundationModelsInput.text].
 */
internal fun Prompt.toFoundationModelsInput(): FoundationModelsInput {
    fun Message.textOf(): String =
        parts.filterIsInstance<MessagePart.Text>().joinToString("\n") { it.text }

    val instructions = messages.filterIsInstance<Message.System>()
        .joinToString("\n") { it.textOf() }
        .ifBlank { null }

    val text = messages.filterNot { it is Message.System }
        .map { it.textOf() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")

    return FoundationModelsInput(instructions, text)
}

/**
 * Wraps a Foundation Models response string in a [Message.Assistant].
 * Always emits exactly one [MessagePart.Text] (empty string for empty output) so the
 * single-run agent loop's `onTextMessage` edge always fires and the agent terminates.
 */
@OptIn(ExperimentalTime::class)
internal fun foundationModelsAssistantMessage(content: String): Message.Assistant =
    Message.Assistant(
        parts = listOf(MessagePart.Text(content)),
        metaInfo = ResponseMetaInfo.create(KoogClock.System),
    )

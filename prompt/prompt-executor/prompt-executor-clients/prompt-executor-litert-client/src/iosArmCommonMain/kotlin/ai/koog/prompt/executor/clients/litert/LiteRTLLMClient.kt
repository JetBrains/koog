package ai.koog.prompt.executor.clients.litert

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.LLMClientException
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.time.KoogClock
import io.github.johnrocky.litertlm.Backend
import io.github.johnrocky.litertlm.LiteRtLmException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import kotlin.time.ExperimentalTime

/**
 * Configuration for [LiteRTLLMClient] on iOS.
 *
 * @property defaultModel The [LLModel] used when no model is explicitly specified.
 *   Defaults to [LiteRTLLModels.FunctionGemma].
 * @property modelsPath Absolute path where `.litertlm` model files are stored.
 *   Defaults to the app's Documents directory.
 * @property cacheDir Absolute path of a writable directory for compiled caches.
 *   When `null`, LiteRT defaults to the directory of the model file.
 * @property backend LiteRT compute backend: Metal with `Backend.GPU()`, or `Backend.CPU()`, the default.
 *   The simulator has only the CPU.
 * @property clock Clock instance used for response timestamp metadata.
 * @property minAvailableMemoryBytes A request fails below this much available memory, instead of the system
 *   killing the app during the load. `null` uses half the model file's size. 0 turns the check off.
 */
public data class LiteRTClientConfig(
    val defaultModel: LLModel = LiteRTLLModels.FunctionGemma,
    val modelsPath: String = documentsDirectory(),
    val cacheDir: String? = null,
    val backend: Backend = Backend.CPU(),
    @OptIn(ExperimentalTime::class)
    val clock: KoogClock = KoogClock.System,
    val minAvailableMemoryBytes: Long? = null,
)

private fun documentsDirectory(): String =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String

/**
 * [LLMClient] implementation that runs inference on-device using the LiteRT runtime.
 *
 * Delegates to an internal [LiteRTLLMSession] which manages the LiteRT engine and conversation
 * lifecycle. Tool execution is performed by the koog agent framework, not by LiteRT.
 * A failure of the runtime is an [LLMClientException] with the `LiteRtLmException` as its cause.
 *
 * The app links Google's runtime itself: see this module's documentation.
 *
 * @param config Configuration specifying model paths, backend, and runtime options.
 */
public class LiteRTLLMClient(config: LiteRTClientConfig) : LLMClient() {
    private val session = LiteRTLLMSession(config)

    /**
     * Runs inference for [prompt] using [model] and returns the model's response message.
     */
    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Message.Assistant {
        try {
            return session.execute(prompt, model, tools)
        } catch (e: LiteRtLmException) {
            throw LLMClientException(clientName, e.message, e)
        }
    }

    /**
     * Runs inference for [prompt] using [model] and emits the response as it is generated.
     * Cancelling the collector stops the model.
     */
    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> =
        session.executeStreaming(prompt, model, tools).catch { e ->
            throw if (e is LiteRtLmException) LLMClientException(clientName, e.message, e) else e
        }

    /**
     * Not supported — content moderation is unavailable for on-device models.
     *
     * @throws UnsupportedOperationException always.
     */
    override suspend fun moderate(
        prompt: Prompt,
        model: LLModel
    ): ModerationResult {
        throw UnsupportedOperationException("Moderation is not supported for on-device models")
    }

    /** Returns [LiteRTLLMProvider] as the provider for all models served by this client. */
    override fun llmProvider(): LLMProvider = LiteRTLLMProvider

    /** Closes the underlying [LiteRTLLMSession] and releases all LiteRT resources. */
    override fun close() {
        kotlinx.coroutines.runBlocking { session.close() }
    }
}

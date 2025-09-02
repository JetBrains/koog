package ai.koog.prompt.executor.model

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.CoroutineContext

/**
 * A wrapper for [PromptExecutor] that exposes a Java-friendly API.
 *
 * @property delegate The [PromptExecutor] instance to wrap.
 * @constructor Creates a new [JavaPromptExecutor] instance.
 */
@ApiStatus.Experimental
public class JavaPromptExecutor(
    private val delegate: PromptExecutor
) : PromptExecutor by delegate {

    /**
     * Executes the given prompt asynchronously using the specified model and tools.
     *
     * @param prompt The prompt input to be executed.
     * @param model The language model to process the prompt.
     * @param tools An optional list of tool descriptors that may assist in processing the prompt.
     * @param coroutineContext The coroutine context to execute the operation, defaulting to `Dispatchers.IO`.
     * @return A CompletableFuture containing a list of response messages produced by the execution.
     */
    @JvmOverloads
    public fun executeAsync(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor> = emptyList(),
        coroutineContext: CoroutineContext = Dispatchers.IO
    ): CompletableFuture<List<Message.Response>> = CoroutineScope(coroutineContext).future {
        execute(prompt, model, tools)
    }
}

/**
 * Converts a [PromptExecutor] instance to a [JavaPromptExecutor] instance.
 */
public fun PromptExecutor.asJava(): JavaPromptExecutor = JavaPromptExecutor(delegate = this)

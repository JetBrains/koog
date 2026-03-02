package ai.koog.agents.chatMemory

import ai.koog.agents.testing.tools.MockLLMBuilder
import ai.koog.prompt.executor.model.PromptExecutor
import kotlin.time.Clock

/**
 * Helper functions to simplify Java test interop with Kotlin APIs
 * that use types not easily accessible from Java in a multiplatform project.
 */
object JavaTestHelpers {

    /**
     * Creates a [MockLLMBuilder] using the system clock.
     * This avoids Java tests needing to import [kotlinx.datetime.Clock] directly.
     */
    @JvmStatic
    fun createMockLLMBuilder(): MockLLMBuilder = MockLLMBuilder(Clock.System)

    /**
     * Creates a [PromptExecutor] by configuring a [MockLLMBuilder] via a Java-friendly callback.
     */
    @JvmStatic
    fun createMockExecutor(configure: java.util.function.Consumer<MockLLMBuilder>): PromptExecutor {
        val builder = createMockLLMBuilder()
        configure.accept(builder)
        return builder.build()
    }
}

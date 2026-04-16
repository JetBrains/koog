package ai.koog.agents.chatMemory

import ai.koog.agents.testing.tools.MockExecutorDSLBuilder
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.serialization.jackson.JacksonSerializer
import java.util.function.Consumer
import kotlin.time.Clock

/**
 * Helper functions to simplify Java test interop with Kotlin APIs
 * that use types not easily accessible from Java in a multiplatform project.
 */
object JavaTestHelpers {
    private val serializer = JacksonSerializer()

    /**
     * Creates a [PromptExecutor] by configuring a [MockExecutorDSLBuilder] via a Java-friendly callback.
     */
    @JvmStatic
    fun createMockExecutor(configure: Consumer<MockExecutorDSLBuilder>): PromptExecutor {
        val builder = MockExecutorDSLBuilder(Clock.System, serializer)
        configure.accept(builder)
        return builder.build()
    }
}

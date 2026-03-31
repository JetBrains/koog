package ai.koog.spring.ai.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import org.slf4j.Logger
import org.springframework.core.task.AsyncTaskExecutor

/**
 * Dispatcher settings for blocking Spring AI calls.
 *
 * - [Auto]: Automatically detect the best dispatcher. Uses Spring's `AsyncTaskExecutor` when
 *   available (e.g. virtual-thread executor), otherwise falls back to `Dispatchers.IO`.
 * - [IO]: Use [kotlinx.coroutines.Dispatchers.IO], optionally limited to [IO.parallelism] threads.
 */
public sealed interface DispatcherProperties {

    /**
     * Automatically detect the best dispatcher.
     *
     * When Spring Boot's `spring.threads.virtual.enabled=true` is set, an
     * [org.springframework.core.task.AsyncTaskExecutor] backed by virtual threads
     * is available in the application context. In [Auto] mode the dispatcher is
     * derived from that executor, so users only need the standard Spring Boot
     * property to opt into virtual threads.
     *
     * Falls back to [kotlinx.coroutines.Dispatchers.IO] when no such executor is present.
     *
     * **Warning:** When `spring.threads.virtual.enabled=false` (the default before
     * Spring Boot 3.2), the application task executor is typically a bounded
     * `ThreadPoolTaskExecutor` (8 core threads by default). Wrapping it as a
     * coroutine dispatcher means all blocking calls share the same thread pool
     * used by `@Async`, scheduled tasks, and web MVC async handlers. Under load this
     * can cause thread starvation or deadlocks. In such setups, prefer [IO] or enable
     * virtual threads.
     */
    public data object Auto : DispatcherProperties

    /**
     * Use [kotlinx.coroutines.Dispatchers.IO].
     *
     * When [parallelism] is greater than 0, uses
     * `Dispatchers.IO.limitedParallelism(parallelism)` to cap concurrency.
     *
     * @property parallelism Maximum parallelism for the dispatcher. When `null` or 0,
     *   the unbounded `Dispatchers.IO` is used.
     */
    public data class IO(
        val parallelism: Int? = null
    ) : DispatcherProperties
}

/**
 * Spring Boot–bindable configuration that maps to a [DispatcherProperties] sealed variant.
 *
 * Properties:
 * - `type` – `AUTO` (default) or `IO`.
 * - `parallelism` – only meaningful when `type = IO`.
 *
 * @see DispatcherProperties
 */
public data class DispatcherConfig(
    val type: DispatcherType = DispatcherType.AUTO,
    val parallelism: Int = 0,
) {
    /**
     * Converts this bindable configuration into the corresponding [DispatcherProperties] variant.
     */
    public fun toDispatcherProperties(): DispatcherProperties = when (type) {
        DispatcherType.AUTO -> DispatcherProperties.Auto
        DispatcherType.IO -> DispatcherProperties.IO(parallelism.takeIf { it > 0 })
    }
}

/**
 * Dispatcher type for blocking Spring AI calls.
 */
public enum class DispatcherType {
    AUTO,
    IO,
}

/**
 * Resolves a [kotlinx.coroutines.CoroutineDispatcher] from the given [DispatcherConfig] and
 * optional [org.springframework.core.task.AsyncTaskExecutor].
 *
 * This is the shared implementation behind every `koogSpringAi*Dispatcher` bean factory method.
 *
 * @param dispatcherConfig the dispatcher configuration bound from Spring Boot properties
 * @param asyncTaskExecutor the Spring `AsyncTaskExecutor` if available, or `null`
 * @param logger the SLF4J logger to use for informational messages
 * @param componentName a human-readable component label used in log messages
 *   (e.g. `"Koog Spring AI Chat"`)
 * @return the resolved [kotlinx.coroutines.CoroutineDispatcher]
 */
public fun resolveDispatcher(
    dispatcherConfig: DispatcherConfig,
    asyncTaskExecutor: AsyncTaskExecutor?,
    logger: Logger,
    componentName: String,
): CoroutineDispatcher {
    return when (val dispatcher = dispatcherConfig.toDispatcherProperties()) {
        is DispatcherProperties.Auto -> {
            if (asyncTaskExecutor != null) {
                logger.info("$componentName: using Spring AsyncTaskExecutor as dispatcher")
                asyncTaskExecutor.asCoroutineDispatcher()
            } else {
                logger.info("$componentName: no AsyncTaskExecutor found, falling back to Dispatchers.IO")
                Dispatchers.IO
            }
        }

        is DispatcherProperties.IO -> {
            val parallelism = dispatcher.parallelism
            if (parallelism != null && parallelism > 0) {
                logger.info("$componentName: using Dispatchers.IO.limitedParallelism($parallelism)")
                Dispatchers.IO.limitedParallelism(parallelism)
            } else {
                logger.info("$componentName: using Dispatchers.IO")
                Dispatchers.IO
            }
        }
    }
}

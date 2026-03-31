package ai.koog.spring.ai.common

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

package ai.koog.agents.core.agent.config

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Property delegate for tool resource allocation.
 * Provides type-safe, lazy resource management per tool category.
 */
public interface ToolResourceDelegate : ReadOnlyProperty<ToolCategories, ToolResourceHandle> {
    public companion object {
        /**
         * No resource limits - original unlimited behavior.
         */
        public fun unlimited(): ToolResourceDelegate = UnlimitedDelegate
        
        /**
         * Fixed concurrency limit with optional custom dispatcher.
         */
        public fun limited(
            maxConcurrency: Int, 
            dispatcher: CoroutineDispatcher = Dispatchers.Default
        ): ToolResourceDelegate = LimitedDelegate(maxConcurrency, dispatcher)
        
        /**
         * Shared resource pool across multiple tool categories.
         */
        public fun shared(semaphore: Semaphore, dispatcher: CoroutineDispatcher = Dispatchers.Default): ToolResourceDelegate =
            SharedDelegate(semaphore, dispatcher)
    }
}

/**
 * Handle for tool resource management - encapsulates semaphore and dispatcher access.
 */
public data class ToolResourceHandle(
    public val semaphore: Semaphore?,
    public val dispatcher: CoroutineDispatcher
)

/**
 * Unlimited resource delegate - no concurrency limits.
 */
private object UnlimitedDelegate : ToolResourceDelegate {
    private val handle = ToolResourceHandle(semaphore = null, dispatcher = Dispatchers.Default)
    override fun getValue(thisRef: ToolCategories, property: KProperty<*>): ToolResourceHandle = handle
}

/**
 * Limited resource delegate - fixed concurrency with semaphore.
 */
private class LimitedDelegate(
    private val maxConcurrency: Int,
    private val dispatcher: CoroutineDispatcher
) : ToolResourceDelegate {
    init {
        require(maxConcurrency > 0) { "maxConcurrency must be positive, got: $maxConcurrency" }
    }
    
    private val handle by lazy {
        ToolResourceHandle(
            semaphore = Semaphore(maxConcurrency),
            dispatcher = dispatcher
        )
    }
    
    override fun getValue(thisRef: ToolCategories, property: KProperty<*>): ToolResourceHandle = handle
}

/**
 * Shared resource delegate - uses external semaphore.
 */
private class SharedDelegate(
    private val semaphore: Semaphore,
    private val dispatcher: CoroutineDispatcher
) : ToolResourceDelegate {
    private val handle = ToolResourceHandle(semaphore, dispatcher)
    override fun getValue(thisRef: ToolCategories, property: KProperty<*>): ToolResourceHandle = handle
}

/**
 * Type-safe tool category definitions using property delegates.
 * Users can extend this to define their own tool categories.
 */
public open class ToolCategories {
    /**
     * Network-bound tools (API calls, web scraping, etc.)
     * Conservative limit - users should adjust based on their API rate limits and infrastructure.
     */
    public open val network: ToolResourceHandle by ToolResourceDelegate.limited(
        maxConcurrency = 8,
        dispatcher = Dispatchers.IO
    )
    
    /**
     * CPU-intensive tools (computations, processing, etc.)
     * Conservative limit - users should adjust based on their hardware and workload.
     */
    public open val cpu: ToolResourceHandle by ToolResourceDelegate.limited(
        maxConcurrency = 2,
        dispatcher = Dispatchers.Default
    )
    
    /**
     * File/Database I/O tools
     * Conservative limit - users should adjust based on their storage and database capacity.
     */
    public open val io: ToolResourceHandle by ToolResourceDelegate.limited(
        maxConcurrency = 4,
        dispatcher = Dispatchers.IO
    )
    
    /**
     * Memory-heavy tools (ML inference, large data processing)
     * Very conservative limit - users should adjust based on available memory.
     */
    public open val memoryHeavy: ToolResourceHandle by ToolResourceDelegate.limited(
        maxConcurrency = 1,
        dispatcher = Dispatchers.Default
    )
    
    /**
     * Default category for unclassified tools.
     * Conservative limit - users should categorize tools appropriately for their use case.
     */
    public open val default: ToolResourceHandle by ToolResourceDelegate.limited(
        maxConcurrency = 4,
        dispatcher = Dispatchers.Default
    )
    
    /**
     * Unlimited resources - original behavior.
     */
    public open val unlimited: ToolResourceHandle by ToolResourceDelegate.unlimited()
}

/**
 * Configuration for agent resource management using type-safe delegates.
 *
 * @property toolCategories Tool category definitions with resource delegates
 * @property toolClassifier Function to classify tools into categories
 */
public data class AgentResourceConfig(
    public val toolCategories: ToolCategories = ToolCategories(),
    public val toolClassifier: (toolName: String) -> ToolResourceHandle = { toolCategories.default }
) {

    public companion object {
        /**
         * Basic default configuration with conservative limits.
         * Users should adjust based on their specific deployment needs.
         */
        public val DEFAULT: AgentResourceConfig = AgentResourceConfig()

        /**
         * Unlimited configuration - original behavior with no resource limits.
         */
        public val UNLIMITED: AgentResourceConfig = run {
            val unlimitedCategories = object : ToolCategories() {
                override val network by ToolResourceDelegate.unlimited()
                override val cpu by ToolResourceDelegate.unlimited()
                override val io by ToolResourceDelegate.unlimited()
                override val memoryHeavy by ToolResourceDelegate.unlimited()
                override val default by ToolResourceDelegate.unlimited()
            }
            AgentResourceConfig(
                toolCategories = unlimitedCategories,
                toolClassifier = { unlimitedCategories.unlimited }
            )
        }
    }
}
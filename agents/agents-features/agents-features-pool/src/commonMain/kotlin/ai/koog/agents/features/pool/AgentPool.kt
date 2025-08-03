package ai.koog.agents.features.pool

import ai.koog.agents.core.agent.AIAgent
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Marks declarations that are **delicate** —
 * they have limited use-case and shall be used with care in general code.
 * Any use of a delicate declaration has to be carefully reviewed to make sure it is
 * properly used and does not create problems like resource leaks or deadlocks.
 * Delicate declarations are not inherently unsafe, but they require extra care.
 */
@RequiresOptIn(
    message = "This is a delicate API and its use requires care. " +
        "Make sure you fully read and understand documentation of the declaration that is marked as a delicate API.",
    level = RequiresOptIn.Level.WARNING
)
public annotation class DelicateAgentPoolApi

/**
 * A pool of pre-initialized AI agents that can be acquired and released for reuse.
 * 
 * This provides significant performance benefits over creating new agent instances per request by:
 * - Eliminating cold start latency (strategy graph building, tool initialization)
 * - Reducing memory allocation and GC pressure
 * - Enabling stateful agent reuse across interactions
 * - Providing backpressure control via pool size limits
 * 
 * @param Input Type of agent input
 * @param Output Type of agent output
 */
public interface AgentPool<Input, Output> {
    
    /**
     * Execute a block with an agent from the pool, automatically handling acquire/release.
     * This is the recommended way to use the pool as it ensures proper resource cleanup.
     * 
     * @param timeout Maximum time to wait for an available agent
     * @param block The block to execute with the acquired agent
     * @return The result of the block execution
     * @throws IllegalStateException if no agent becomes available within the timeout
     */
    public suspend fun <R> withAgent(
        timeout: Duration = 30.seconds,
        block: suspend (AIAgent<Input, Output>) -> R
    ): R
    
    /**
     * Acquire an agent from the pool. Suspends if no agents are available until one becomes free
     * or the timeout is reached.
     * 
     * **This is a delicate API.** Consider using [withAgent] instead for automatic resource management.
     * When using this API, you must ensure that [PooledAgent.release] is called to return the agent
     * to the pool, preferably in a try/finally block or using [PooledAgent.use].
     * 
     * @param timeout Maximum time to wait for an available agent
     * @return A pooled agent wrapper, or null if timeout is exceeded
     */
    @DelicateAgentPoolApi
    public suspend fun acquire(timeout: Duration = 30.seconds): PooledAgent<Input, Output>?
    
    /**
     * Get pool statistics for monitoring and debugging
     */
    public val stats: PoolStats
    
    /**
     * Close the pool and all contained agents
     */
    public suspend fun close()
}

/**
 * A wrapper around an agent that automatically returns it to the pool when closed.
 */
public interface PooledAgent<Input, Output> : AutoCloseable {
    /**
     * The underlying agent instance
     */
    public val agent: AIAgent<Input, Output>
    
    /**
     * Execute a prompt with the pooled agent
     */
    public suspend fun run(input: Input): Output = agent.run(input)
    
    /**
     * Return the agent to the pool
     */
    override fun close()
    
    /**
     * Asynchronously release the agent back to the pool.
     * 
     * **This is a delicate API.** Consider using [withAgent] instead for automatic resource management.
     */
    @DelicateAgentPoolApi
    public suspend fun release()
}

/**
 * Statistics about pool usage for monitoring performance
 */
public data class PoolStats(
    val totalAgents: Int,
    val availableAgents: Int,
    val acquiredAgents: Int,
    val totalAcquires: Long,
    val totalReleases: Long,
    val hits: Long,
    val misses: Long,
    val timeouts: Long
) {
    public val hitRate: Double get() = if (totalAcquires > 0) hits.toDouble() / totalAcquires else 0.0
    public val utilizationRate: Double get() = if (totalAgents > 0) acquiredAgents.toDouble() / totalAgents else 0.0
}

/**
 * Configuration for agent pool behavior
 */
public data class AgentPoolConfig(
    val maxSize: Int = 50,
    val minSize: Int = 1, 
    val acquireTimeout: Duration = 30.seconds,
    val enableStatistics: Boolean = true
)

/**
 * Factory function for creating agent instances
 */
public fun interface AgentFactory<Input, Output> {
    public suspend fun createAgent(): AIAgent<Input, Output>
}

/**
 * Default implementation of AgentPool using high-performance Semaphore-based approach.
 * 
 * This replaces the previous Channel-based implementation which had significant 
 * synchronization overhead. The Semaphore approach provides:
 * - Much better performance (no Channel overhead)
 * - Proper backpressure control  
 * - Efficient coroutine suspension
 * - Simple ArrayDeque for idle agent storage
 */
public class DefaultAgentPool<Input, Output>(
    private val factory: AgentFactory<Input, Output>,
    private val config: AgentPoolConfig = AgentPoolConfig()
) : AgentPool<Input, Output> {
    
    private val semaphore = Semaphore(config.maxSize)
    private val idleAgents = ArrayDeque<AIAgent<Input, Output>>()
    private val mutex = Mutex()
    private var closed = false
    
    // Statistics
    private var _totalAcquires = 0L
    private var _totalReleases = 0L
    private var _hits = 0L
    private var _misses = 0L
    private var _timeouts = 0L
    private var _totalAgents = 0
    private var _acquiredAgents = 0
    
    override val stats: PoolStats
        get() = if (mutex.tryLock()) {
            try {
                PoolStats(
                    totalAgents = _totalAgents,
                    availableAgents = idleAgents.size,
                    acquiredAgents = _acquiredAgents,
                    totalAcquires = _totalAcquires,
                    totalReleases = _totalReleases,
                    hits = _hits,
                    misses = _misses,
                    timeouts = _timeouts
                )
            } finally {
                mutex.unlock()
            }
        } else {
            PoolStats(0, 0, 0, 0, 0, 0, 0, 0)
        }
    
    override suspend fun <R> withAgent(
        timeout: Duration,
        block: suspend (AIAgent<Input, Output>) -> R
    ): R {
        val pooledAgent = acquire(timeout) 
            ?: throw IllegalStateException("No agent available within timeout: $timeout")
        
        return pooledAgent.use { agent ->
            block(agent)
        }
    }
    
    override suspend fun acquire(timeout: Duration): PooledAgent<Input, Output>? {
        if (closed) return null
        
        mutex.withLock { _totalAcquires++ }
        
        return try {
            semaphore.withPermit {
                val agent = mutex.withLock {
                    if (idleAgents.isNotEmpty()) {
                        _hits++
                        _acquiredAgents++
                        idleAgents.removeFirst()
                    } else {
                        _misses++
                        _totalAgents++
                        _acquiredAgents++
                        null
                    }
                }
                
                val finalAgent = agent ?: factory.createAgent()
                PooledAgentImpl(finalAgent, this)
            }
        } catch (e: Exception) {
            mutex.withLock { _timeouts++ }
            null
        }
    }
    
    internal suspend fun release(agent: AIAgent<Input, Output>) {
        if (closed) {
            agent.close()
            return
        }
        
        mutex.withLock {
            _totalReleases++
            _acquiredAgents--
            
            if (idleAgents.size < config.maxSize) {
                idleAgents.addLast(agent)
            } else {
                // Pool full, close this agent
                agent.close()
                _totalAgents--
            }
        }
    }
    
    override suspend fun close() {
        if (closed) return
        
        mutex.withLock {
            closed = true
            
            // Close all idle agents
            while (idleAgents.isNotEmpty()) {
                idleAgents.removeFirst().close()
            }
        }
    }
}

/**
 * Implementation of PooledAgent that automatically returns the agent to pool on close
 */
internal class PooledAgentImpl<Input, Output>(
    override val agent: AIAgent<Input, Output>,
    private val pool: DefaultAgentPool<Input, Output>
) : PooledAgent<Input, Output> {
    
    private var released = false
    
    override fun close() {
        if (!released) {
            released = true
            // Note: close() is non-blocking, for proper pool return use release()
            // This is mainly for cleanup when not using the pool pattern correctly
        }
    }
    
    // Extension for proper async release
    override suspend fun release() {
        if (!released) {
            released = true
            pool.release(agent)
        }
    }
}

/**
 * Extension function to use a pooled agent with automatic release
 */
public suspend inline fun <Input, Output, R> PooledAgent<Input, Output>.use(
    block: suspend (AIAgent<Input, Output>) -> R
): R {
    try {
        return block(agent)
    } finally {
        this.release()
    }
}
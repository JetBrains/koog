package ai.koog.agents.core.agent.context.element

import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KType

/**
 * Represents a coroutine context element that holds metadata specific to a particular node
 * participating in the execution context of an AI agent strategy.
 *
 * This class implements `CoroutineContext.Element`, enabling it to store and provide access
 * to node-specific context information, such as the name of the node, within coroutine scopes.
 *
 * @property name The name of the node associated with this context element.
 * @property input The input data provided to the node during execution.
 * @property inputType The type of the input data provided to the node during execution.
 */
public data class NodeInfoContextElement(
    val id: String,
    val parentId: String?,
    val name: String,
    val input: Any?,
    val inputType: KType
) : CoroutineContext.Element {

    /**
     * A companion object that serves as the key for the `NodeInfoContextElement` in a `CoroutineContext`.
     * This key enables the retrieval of a `NodeInfoContextElement` instance stored within a coroutine's context.
     *
     * Implements `CoroutineContext.Key` to provide a type-safe mechanism for accessing the associated context element.
     */
    public companion object Key : CoroutineContext.Key<NodeInfoContextElement>

    override val key: CoroutineContext.Key<*> = Key
}

/**
 * Retrieves the `NodeInfoContextElement` from the current coroutine context, if present.
 *
 * @return The `NodeInfoContextElement` if it exists in the current coroutine context, or `null` if not found.
 */
public suspend fun getNodeInfoElement(): NodeInfoContextElement? =
    currentCoroutineContext()[NodeInfoContextElement.Key]

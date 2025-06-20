package ai.koog.agents.core.agent.entity

/**
 * Means that the entity has subnodes.
 * */
public interface HasSubnodes {

    /**
     * Forces the entity to use a specific node for execution.
     */
    public var forcedNode: AIAgentNodeBase<*, *>?

    /**
     * Sets a forced node for the entity.
     */
    public fun enforceNode(node: AIAgentNodeBase<*, *>) {
        if (forcedNode != null) {
            throw IllegalStateException("Forced node is already set to ${forcedNode!!.name}")
        }
        forcedNode = node
    }
}
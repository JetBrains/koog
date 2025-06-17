package ai.koog.agents.core.agent.entity

/**
 * Represents metadata associated with a subgraph in an AI agent strategy graph.
 *
 * This class holds information about the nodes present in the subgraph and provides
 * insights into the structural uniqueness of node names within the graph. The subgraph
 * is identified by a map of node names to their corresponding `AIAgentNodeBase` implementations.
 *
 * @property nodesMap A map where the keys are node names (String) and the values are the corresponding
 * AI agent nodes (`AIAgentNodeBase`). This map represents the structural composition
 * of the subgraph.
 *
 * @property uniqueNames A boolean flag indicating if node names within the subgraph are unique. If `true`,
 * all node names in the `nodesMap` are guaranteed to be distinct, which can be useful
 * for ensuring consistency and avoiding conflicts in subgraph operations.
 */
public data class SubgraphMetadata(
    val nodesMap: Map<String, AIAgentNodeBase<*, *>>,
    val uniqueNames: Boolean = false,
)
package ai.koog.agents.core.feature.handler

/**
 * Represents different types of events that can occur during the execution of an agent or its related processes.
 *
 * The events are categorized into several groups for better organization.
 * Each event type is represented as an object within this interface.
 */
public sealed interface AgentLifecycleEventType {

    //region Agent Events

    /**
     * Represents an event triggered when an agent is started.
     */
    public object AgentStarting : AgentLifecycleEventType

    /**
     * Represents an event triggered when an agent is finished.
     */
    public object AgentCompleted : AgentLifecycleEventType

    /**
     * Represents an event triggered when an agent encounters an error.
     */
    public object AgentExecutionFailed : AgentLifecycleEventType

    /**
     * Represents an event triggered before an agent is closed.
     */
    public object AgentClosing : AgentLifecycleEventType

    /**
     * Represents an event triggered when an agent is transformed.
     */
    public object AgentEnvironmentTransforming : AgentLifecycleEventType

    //endregion Agent Events

    //region Strategy Events

    /**
     * Represents an event triggered when a strategy is started.
     */
    public object StrategyStarting : AgentLifecycleEventType

    /**
     * Represents an event triggered when a strategy is finished.
     */
    public object StrategyCompleted : AgentLifecycleEventType

    //endregion Strategy Events

    //region Node

    /**
     * Represents an event triggered before a node is executed.
     */
    public object NodeExecutionStarting : AgentLifecycleEventType

    /**
     * Represents an event triggered after a node has been executed.
     */
    public object NodeExecutionCompleted : AgentLifecycleEventType

    /**
     * Represents an event triggered when an error occurs during node execution.
     */
    public object NodeExecutionFailed : AgentLifecycleEventType

    //endregion Node

    //region Subgraph

    /**
     * Represents an event triggered before a subgraph is executed.
     */
    public object SubgraphExecutionStarting : AgentLifecycleEventType

    /**
     * Represents an event triggered after a subgraph has been executed.
     */
    public object SubgraphExecutionCompleted : AgentLifecycleEventType

    /**
     * Represents an event triggered when an error occurs during subgraph execution.
     */
    public object SubgraphExecutionFailed : AgentLifecycleEventType

    //endregion Subgraph

    //region LLM

    /**
     * Represents an event triggered when an error occurs during a language model call.
     */
    public object LLMCallStarting : AgentLifecycleEventType

    /**
     * Represents an event triggered after a language model call has completed.
     */
    public object LLMCallCompleted : AgentLifecycleEventType

    //endregion LLM

    //region Tool

    /**
     * Represents an event triggered when a tool is called.
     */
    public object ToolCallStarting : AgentLifecycleEventType

    /**
     * Represents an event triggered when a tool call fails validation.
     */
    public object ToolValidationFailed : AgentLifecycleEventType

    /**
     * Represents an event triggered when a tool call fails.
     */
    public object ToolCallFailed : AgentLifecycleEventType

    /**
     * Represents an event triggered when a tool call succeeds.
     */
    public object ToolCallCompleted : AgentLifecycleEventType

    //endregion Tool

    //region LLM Streaming

    /**
     * Represents an event triggered before streaming from a language model begins.
     */
    public object LLMStreamingStarting : AgentLifecycleEventType

    /**
     * Represents an event triggered when a streaming frame is received from a language model.
     */
    public object LLMStreamingFrameReceived : AgentLifecycleEventType

    /**
     * Represents an event triggered when an error occurs during streaming from a language model.
     */
    public object LLMStreamingFailed : AgentLifecycleEventType

    /**
     * Represents an event triggered after streaming from a language model completes.
     */
    public object LLMStreamingCompleted : AgentLifecycleEventType

    //endregion LLM Streaming
}

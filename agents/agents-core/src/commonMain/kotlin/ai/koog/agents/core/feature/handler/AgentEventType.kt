package ai.koog.agents.core.feature.handler

/**
 * Represents different types of events that can occur during the execution of an agent or its related processes.
 *
 * The events are categorized into several groups for better organization.
 * Each event type is represented as an object within this interface.
 */
public sealed interface AgentEventType {

    //region Agent Events

    /**
     * Represents an event triggered when an agent is started.
     */
    public object BeforeAgentStart : AgentEventType

    /**
     * Represents an event triggered when an agent is finished.
     */
    public object BeforeAgentFinished : AgentEventType

    /**
     * Represents an event triggered when an agent encounters an error.
     */
    public object AgentRunError : AgentEventType

    /**
     * Represents an event triggered before an agent is closed.
     */
    public object BeforeAgentClose : AgentEventType

    /**
     * Represents an event triggered when an agent is transformed.
     */
    public object TransformEnvironment : AgentEventType

    //endregion Agent Events

    //region Strategy Events

    /**
     * Represents an event triggered when a strategy is started.
     */
    public object StrategyStart : AgentEventType

    /**
     * Represents an event triggered when a strategy is finished.
     */
    public object StrategyFinished : AgentEventType

    //endregion Strategy Events

    //region Node

    /**
     * Represents an event triggered before a node is executed.
     */
    public object BeforeNodeExecute : AgentEventType

    /**
     * Represents an event triggered after a node has been executed.
     */
    public object AfterNodeExecute : AgentEventType

    /**
     * Represents an event triggered when an error occurs during node execution.
     */
    public object NodeExecutionError : AgentEventType

    //endregion Node

    //region LLM

    /**
     * Represents an event triggered when an error occurs during a language model call.
     */
    public object BeforeLLMCall : AgentEventType

    /**
     * Represents an event triggered after a language model call has completed.
     */
    public object AfterLLMCall : AgentEventType

    //endregion LLM

    //region Tool

    /**
     * Represents an event triggered when a tool is called.
     */
    public object ExecuteTool : AgentEventType

    /**
     * Represents an event triggered when a tool call fails validation.
     */
    public object ExecuteToolValidationError : AgentEventType

    /**
     * Represents an event triggered when a tool call fails.
     */
    public object ExecuteToolFailure : AgentEventType

    /**
     * Represents an event triggered when a tool call succeeds.
     */
    public object ExecuteToolResult : AgentEventType

    //endregion Tool

    //region LLM Streaming

    /**
     * Represents an event triggered before streaming from a language model begins.
     */
    public object BeforeStream : AgentEventType

    /**
     * Represents an event triggered when a streaming frame is received from a language model.
     */
    public object StreamFrame : AgentEventType

    /**
     * Represents an event triggered when an error occurs during streaming from a language model.
     */
    public object StreamError : AgentEventType

    /**
     * Represents an event triggered after streaming from a language model completes.
     */
    public object AfterStream : AgentEventType

    //endregion LLM Streaming
}

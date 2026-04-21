package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.features.eventHandler.eventString
import ai.koog.agents.features.eventHandler.traceString

class TestEventsCollector {
    // Checks that from each handler, we can call a suspend function.
    // Note: this verifies that JavaAPI method (non-suspend) is not used by the compiler instead.
    private suspend fun someSuspendFunction() {}

    var runId: String = ""

    val size: Int
        get() = collectedEvents.size

    private val _collectedEvents = mutableListOf<String>()

    val collectedEvents: List<String>
        get() = _collectedEvents.toList()

    val eventHandlerFeatureConfig: EventHandlerConfig.() -> Unit = {

        onAgentStarting { eventContext ->
            someSuspendFunction()
            updateRunId(eventContext.runId)
            _collectedEvents.add(
                "OnAgentStarting (agent id: ${eventContext.agent.id}, run id: ${eventContext.runId})"
            )
        }

        onAgentCompleted { eventContext ->
            someSuspendFunction()
            updateRunId(eventContext.runId)
            _collectedEvents.add(
                "OnAgentCompleted (agent id: ${eventContext.agentId}, run id: ${eventContext.runId}, result: ${eventContext.result})"
            )
        }

        onAgentExecutionFailed { eventContext ->
            someSuspendFunction()
            updateRunId(eventContext.runId)
            _collectedEvents.add(
                "OnAgentExecutionFailed (agent id: ${eventContext.agentId}, run id: ${eventContext.runId}, error: ${eventContext.throwable.message})"
            )
        }

        onAgentClosing { eventContext ->
            someSuspendFunction()
            _collectedEvents.add(
                "OnAgentClosing (agent id: ${eventContext.agentId})"
            )
        }

        onStrategyStarting { eventContext ->
            someSuspendFunction()
            updateRunId(eventContext.context.runId)
            _collectedEvents.add(
                "OnStrategyStarting (run id: ${eventContext.runId}, strategy: ${eventContext.strategy.name})"
            )
        }

        onStrategyCompleted { eventContext ->
            someSuspendFunction()
            updateRunId(eventContext.context.runId)
            _collectedEvents.add(
                "OnStrategyCompleted (run id: ${eventContext.context.runId}, strategy: ${eventContext.strategy.name}, result: ${eventContext.result})"
            )
        }

        onNodeExecutionStarting { eventContext ->
            someSuspendFunction()
            updateRunId(eventContext.context.runId)
            _collectedEvents.add(
                "OnNodeExecutionStarting (run id: ${eventContext.context.runId}, node: ${eventContext.node.name}, input: ${eventContext.input})"
            )
        }

        onNodeExecutionCompleted { eventContext ->
            someSuspendFunction()
            updateRunId(eventContext.context.runId)
            _collectedEvents.add(
                "OnNodeExecutionCompleted (run id: ${eventContext.context.runId}, node: ${eventContext.node.name}, input: ${eventContext.input}, output: ${eventContext.output})"
            )
        }

        onNodeExecutionFailed { eventContext ->
            someSuspendFunction()
            updateRunId(eventContext.context.runId)
            _collectedEvents.add(
                "OnNodeExecutionFailed (run id: ${eventContext.context.runId}, node: ${eventContext.node.name}, input: ${eventContext.input}, error: ${eventContext.throwable.message})"
            )
        }

        onSubgraphExecutionStarting { eventContext ->
            someSuspendFunction()
            updateRunId(eventContext.context.runId)
            _collectedEvents.add(
                "OnSubgraphExecutionStarting (run id: ${eventContext.context.runId}, subgraph: ${eventContext.subgraph.name}, input: ${eventContext.input})"
            )
        }

        onSubgraphExecutionCompleted { eventContext ->
            someSuspendFunction()
            updateRunId(eventContext.context.runId)
            _collectedEvents.add(
                "OnSubgraphExecutionCompleted (run id: ${eventContext.context.runId}, subgraph: ${eventContext.subgraph.name}, input: ${eventContext.input}, output: ${eventContext.output})"
            )
        }

        onSubgraphExecutionFailed { eventContext ->
            someSuspendFunction()
            updateRunId(eventContext.context.runId)
            _collectedEvents.add(
                "OnSubgraphExecutionFailed (run id: ${eventContext.context.runId}, subgraph: ${eventContext.subgraph.name}, input: ${eventContext.input}, error: ${eventContext.throwable.message})"
            )
        }

        onLLMCallStarting { eventContext ->
            someSuspendFunction()
            updateRunId(eventContext.runId)
            _collectedEvents.add(
                "OnLLMCallStarting (run id: ${eventContext.runId}, prompt: ${eventContext.prompt.traceString}, tools: [${
                    eventContext.tools.joinToString {
                        it.name
                    }
                }])"
            )
        }

        onLLMCallCompleted { eventContext ->
            someSuspendFunction()
            updateRunId(eventContext.runId)
            _collectedEvents.add(
                "OnLLMCallCompleted (run id: ${eventContext.runId}, prompt: ${eventContext.prompt.traceString}, model: ${eventContext.model.eventString}, tools: [${
                    eventContext.tools.joinToString {
                        it.name
                    }
                }], responses: [${eventContext.responses.joinToString { response -> response.traceString }}])"
            )
        }

        onToolCallStarting { eventContext ->
            someSuspendFunction()
            updateRunId(eventContext.runId)
            _collectedEvents.add(
                "OnToolCallStarting (run id: ${eventContext.runId}, tool: ${eventContext.toolName}, args: ${eventContext.toolArgs})"
            )
        }

        onToolValidationFailed { eventContext ->
            someSuspendFunction()
            updateRunId(eventContext.runId)
            _collectedEvents.add(
                "OnToolValidationFailed (run id: ${eventContext.runId}, tool: ${eventContext.toolName}, args: ${eventContext.toolArgs}, value: ${eventContext.error})"
            )
        }

        onToolCallFailed { eventContext ->
            someSuspendFunction()
            updateRunId(eventContext.runId)
            _collectedEvents.add(
                "OnToolCallFailed (run id: ${eventContext.runId}, tool: ${eventContext.toolName}, args: ${eventContext.toolArgs}, throwable: ${eventContext.error?.message})"
            )
        }

        onToolCallCompleted { eventContext ->
            someSuspendFunction()
            updateRunId(eventContext.runId)
            _collectedEvents.add(
                "OnToolCallCompleted (run id: ${eventContext.runId}, tool: ${eventContext.toolName}, args: ${eventContext.toolArgs}, result: ${eventContext.toolResult})"
            )
        }

        onLLMStreamingStarting { eventContext ->
            someSuspendFunction()
            updateRunId(eventContext.runId)
            _collectedEvents.add(
                "OnLLMStreamingStarting (run id: ${eventContext.runId}, prompt: ${eventContext.prompt.traceString}, model: ${eventContext.model.eventString}, tools: [${
                    eventContext.tools.joinToString { it.name }
                }])"
            )
        }

        onLLMStreamingFrameReceived { eventContext ->
            someSuspendFunction()
            updateRunId(eventContext.runId)
            _collectedEvents.add(
                "OnLLMStreamingFrameReceived (run id: ${eventContext.runId}, frame: ${eventContext.streamFrame})"
            )
        }

        onLLMStreamingFailed { eventContext ->
            someSuspendFunction()
            updateRunId(eventContext.runId)
            _collectedEvents.add(
                "OnLLMStreamingFailed (run id: ${eventContext.runId}, error: ${eventContext.error.message})"
            )
        }

        onLLMStreamingCompleted { eventContext ->
            someSuspendFunction()
            updateRunId(eventContext.runId)
            _collectedEvents.add(
                "OnLLMStreamingCompleted (run id: ${eventContext.runId}, prompt: ${eventContext.prompt.traceString}, model: ${eventContext.model.eventString}, tools: [${
                    eventContext.tools.joinToString { it.name }
                }])"
            )
        }
    }

    @Suppress("unused")
    fun reset() {
        _collectedEvents.clear()
    }

    private fun updateRunId(runId: String) {
        if (this.runId.isEmpty()) this.runId = runId
    }
}

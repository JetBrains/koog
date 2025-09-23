package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.features.eventHandler.eventString
import ai.koog.agents.features.eventHandler.traceString

class TestEventsCollector {

    var runId: String = ""

    val size: Int
        get() = collectedEvents.size

    private val _collectedEvents = mutableListOf<String>()

    val collectedEvents: List<String>
        get() = _collectedEvents.toList()

    val eventHandlerFeatureConfig: EventHandlerConfig.() -> Unit = {

        onAgentStarting { eventContext ->
            runId = eventContext.runId
            _collectedEvents.add(
                "OnBeforeAgentStarted (agent id: ${eventContext.agent.id}, run id: ${eventContext.runId})"
            )
        }

        onAgentCompleted { eventContext ->
            _collectedEvents.add(
                "OnAgentFinished (agent id: ${eventContext.agentId}, run id: ${eventContext.runId}, result: ${eventContext.result})"
            )
        }

        onAgentExecutionFailed { eventContext ->
            _collectedEvents.add(
                "OnAgentRunError (agent id: ${eventContext.agentId}, run id: ${eventContext.runId}, error: ${eventContext.throwable.message})"
            )
        }

        onAgentClosing { eventContext ->
            _collectedEvents.add("OnAgentBeforeClose (agent id: ${eventContext.agentId})")
        }

        onStrategyStarting { eventContext ->
            _collectedEvents.add(
                "OnStrategyStarted (run id: ${eventContext.runId}, strategy: ${eventContext.strategy.name})"
            )
        }

        onStrategyCompleted { eventContext ->
            _collectedEvents.add(
                "OnStrategyFinished (run id: ${eventContext.runId}, strategy: ${eventContext.strategy.name}, result: ${eventContext.result})"
            )
        }

        onNodeExecutionStarting { eventContext ->
            _collectedEvents.add(
                "OnBeforeNode (run id: ${eventContext.context.runId}, node: ${eventContext.node.name}, input: ${eventContext.input})"
            )
        }

        onNodeExecutionCompleted { eventContext ->
            _collectedEvents.add(
                "OnAfterNode (run id: ${eventContext.context.runId}, node: ${eventContext.node.name}, input: ${eventContext.input}, output: ${eventContext.output})"
            )
        }

        onNodeExecutionFailed { eventContext ->
            _collectedEvents.add(
                "OnNodeExecutionError (run id: ${eventContext.context.runId}, node: ${eventContext.node.name}, error: ${eventContext.throwable.message})"
            )
        }

        onLLMCallStarting { eventContext ->
            _collectedEvents.add(
                "OnBeforeLLMCall (run id: ${eventContext.runId}, prompt: ${eventContext.prompt.traceString}, tools: [${
                    eventContext.tools.joinToString {
                        it.name
                    }
                }])"
            )
        }

        onLLMCallCompleted { eventContext ->
            _collectedEvents.add(
                "OnAfterLLMCall (run id: ${eventContext.runId}, prompt: ${eventContext.prompt.traceString}, model: ${eventContext.model.eventString}, tools: [${
                    eventContext.tools.joinToString {
                        it.name
                    }
                }], responses: [${eventContext.responses.joinToString { response -> response.traceString }}])"
            )
        }

        onToolExecutionStarting { eventContext ->
            _collectedEvents.add(
                "OnToolCall (run id: ${eventContext.runId}, tool: ${eventContext.tool.name}, args: ${eventContext.toolArgs})"
            )
        }

        onToolValidationFailed { eventContext ->
            _collectedEvents.add(
                "OnToolValidationError (run id: ${eventContext.runId}, tool: ${eventContext.tool.name}, args: ${eventContext.toolArgs}, value: ${eventContext.error})"
            )
        }

        onToolExecutionFailed { eventContext ->
            _collectedEvents.add(
                "OnToolCallFailure (run id: ${eventContext.runId}, tool: ${eventContext.tool.name}, args: ${eventContext.toolArgs}, throwable: ${eventContext.throwable.message})"
            )
        }

        onToolExecutionCompleted { eventContext ->
            _collectedEvents.add(
                "OnToolCallResult (run id: ${eventContext.runId}, tool: ${eventContext.tool.name}, args: ${eventContext.toolArgs}, result: ${eventContext.result})"
            )
        }

        onLLMStreamingStarting { eventContext ->
            _collectedEvents.add(
                "OnBeforeStream (run id: ${eventContext.runId}, prompt: ${eventContext.prompt.traceString}, model: ${eventContext.model.eventString}, tools: [${
                    eventContext.tools.joinToString { it.name }
                }])"
            )
        }

        onLLMStreamingFrameReceived { eventContext ->
            _collectedEvents.add(
                "OnStreamFrame (run id: ${eventContext.runId}, frame: ${eventContext.streamFrame})"
            )
        }

        onLLMStreamingFailed { eventContext ->
            _collectedEvents.add(
                "OnStreamError (run id: ${eventContext.runId}, error: ${eventContext.error.message})"
            )
        }

        onLLMStreamingCompleted { eventContext ->
            _collectedEvents.add(
                "OnAfterStream (run id: ${eventContext.runId}, prompt: ${eventContext.prompt.traceString}, model: ${eventContext.model.eventString}, tools: [${
                    eventContext.tools.joinToString { it.name }
                }])"
            )
        }
    }

    @Suppress("unused")
    fun reset() {
        _collectedEvents.clear()
    }
}

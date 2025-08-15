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

        onBeforeAgentStarted { eventContext ->
            runId = eventContext.runId
            _collectedEvents.add(
                "OnBeforeAgentStarted (agent id: ${eventContext.agent.id}, run id: ${eventContext.runId}, strategy: ${eventContext.strategy.name})"
            )
        }

        onAgentFinished { eventContext ->
            _collectedEvents.add(
                "OnAgentFinished (agent id: ${eventContext.agentId}, run id: ${eventContext.runId}, result: ${eventContext.result})"
            )
        }

        onAgentRunError { eventContext ->
            _collectedEvents.add(
                "OnAgentRunError (agent id: ${eventContext.agentId}, run id: ${eventContext.runId}, error: ${eventContext.throwable.message})"
            )
        }

        onAgentBeforeClose { eventContext ->
            _collectedEvents.add("OnAgentBeforeClose (agent id: ${eventContext.agentId})")
        }

        onStrategyStarted { eventContext ->
            _collectedEvents.add(
                "OnStrategyStarted (run id: ${eventContext.runId}, strategy: ${eventContext.strategy.name})"
            )
        }

        onStrategyFinished { eventContext ->
            _collectedEvents.add(
                "OnStrategyFinished (run id: ${eventContext.runId}, strategy: ${eventContext.strategy.name}, result: ${eventContext.result})"
            )
        }

        onBeforeNode { eventContext ->
            _collectedEvents.add(
                "OnBeforeNode (run id: ${eventContext.context.runId}, node: ${eventContext.node.name}, input: ${eventContext.input})"
            )
        }

        onAfterNode { eventContext ->
            _collectedEvents.add(
                "OnAfterNode (run id: ${eventContext.context.runId}, node: ${eventContext.node.name}, input: ${eventContext.input}, output: ${eventContext.output})"
            )
        }

        onNodeExecutionError { eventContext ->
            _collectedEvents.add(
                "OnNodeExecutionError (run id: ${eventContext.context.runId}, node: ${eventContext.node.name}, error: ${eventContext.throwable.message})"
            )
        }

        onBeforeLLMCall { eventContext ->
            _collectedEvents.add(
                "OnBeforeLLMCall (run id: ${eventContext.runId}, prompt: ${eventContext.prompt.traceString}, tools: [${
                    eventContext.tools.joinToString {
                        it.name
                    }
                }])"
            )
        }

        onAfterLLMCall { eventContext ->
            _collectedEvents.add(
                "OnAfterLLMCall (run id: ${eventContext.runId}, prompt: ${eventContext.prompt.traceString}, model: ${eventContext.model.eventString}, tools: [${
                    eventContext.tools.joinToString {
                        it.name
                    }
                }], responses: [${eventContext.responses.joinToString { response -> response.traceString }}])"
            )
        }

        onToolCall { eventContext ->
            _collectedEvents.add(
                "OnToolCall (run id: ${eventContext.runId}, tool: ${eventContext.tool.name}, args: ${eventContext.toolArgs})"
            )
        }

        onToolValidationError { eventContext ->
            _collectedEvents.add(
                "OnToolValidationError (run id: ${eventContext.runId}, tool: ${eventContext.tool.name}, args: ${eventContext.toolArgs}, value: ${eventContext.error})"
            )
        }

        onToolCallFailure { eventContext ->
            _collectedEvents.add(
                "OnToolCallFailure (run id: ${eventContext.runId}, tool: ${eventContext.tool.name}, args: ${eventContext.toolArgs}, throwable: ${eventContext.throwable.message})"
            )
        }

        onToolCallResult { eventContext ->
            _collectedEvents.add(
                "OnToolCallResult (run id: ${eventContext.runId}, tool: ${eventContext.tool.name}, args: ${eventContext.toolArgs}, result: ${eventContext.result})"
            )
        }
    }

    @Suppress("unused")
    fun reset() {
        _collectedEvents.clear()
    }
}

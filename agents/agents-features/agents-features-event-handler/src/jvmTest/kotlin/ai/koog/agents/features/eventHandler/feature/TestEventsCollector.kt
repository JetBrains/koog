package ai.koog.agents.features.eventHandler.feature

import ai.koog.agents.core.feature.model.eventString
import ai.koog.agents.core.feature.traceString

class TestEventsCollector {

    var sessionId: String = ""

    val size: Int
        get() = collectedEvents.size

    private val _collectedEvents = mutableListOf<String>()

    val collectedEvents: List<String>
        get() = _collectedEvents.toList()

    val eventHandlerFeatureConfig: EventHandlerConfig.() -> Unit = {

        onBeforeAgentStarted { eventContext ->
            sessionId = eventContext.sessionId
            _collectedEvents.add("OnBeforeAgentStarted (agent id: ${eventContext.agent.id}, session id: ${eventContext.sessionId}, strategy: ${eventContext.strategy.name})")
        }

        onAgentFinished { eventContext ->
            _collectedEvents.add("OnAgentFinished (agent id: ${eventContext.agentId}, session id: ${eventContext.sessionId}, result: ${eventContext.result})")
        }

        onAgentRunError { eventContext ->
            _collectedEvents.add("OnAgentRunError (agent id: ${eventContext.agentId}, session id: ${eventContext.sessionId}, throwable: ${eventContext.throwable.message})")
        }

        onStrategyStarted { eventContext ->
            _collectedEvents.add("OnStrategyStarted (session id: ${eventContext.sessionId}, strategy: ${eventContext.strategy.name})")
        }

        onStrategyFinished { eventContext ->
            _collectedEvents.add("OnStrategyFinished (session id: ${eventContext.sessionId}, strategy: ${eventContext.strategy.name}, result: ${eventContext.result})")
        }

        onBeforeNode { eventContext ->
            _collectedEvents.add("OnBeforeNode (session id: ${eventContext.context.sessionId}, node: ${eventContext.node.name}, input: ${eventContext.input})")
        }

        onAfterNode { eventContext ->
            _collectedEvents.add("OnAfterNode (session id: ${eventContext.context.sessionId}, node: ${eventContext.node.name}, input: ${eventContext.input}, output: ${eventContext.output})")
        }

        onBeforeLLMCall { eventContext ->
            _collectedEvents.add("OnBeforeLLMCall (session id: ${eventContext.sessionId}, prompt: ${eventContext.prompt.traceString}, tools: [${eventContext.tools.joinToString { it.name } }])")
        }

        onAfterLLMCall { eventContext ->
            _collectedEvents.add("OnAfterLLMCall (session id: ${eventContext.sessionId}, prompt: ${eventContext.prompt.traceString}, model: ${eventContext.model.eventString}, tools: [${eventContext.tools.joinToString { it.name }}], responses: [${eventContext.responses.joinToString { response -> response.traceString }}])")
        }

        onToolCall { eventContext ->
            _collectedEvents.add("OnToolCall (session id: ${eventContext.sessionId}, tool: ${eventContext.tool.name}, args: ${eventContext.toolArgs})")
        }

        onToolValidationError { eventContext ->
            _collectedEvents.add("OnToolValidationError (session id: ${eventContext.sessionId}, tool: ${eventContext.tool.name}, args: ${eventContext.toolArgs}, value: ${eventContext.error})")
        }

        onToolCallFailure { eventContext ->
            _collectedEvents.add("OnToolCallFailure (session id: ${eventContext.sessionId}, tool: ${eventContext.tool.name}, args: ${eventContext.toolArgs}, throwable: ${eventContext.throwable.message})")
        }

        onToolCallResult { eventContext ->
            _collectedEvents.add("OnToolCallResult (session id: ${eventContext.sessionId}, tool: ${eventContext.tool.name}, args: ${eventContext.toolArgs}, result: ${eventContext.result})")
        }
    }

    fun reset() {
        _collectedEvents.clear()
    }
}

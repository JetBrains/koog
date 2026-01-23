package ai.koog.protocol.feature

import ai.koog.agents.core.agent.GraphAIAgent.FeatureContext
import ai.koog.agents.core.agent.context.DetachedPromptExecutorAPI
import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.FINISH_NODE_PREFIX
import ai.koog.agents.core.agent.entity.AIAgentSubgraph.Companion.START_NODE_PREFIX
import ai.koog.agents.core.feature.handler.agent.AgentCompletedContext
import ai.koog.agents.core.feature.handler.agent.AgentStartingContext
import ai.koog.agents.core.feature.handler.llm.LLMCallCompletedContext
import ai.koog.agents.core.feature.handler.llm.LLMCallStartingContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionCompletedContext
import ai.koog.agents.core.feature.handler.subgraph.SubgraphExecutionStartingContext
import ai.koog.agents.core.feature.handler.tool.ToolCallCompletedContext
import ai.koog.agents.core.feature.handler.tool.ToolCallStartingContext
import ai.koog.agents.features.eventHandler.feature.handleEvents

@OptIn(DetachedPromptExecutorAPI::class)
internal fun FeatureContext.addEventHandler() {
    handleEvents {
        onAgentStarting { ctx ->
            println("---\n>>> Agent: ${ctx.agent.id}\n---")
        }

        onAgentCompleted { ctx ->
            println("---\n<<< Agent: ${ctx.agentId}. Result: ${ctx.result}\n---")
        }

        onSubgraphExecutionStarting { ctx ->
            if (!ctx.subgraph.name.contains(START_NODE_PREFIX) &&
                !ctx.subgraph.name.contains(FINISH_NODE_PREFIX)) {

                println("---\n>>> Subgraph: ${ctx.subgraph.id}. Model: ${ctx.context.llm.model.id}\n---")
            }
        }

        onSubgraphExecutionCompleted { ctx ->
            if (!ctx.subgraph.name.contains(START_NODE_PREFIX) &&
                !ctx.subgraph.name.contains(FINISH_NODE_PREFIX)) {

                println("---\n<<< Subgraph: ${ctx.subgraph.id}. Result: ${ctx.output}\n---")
            }
        }

        onToolCallStarting { ctx ->
            println("---\n>>> Tool start\nTool: ${ctx.toolName}, args: ${ctx.toolArgs}\n---")
        }

        onToolCallCompleted { ctx ->
            println("---\n<<< Tool completed\nTool: ${ctx.toolName}, args: ${ctx.toolArgs}, result: ${ctx.toolResult}\n---")
        }

        onLLMCallStarting { ctx ->
            println(
                "---\n>>> LLM start\nRequest:${ctx.prompt.messages.lastOrNull()?.content}\n" +
                    "tools: ${ctx.tools.joinToString("\n") { " - ${it.name }" } }\n---"
            )
        }

        onLLMCallCompleted { ctx ->
            println(
                "---\n<<< LLM complete\nResponses:${ctx.responses.joinToString("\n") { " - [${it.role.name}] ${it.content}" } } }\n---"
            )
        }
    }
}

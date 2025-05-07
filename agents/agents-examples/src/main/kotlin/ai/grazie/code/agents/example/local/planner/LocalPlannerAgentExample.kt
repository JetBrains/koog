package ai.grazie.code.agents.example.local.planner

import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.local.KotlinAIAgent
import ai.grazie.code.agents.local.agent.LocalAgentConfig
import ai.grazie.code.agents.local.agent.createStorageKey
import ai.grazie.code.agents.local.agent.stage.LocalAgentStageContext
import ai.grazie.code.agents.local.dsl.builders.forwardTo
import ai.grazie.code.agents.local.dsl.builders.strategy
import ai.grazie.code.agents.local.dsl.extensions.*
import ai.grazie.code.agents.local.graph.LocalAgentNode
import ai.jetbrains.code.prompt.executor.model.PromptExecutor
import ai.jetbrains.code.prompt.message.Message
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface PlannerNode {
    suspend fun execute(dispatcher: CoroutineDispatcher)

    interface Builder {
        val subtaskDescription: String

        fun build(): PlannerNode

        data class Reference(var builder: PlannerNode.Builder)
    }
}

abstract class IntermediatePlannerNode : PlannerNode {
    abstract val children: List<PlannerNode>

    abstract class Builder(val children: MutableList<PlannerNode.Builder.Reference>) : PlannerNode.Builder {
        fun addChild(child: PlannerNode.Builder) = apply { children.add(PlannerNode.Builder.Reference(child)) }

        override fun build(): PlannerNode = ParallelNode(children.map { it.builder.build() })
    }
}

class ParallelNode(override val children: List<PlannerNode>) : IntermediatePlannerNode() {
    override suspend fun execute(dispatcher: CoroutineDispatcher) {
        children.map {
            CoroutineScope(dispatcher).launch {
                it.execute(dispatcher)
            }
        }.forEach { it.join() }
    }

    class Builder(override val subtaskDescription: String) : IntermediatePlannerNode.Builder(mutableListOf())
}

class SequentialNode(override val children: List<PlannerNode>) : IntermediatePlannerNode() {
    override suspend fun execute(dispatcher: CoroutineDispatcher) {
        children.forEach { it.execute(dispatcher) }
    }

    class Builder(override val subtaskDescription: String) : IntermediatePlannerNode.Builder(mutableListOf())
}

class DelegateNode(val agent: KotlinAIAgent, val input: String) : PlannerNode {
    override suspend fun execute(dispatcher: CoroutineDispatcher) {
        agent.run(input)
    }

    class Builder(override val subtaskDescription: String, val agent: KotlinAIAgent) : PlannerNode.Builder {
        override fun build() = DelegateNode(agent, subtaskDescription)
    }
}

data class AgentDescriptor(val agent: KotlinAIAgent, val description: String)

interface ParsedMessage

class ParsedSubNodesMessage(val subNodes: List<PlannerNode.Builder>) : ParsedMessage
abstract class FailureMessage(val problemDescription: String) : ParsedMessage
class ParsingErrorMessage(problemDescription: String) : FailureMessage(problemDescription)
class FailedToPlanParallelNode(problemDescription: String) : FailureMessage(problemDescription)
class FailedToPlanSequentialNode(problemDescription: String) : FailureMessage(problemDescription)

fun parse(message: String): ParsedMessage = ParsingErrorMessage("TODO: parsing is not implemented")

suspend fun planWork(
    initialTaskDescription: String,
    subAgents: List<AgentDescriptor>,
    observingTools: ToolRegistry,
    promptExecutor: PromptExecutor,
    eventHandler: EventHandler,
    config: LocalAgentConfig,
    coroutineScope: CoroutineScope
): PlannerNode {
    val unfinishedNodesKey = createStorageKey<MutableList<PlannerNode.Builder.Reference>>("unfinishedNodes")
    val currentNodeKey = createStorageKey<PlannerNode.Builder.Reference>("currentNode")

    val result = CompletableDeferred<PlannerNode>()

    val planner = strategy("planner") {
        stage {
            suspend fun LocalAgentStageContext.defineTask(inputTask: String, prompt: String): Message.Response {
                return llm.writeSession {
                    updatePrompt {
                        system(prompt)
                        system("Available sub-agents: ${subAgents.joinToString { it.description }}")
                        user(inputTask)
                    }

                    requestLLMWithoutTools()
                }
            }

            val setup by nodeLLMSendStageInput()

            val tryFindingSequentialSubtasks: LocalAgentNode<String, Message.Response> by node { inputTask ->
                llm.writeSession {
                    replaceHistoryWithTLDR()
                }

                val newCurrentNode = SequentialNode.Builder(inputTask)

                if (storage.get(currentNodeKey) == null) storage.set(
                    currentNodeKey,
                    PlannerNode.Builder.Reference(newCurrentNode)
                )
                else storage.get(currentNodeKey)!!.builder = newCurrentNode

                defineTask(inputTask = inputTask, prompt = DEFINE_CONSECUTIVE_PLANNING_TASK)
            }

            val tryFindingParallelSubtasks: LocalAgentNode<String, Message.Response> by node { inputTask ->
                llm.writeSession {
                    replaceHistoryWithTLDR()
                }

                val newCurrentNode = ParallelNode.Builder(inputTask)

                if (storage.get(currentNodeKey) == null) storage.set(
                    currentNodeKey,
                    PlannerNode.Builder.Reference(newCurrentNode)
                )
                else storage.get(currentNodeKey)!!.builder = newCurrentNode

                defineTask(inputTask = inputTask, prompt = DEFINE_PARALLEL_PLANNING_TASK)
            }

            val parseLLMResponse by node<Message.Assistant, ParsedMessage> { message ->
                parse(message.content)
            }

            fun <T : FailureMessage> retryPlanning(
                nextNode: LocalAgentNode<String, Message.Response>
            ): LocalAgentNode<T, Message.Response> {
                val result by node<T, Message.Response> { parsedMessage ->
                    nextNode.execute(this, parsedMessage.problemDescription)
                }
                return result
            }

            val saveNodesToState by node<ParsedSubNodesMessage, Unit> {
                if (storage.get(unfinishedNodesKey) == null)
                    storage.set(unfinishedNodesKey, mutableListOf())

                val currentNode = storage.get(currentNodeKey)!!.builder
                if (currentNode is IntermediatePlannerNode.Builder) {
                    it.subNodes.forEach(currentNode::addChild)
                    storage.get(unfinishedNodesKey)!!.addAll(currentNode.children)
                } else {
                    throw IllegalStateException("Sub-nodes must be built only for intermediate nodes, not for agent nodes")
                }
            }

            val pickNodeToBuild by node<Unit, PlannerNode.Builder.Reference?> {
                storage.get(unfinishedNodesKey)?.removeFirst()?.also {
                    storage.set(currentNodeKey, it)
                }
            }

            val callTool by nodeExecuteTool()
            val askLLM by nodeLLMRequest(allowToolCalls = false)

            val buildPlanTree by node<Unit, Unit> {
                val tree = storage.get(currentNodeKey)?.builder?.build()
                    ?: throw Exception("Failed to build any plan, try again :(")
                result.complete(tree)
            }

            edge(nodeStart forwardTo setup)
            edge(setup forwardTo tryFindingSequentialSubtasks transformed { initialTaskDescription })

            edge(tryFindingParallelSubtasks forwardTo callTool onIsInstance Message.Tool.Call::class)
            edge(tryFindingParallelSubtasks forwardTo parseLLMResponse onIsInstance Message.Assistant::class)

            edge(tryFindingSequentialSubtasks forwardTo callTool onIsInstance Message.Tool.Call::class)
            edge(tryFindingSequentialSubtasks forwardTo parseLLMResponse onIsInstance Message.Assistant::class)

            edge(callTool forwardTo askLLM transformed { it.content })
            edge(askLLM forwardTo callTool onIsInstance Message.Tool.Call::class)
            edge(askLLM forwardTo parseLLMResponse onIsInstance Message.Assistant::class)

            edge(parseLLMResponse forwardTo retryPlanning<ParsingErrorMessage>(nextNode = askLLM) onIsInstance ParsingErrorMessage::class)
            edge(parseLLMResponse forwardTo retryPlanning<FailedToPlanParallelNode>(nextNode = tryFindingSequentialSubtasks) onIsInstance FailedToPlanParallelNode::class)
            edge(parseLLMResponse forwardTo retryPlanning<FailedToPlanSequentialNode>(nextNode = tryFindingParallelSubtasks) onIsInstance FailedToPlanSequentialNode::class)

            edge(parseLLMResponse forwardTo saveNodesToState onIsInstance ParsedSubNodesMessage::class)
            edge(saveNodesToState forwardTo pickNodeToBuild)

            edge(
                pickNodeToBuild forwardTo pickNodeToBuild
                        onCondition { it?.builder is DelegateNode.Builder }
                        transformed { }
            )
            edge(
                pickNodeToBuild forwardTo tryFindingParallelSubtasks
                        onCondition { it?.builder is ParallelNode.Builder }
                        transformed { it!!.builder.subtaskDescription }
            )
            edge(
                pickNodeToBuild forwardTo tryFindingSequentialSubtasks
                        onCondition { it?.builder is SequentialNode.Builder }
                        transformed { it!!.builder.subtaskDescription }
            )
            edge(
                pickNodeToBuild forwardTo buildPlanTree
                        onCondition { it == null }
                        transformed {}
            )

            edge(buildPlanTree forwardTo nodeFinish transformed { "Done" })
        }
    }

    KotlinAIAgent(
        toolRegistry = observingTools,
        strategy = planner,
        eventHandler = eventHandler,
        agentConfig = config,
        promptExecutor = promptExecutor,
        cs = coroutineScope
    ).run(initialTaskDescription)

    return result.await()
}

val DEFINE_PARALLEL_PLANNING_TASK = """
    TODO
""".trimIndent()

val DEFINE_CONSECUTIVE_PLANNING_TASK = """
    TODO
""".trimIndent()
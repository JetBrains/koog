package ai.koog.spring.sandwich.agents

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.AIAgent.Companion.State
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.tools
import ai.koog.agents.features.opentelemetry.feature.OpenTelemetry
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.feature.RollbackToolRegistry
import ai.koog.agents.snapshot.feature.registerRollback
import ai.koog.agents.snapshot.feature.withPersistence
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.spring.sandwich.checkpoints.createPostgresStorage
import ai.koog.spring.sandwich.structs.OrderUpdateSummary
import ai.koog.spring.sandwich.tools.CommunicationTools
import ai.koog.spring.sandwich.tools.OrderTools
import ai.koog.spring.sandwich.tools.RollbackTools
import ai.koog.spring.sandwich.tools.UserTools
import io.ktor.util.collections.*
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Service

@Service
class KoogAgentService(
    private val promptExecutor: MultiLLMPromptExecutor,
    private val spanExporters: List<SpanExporter>,
    private val buildProps: BuildProperties,
) {
    private val agentIdsByUser = ConcurrentMap<String, ConcurrentMap<String, Unit>>()
    private val agentsById = ConcurrentMap<String, AIAgent<String, OrderUpdateSummary>>()

    private val postgresStorage by lazy { createPostgresStorage() }

    private fun createAgent(userId: String): AIAgent<String, OrderUpdateSummary> {
        val userTools = UserTools(userId)

        val rollbackTools = RollbackTools(userId)

        val agent = AIAgent(
            promptExecutor = promptExecutor,
            llmModel = OpenAIModels.Chat.GPT5Nano,
            toolRegistry = ToolRegistry {
                tools(CommunicationTools)
                tools(OrderTools)
                tools(userTools)
            },
            strategy = interactiveSupportStrategy(userTools),
        ) {
            install(OpenTelemetry) {
                setServiceInfo(serviceName = buildProps.name!!, serviceVersion = buildProps.version!!)
                setVerbose(true)
                spanExporters.forEach {
                    addSpanExporter(it)
                }
            }

            install(Persistence) {
                storage = postgresStorage

                enableAutomaticPersistence = true

                rollbackToolRegistry = RollbackToolRegistry {
                    registerRollback(userTools::issueRefund, rollbackTools::undoRefund)
                    registerRollback(userTools::makeAnotherOrder, rollbackTools::undoAnotherOrder)
                    registerRollback(OrderTools::contactCarrier, rollbackTools::notifyCarrierAboutCancellation)
                    registerRollback(OrderTools::updateAddress, rollbackTools::rollbackAddress)
                }
            }
        }

        agentIdsByUser.getOrPut(userId) { ConcurrentMap() }[agent.id] = Unit
        agentsById[agent.id] = agent
        return agent
    }

    @OptIn(DelicateCoroutinesApi::class)
    private val parallelScope = CoroutineScope(newFixedThreadPoolContext(nThreads = 10, name = "agents"))

    suspend fun launchSupportAgent(userId: String, question: String): String {
        val agent = createAgent(userId)
        parallelScope.launch { agent.run(question) }
        return agent.id
    }

    suspend fun getState(agentId: String): State<OrderUpdateSummary> {
        val agent = agentsById[agentId] ?: throw Exception("Agent with id = `$agentId` not found")
        return agent.getState()
    }

    @Serializable
    data class CheckpointInfo(
        val checkpointId: String,
        val createdAt: Instant,
        val nodeId: String
    ) {
        companion object {
            fun fromCheckpoint(checkpoint: AgentCheckpointData): CheckpointInfo = CheckpointInfo(
                checkpoint.checkpointId, checkpoint.createdAt, checkpoint.nodeId
            )
        }
    }

    suspend fun getCheckpoints(agentId: String): List<CheckpointInfo> =
        postgresStorage.getCheckpoints(agentId).map(CheckpointInfo::fromCheckpoint)

    suspend fun getAgentIds(userId: String): List<String> =
        agentIdsByUser[userId]?.keys?.toList() ?: emptyList()

    suspend fun rollback(agentId: String, checkpointId: String) {
        val agent = agentsById[agentId] ?: throw Exception("Agent with id = `$agentId` not found")
        agent.withPersistence { ctx ->
            rollbackToCheckpoint(checkpointId, ctx)
        }
    }
}

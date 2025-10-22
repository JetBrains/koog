@file:OptIn(ExperimentalUuidApi::class)

package ai.koog.agents.example.a2a.advancedjoke

import ai.koog.a2a.client.A2AClient
import ai.koog.a2a.client.UrlAgentCardResolver
import ai.koog.a2a.model.Artifact
import ai.koog.a2a.model.Message
import ai.koog.a2a.model.MessageSendParams
import ai.koog.a2a.model.Role
import ai.koog.a2a.model.Task
import ai.koog.a2a.model.TaskArtifactUpdateEvent
import ai.koog.a2a.model.TaskState
import ai.koog.a2a.model.TaskStatusUpdateEvent
import ai.koog.a2a.model.TextPart
import ai.koog.a2a.transport.Request
import ai.koog.a2a.transport.client.jsonrpc.http.HttpJSONRPCClientTransport
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val CYAN = "\u001B[36m"
private const val YELLOW = "\u001B[33m"
private const val MAGENTA = "\u001B[35m"
private const val GREEN = "\u001B[32m"
private const val RED = "\u001B[31m"
private const val BLUE = "\u001B[34m"
private const val RESET = "\u001B[0m"

private val json = Json { prettyPrint = true }

@OptIn(ExperimentalUuidApi::class)
suspend fun main() {
    println("\n${YELLOW}Starting Advanced Joke Generator A2A Client$RESET\n")

    val transport = HttpJSONRPCClientTransport(url = "http://localhost:9999$ADVANCED_JOKE_AGENT_PATH")
    val agentCardResolver = UrlAgentCardResolver(baseUrl = "http://localhost:9999", path = ADVANCED_JOKE_AGENT_CARD_PATH)
    val client = A2AClient(transport = transport, agentCardResolver = agentCardResolver)

    client.connect()
    val agentCard = client.cachedAgentCard()
    println("${YELLOW}Connected: ${agentCard.name}$RESET\n")

    if (agentCard.capabilities.streaming != true) {
        println("${RED}Error: Streaming not supported$RESET")
        transport.close()
        return
    }

    println("${CYAN}Context ID:$RESET")
    val contextId = readln()
    println()

    var currentTaskId: String? = null
    val artifacts = mutableMapOf<String, Artifact>()

    while (true) {
        println("${CYAN}Request (/q to quit):$RESET")
        val request = readln()
        println()

        if (request == "/q") break

        val message = Message(
            messageId = Uuid.random().toString(),
            role = Role.User,
            parts = listOf(TextPart(request)),
            contextId = contextId,
            taskId = currentTaskId
        )

        try {
            client.sendMessageStreaming(Request(MessageSendParams(message = message))).collect { response ->
                val event = response.data
                println("${BLUE}[${event.kind}]$RESET")
                println("${json.encodeToString(event)}\n")

                when (event) {
                    is Task -> {
                        currentTaskId = event.id
                        event.artifacts?.forEach { artifacts[it.artifactId] = it }
                    }

                    is Message -> {
                        val textContent = event.parts.filterIsInstance<TextPart>().joinToString("\n") { it.text }
                        if (textContent.isNotBlank()) {
                            println("${MAGENTA}Message:$RESET\n$textContent\n")
                        }
                    }

                    is TaskStatusUpdateEvent -> {
                        when (event.status.state) {
                            TaskState.InputRequired -> {
                                val question = event.status.message?.parts
                                    ?.filterIsInstance<TextPart>()
                                    ?.joinToString("\n") { it.text }
                                if (!question.isNullOrBlank()) {
                                    println("${MAGENTA}Question:$RESET\n$question\n")
                                }
                            }

                            TaskState.Completed -> {
                                if (artifacts.isNotEmpty()) {
                                    println("${GREEN}=== Artifacts ===$RESET")
                                    artifacts.values.forEach { artifact ->
                                        val content = artifact.parts.filterIsInstance<TextPart>()
                                            .joinToString("\n") { it.text }
                                        if (content.isNotBlank()) {
                                            println("${GREEN}[${artifact.artifactId}]$RESET\n$content\n")
                                        }
                                    }
                                }
                                if (event.final) {
                                    currentTaskId = null
                                    artifacts.clear()
                                }
                            }

                            TaskState.Failed, TaskState.Canceled, TaskState.Rejected -> {
                                if (event.final) {
                                    currentTaskId = null
                                    artifacts.clear()
                                }
                            }

                            else -> {}
                        }
                    }

                    is TaskArtifactUpdateEvent -> {
                        if (event.append == true) {
                            val existing = artifacts[event.artifact.artifactId]
                            if (existing != null) {
                                artifacts[event.artifact.artifactId] = existing.copy(
                                    parts = existing.parts + event.artifact.parts
                                )
                            } else {
                                artifacts[event.artifact.artifactId] = event.artifact
                            }
                        } else {
                            artifacts[event.artifact.artifactId] = event.artifact
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("${RED}Error: ${e.message}$RESET\n")
        }
    }

    println("${YELLOW}Done$RESET")
    transport.close()
}

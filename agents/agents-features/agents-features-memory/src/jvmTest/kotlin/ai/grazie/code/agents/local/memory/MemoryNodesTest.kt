package ai.grazie.code.agents.local.memory

import ai.grazie.code.agents.core.event.EventHandler
import ai.grazie.code.agents.core.tools.ToolRegistry
import ai.grazie.code.agents.core.agent.AIAgentBase
import ai.grazie.code.agents.core.agent.config.LocalAgentConfig
import ai.grazie.code.agents.core.dsl.builder.forwardTo
import ai.grazie.code.agents.core.dsl.builder.strategy
import ai.grazie.code.agents.local.memory.feature.MemoryFeature
import ai.grazie.code.agents.local.memory.feature.nodes.nodeSaveToMemoryAutoDetectFacts
import ai.grazie.code.agents.local.memory.feature.withMemory
import ai.grazie.code.agents.local.memory.model.*
import ai.grazie.code.agents.local.memory.providers.AgentMemoryProvider
import ai.grazie.code.agents.testing.tools.DummyTool
import ai.grazie.code.agents.testing.tools.getMockExecutor
import ai.grazie.code.agents.testing.tools.mockLLMAnswer
import ai.jetbrains.code.prompt.dsl.prompt
import ai.jetbrains.code.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class TestMemoryProvider : AgentMemoryProvider {
    val facts = mutableMapOf<String, MutableList<Fact>>()

    override suspend fun save(fact: Fact, subject: MemorySubject, scope: MemoryScope) {
        val key = "${subject}_${scope}"
        println("[DEBUG_LOG] Saving fact: $fact for key: $key")
        facts.getOrPut(key) { mutableListOf() }.add(fact)
        println("[DEBUG_LOG] Current facts: ${facts[key]}")
    }

    override suspend fun load(concept: Concept, subject: MemorySubject, scope: MemoryScope): List<Fact> {
        val key = "${subject}_${scope}"
        return facts[key]?.filter { it.concept == concept } ?: emptyList()
    }

    override suspend fun loadAll(subject: MemorySubject, scope: MemoryScope): List<Fact> {
        val key = "${subject}_${scope}"
        return facts[key] ?: emptyList()
    }

    override suspend fun loadByDescription(
        description: String,
        subject: MemorySubject,
        scope: MemoryScope
    ): List<Fact> {
        val key = "${subject}_${scope}"
        return facts[key]?.filter { it.concept.description.contains(description) } ?: emptyList()
    }
}

class MemoryNodesTest {
    private fun createMockExecutor() = getMockExecutor {
        mockLLMAnswer("Here's a summary of the conversation: Test user asked questions and received responses.") onRequestContains "Summarize all the main achievements"
        mockLLMAnswer(
            """
            [
                {
                    "subject": "USER",
                    "keyword": "test-concept",
                    "description": "Test concept description",
                    "value": "Test fact value"
                }
            ]
        """
        ) onRequestContains "test-concept"
        mockLLMAnswer(
            """
            [
                {
                    "subject": "USER",
                    "keyword": "user-preference-language",
                    "description": "User's preferred programming language",
                    "value": "Python for data analysis"
                },
                {
                    "subject": "PROJECT",
                    "keyword": "project-requirement-java",
                    "description": "Project's Java version requirement",
                    "value": "Java 11 or higher"
                }
            ]
        """
        ) onRequestContains "Analyze the conversation history and identify important facts about:"
    }

    @Test
    fun testMemoryNodes() = runTest {
        val concept = Concept(
            keyword = "test-concept",
            description = "Is user a human or an agent? Please answer yes or no.",
            factType = FactType.SINGLE
        )

        val testTimestamp = 1234567890L
        val fact = SingleFact(concept = concept, value = "Test fact value", timestamp = testTimestamp)

        val result = mutableListOf<Fact>()

        val strategy = strategy("test-agent") {
            stage {
                val saveAutoDetect by nodeSaveToMemoryAutoDetectFacts<Unit>(
                    subjects = listOf(MemorySubject.USER)
                )

                val saveTestConcept by node<Unit, Unit> {
                    withMemory {
                        agentMemory.save(fact, MemorySubject.USER, MemoryScope.Agent("test-agent"))
                    }
                }

                val loadTestConcept by node<Unit, Unit> {
                    result += withMemory {
                        agentMemory.load(concept, MemorySubject.USER, MemoryScope.Agent("test-agent"))
                    }
                }

                edge(nodeStart forwardTo saveAutoDetect)
                edge(saveAutoDetect forwardTo saveTestConcept)
                edge(saveTestConcept forwardTo loadTestConcept)
                edge(loadTestConcept forwardTo nodeFinish transformed { "Done" })
            }
        }

        val agentConfig = LocalAgentConfig(
            prompt = prompt(OllamaModels.Meta.LLAMA_3_2, "test") {
                system("Test system message")
                user("I prefer using Python for data analysis")
                assistant("I'll remember that you prefer Python for data analysis tasks")
            },
            maxAgentIterations = 10
        )

        val agent = AIAgentBase(
            promptExecutor = createMockExecutor(),
            toolRegistry = ToolRegistry {
                stage {
                    tool(DummyTool())
                }
            },
            strategy = strategy,
            eventHandler = EventHandler {},
            agentConfig = agentConfig,
            cs = this
        ) {
            install(MemoryFeature) {
                memoryProvider = TestMemoryProvider()

                featureName = "test-feature"
                organizationName = "test-organization"
            }
        }


        agent.run("")


        // Verify that the fact was saved and loaded correctly with timestamp
        assertEquals(1, result.size)
        val loadedFact = result.first()
        assertEquals(fact.concept, loadedFact.concept)
        assertEquals(fact.timestamp, loadedFact.timestamp)
        assertTrue(loadedFact is SingleFact)
        assertEquals(fact.value, loadedFact.value)
    }

    @Test
    fun testAutoDetectFacts() = runTest {
        val strategy = strategy("test-agent") {
            stage {
                val detect by nodeSaveToMemoryAutoDetectFacts<Unit>(
                    subjects = listOf(MemorySubject.USER, MemorySubject.PROJECT)
                )

                edge(nodeStart forwardTo detect transformed { })
                edge(detect forwardTo nodeFinish transformed { "Done" })
            }
        }

        val eventHandler = EventHandler {}

        val memory = TestMemoryProvider()

        val agentConfig = LocalAgentConfig(
            prompt = prompt(OllamaModels.Meta.LLAMA_3_2, "test") {
                system("Test system message")
                user("I prefer using Python for data analysis")
                assistant("I'll remember that you prefer Python for data analysis tasks")
                user("Our project requires Java 11 or higher")
                assistant("Noted about the Java version requirement")
            },
            maxAgentIterations = 10
        )

        val agent = AIAgentBase(
            promptExecutor = createMockExecutor(),
            toolRegistry = ToolRegistry {
                stage("default") {
                    tool(DummyTool())
                }
            },
            strategy = strategy,
            eventHandler = eventHandler,
            agentConfig = agentConfig,
            cs = this
        ) {
            install(MemoryFeature) {
                memoryProvider = memory
            }
        }

        agent.run("")

        // Verify that facts were detected and saved with timestamps
        assertEquals(2, memory.facts.size)
        val facts = memory.facts.values.flatten()
        assertTrue(facts.isNotEmpty())

        // Verify facts have proper concepts and timestamps
        assertTrue(facts.any { fact ->
            fact.concept.keyword.contains("user-preference") &&
                    fact.timestamp > 0 // Verify timestamp is set
        })
        assertTrue(facts.any { fact ->
            fact.concept.keyword.contains("project-requirement") &&
                    fact.timestamp > 0 // Verify timestamp is set
        })
    }
}

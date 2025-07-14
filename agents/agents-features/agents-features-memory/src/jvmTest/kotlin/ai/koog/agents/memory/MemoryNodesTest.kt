package ai.koog.agents.memory

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.memory.feature.AgentMemory
import ai.koog.agents.memory.feature.nodes.nodeSaveToMemoryAutoDetectFacts
import ai.koog.agents.memory.feature.withMemory
import ai.koog.agents.memory.model.*
import ai.koog.agents.memory.providers.AgentMemoryProvider
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.agents.testing.tools.mockLLMAnswer
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
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

@OptIn(InternalAgentsApi::class)
class MemoryNodesTest {
    object MemorySubjects {
        /**
         * Information specific to the local machine environment
         * Examples: Installed tools, SDKs, OS configuration, available commands
         */
        @Serializable
        data object Machine : MemorySubject() {
            override val name: String = "machine"
            override val promptDescription: String =
                "Technical environment (installed tools, package managers, packages, SDKs, OS, etc.)"
            override val priorityLevel: Int = 1
        }

        /**
         * Information specific to the current user
         * Examples: Preferences, settings, authentication tokens
         */
        @Serializable
        data object User : MemorySubject() {
            override val name: String = "user"
            override val promptDescription: String =
                "User's preferences, settings, and behavior patterns, expectations from the agent, preferred messaging style, etc."
            override val priorityLevel: Int = 2
        }

        /**
         * Information specific to the current project
         * Examples: Build configuration, dependencies, code style rules
         */
        @Serializable
        data object Project : MemorySubject() {
            override val name: String = "project"
            override val promptDescription: String =
                "Project details, requirements, and constraints, dependencies, folders, technologies, modules, documentation, etc."
            override val priorityLevel: Int = 3
        }

        /**
         * Information shared across an organization
         * Examples: Coding standards, shared configurations, team practices
         */
        @Serializable
        data object Organization : MemorySubject() {
            override val name: String = "organization"
            override val promptDescription: String = "Organization structure and policies"
            override val priorityLevel: Int = 4
        }
    }

    private fun createMockExecutor() = getMockExecutor {
        mockLLMAnswer("Here's a summary of the conversation: Test user asked questions and received responses.") onRequestContains "Summarize all the main achievements"
        mockLLMAnswer(
            """
            [
                {
                    "subject": "user",
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
                    "subject": "user",
                    "keyword": "user-preference-language",
                    "description": "User's preferred programming language",
                    "value": "Python for data analysis"
                },
                {
                    "subject": "project",
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

        val strategy = strategy<String, String>("test-agent") {
            val saveAutoDetect by nodeSaveToMemoryAutoDetectFacts<Unit>(
                subjects = listOf(MemorySubjects.User)
            )

            val saveTestConcept by node<Unit, Unit> {
                withMemory {
                    agentMemory.save(fact, MemorySubjects.User, MemoryScope.Agent("test-agent"))
                }
            }

            val loadTestConcept by node<Unit, Unit> {
                result += withMemory {
                    agentMemory.load(concept, MemorySubjects.User, MemoryScope.Agent("test-agent"))
                }
            }

            edge(nodeStart forwardTo saveAutoDetect transformed { })
            edge(saveAutoDetect forwardTo saveTestConcept)
            edge(saveTestConcept forwardTo loadTestConcept)
            edge(loadTestConcept forwardTo nodeFinish transformed { "Done" })
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("Test system message")
                user("I prefer using Python for data analysis")
                assistant("I'll remember that you prefer Python for data analysis tasks")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent(
            promptExecutor = createMockExecutor(),
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry {
                tool(DummyTool())
            }
        ) {
            install(AgentMemory) {
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
        val strategy = strategy<String, String>("test-agent") {
            val detect by nodeSaveToMemoryAutoDetectFacts<Unit>(
                subjects = listOf(MemorySubjects.User, MemorySubjects.Project)
            )

            edge(nodeStart forwardTo detect transformed { })
            edge(detect forwardTo nodeFinish transformed { "Done" })
        }

        val memory = TestMemoryProvider()

        val agentConfig = AIAgentConfig(
            prompt = prompt("test") {
                system("Test system message")
                user("I prefer using Python for data analysis")
                assistant("I'll remember that you prefer Python for data analysis tasks")
                user("Our project requires Java 11 or higher")
                assistant("Noted about the Java version requirement")
            },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 10
        )

        val agent = AIAgent(
            promptExecutor = createMockExecutor(),
            strategy = strategy,
            agentConfig = agentConfig,
            toolRegistry = ToolRegistry {
                tool(DummyTool())
            }
        ) {
            install(AgentMemory) {
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

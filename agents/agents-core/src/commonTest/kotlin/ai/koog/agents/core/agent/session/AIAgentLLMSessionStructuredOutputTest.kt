package ai.koog.agents.core.agent.session

import ai.koog.agents.core.CalculatorChatExecutor.testClock
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.context.AgentTestBase
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.structure.StructuredRequest
import ai.koog.prompt.structure.StructuredRequestConfig
import ai.koog.prompt.structure.json.JsonStructure
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AIAgentLLMSessionStructuredOutputTest : AgentTestBase() {

    @Serializable
    data class TestStructure(
        @property:LLMDescription("The name field")
        val name: String,
        @property:LLMDescription("The age field")
        val age: Int,
        @property:LLMDescription("Optional description field")
        val description: String? = null
    )

    @Test
    fun testParseResponseToStructuredResponse() = runTest {
        val structure = JsonStructure.create<TestStructure>()
        val config = StructuredRequestConfig(
            default = StructuredRequest.Manual(structure)
        )

        val mockExecutor = getMockExecutor {
            mockLLMAnswer("Test response").asDefaultResponse
        }

        val prompt = prompt("test") {
            system("Test system message")
        }

        val llmContext = AIAgentLLMContext(
            tools = emptyList(),
            prompt = prompt,
            model = OpenAIModels.Chat.GPT4oMini,
            responseProcessor = null,
            promptExecutor = mockExecutor,
            environment = createTestEnvironment(),
            config = createTestConfig(),
            clock = testClock
        )

        val context = createTestContext(
            llmContext = llmContext
        )

        val jsonResponse = """
            {
                "name": "John Doe",
                "age": 30,
                "description": "A test person"
            }
        """.trimIndent()

        val assistantMessage = Message.Assistant(
            content = jsonResponse,
            metaInfo = ResponseMetaInfo.create(testClock)
        )

        val result = context.llm.writeSession {
            parseResponseToStructuredResponse(assistantMessage, config)
        }

        assertNotNull(result)
        assertEquals("John Doe", result.data.name)
        assertEquals(30, result.data.age)
        assertEquals("A test person", result.data.description)
        assertEquals(assistantMessage, result.message)
    }

    @Test
    fun testParseResponseToStructuredResponseWithNullableField() = runTest {
        val structure = JsonStructure.create<TestStructure>()
        val config = StructuredRequestConfig(
            default = StructuredRequest.Manual(structure)
        )

        val mockExecutor = getMockExecutor {
            mockLLMAnswer("Test response").asDefaultResponse
        }

        val prompt = prompt("test") {
            system("Test system message")
        }

        val llmContext = AIAgentLLMContext(
            tools = emptyList(),
            prompt = prompt,
            model = OpenAIModels.Chat.GPT4oMini,
            responseProcessor = null,
            promptExecutor = mockExecutor,
            environment = createTestEnvironment(),
            config = createTestConfig(),
            clock = testClock
        )

        val context = createTestContext(
            llmContext = llmContext
        )

        val jsonResponse = """
            {
                "name": "Jane Doe",
                "age": 25
            }
        """.trimIndent()

        val assistantMessage = Message.Assistant(
            content = jsonResponse,
            metaInfo = ResponseMetaInfo.create(testClock)
        )

        val result = context.llm.writeSession {
            parseResponseToStructuredResponse(assistantMessage, config)
        }

        assertNotNull(result)
        assertEquals("Jane Doe", result.data.name)
        assertEquals(25, result.data.age)
        assertEquals(null, result.data.description)
        assertEquals(assistantMessage, result.message)
    }

    @Test
    fun testParseResponseToStructuredResponseComplexStructure() = runTest {
        @Serializable
        data class Address(
            @property:LLMDescription("Street name")
            val street: String,
            @property:LLMDescription("City name")
            val city: String
        )

        @Serializable
        data class ComplexStructure(
            @property:LLMDescription("User identifier")
            val id: Int,
            @property:LLMDescription("List of addresses")
            val addresses: List<Address>,
            @property:LLMDescription("Tags associated with the user")
            val tags: Set<String>
        )

        val structure = JsonStructure.create<ComplexStructure>()
        val config = StructuredRequestConfig(
            default = StructuredRequest.Manual(structure)
        )

        val mockExecutor = getMockExecutor {
            mockLLMAnswer("Test response").asDefaultResponse
        }

        val prompt = prompt("test") {
            system("Test system message")
        }

        val llmContext = AIAgentLLMContext(
            tools = emptyList(),
            prompt = prompt,
            model = OpenAIModels.Chat.GPT4oMini,
            responseProcessor = null,
            promptExecutor = mockExecutor,
            environment = createTestEnvironment(),
            config = createTestConfig(),
            clock = testClock
        )

        val context = createTestContext(
            llmContext = llmContext
        )

        val jsonResponse = """
            {
                "id": 123,
                "addresses": [
                    {
                        "street": "123 Main St",
                        "city": "New York"
                    },
                    {
                        "street": "456 Oak Ave",
                        "city": "Los Angeles"
                    }
                ],
                "tags": ["vip", "premium", "verified"]
            }
        """.trimIndent()

        val assistantMessage = Message.Assistant(
            content = jsonResponse,
            metaInfo = ResponseMetaInfo.create(testClock)
        )

        val result = context.llm.writeSession {
            parseResponseToStructuredResponse(assistantMessage, config)
        }

        assertNotNull(result)
        assertEquals(123, result.data.id)
        assertEquals(2, result.data.addresses.size)
        assertEquals("123 Main St", result.data.addresses[0].street)
        assertEquals("New York", result.data.addresses[0].city)
        assertEquals("456 Oak Ave", result.data.addresses[1].street)
        assertEquals("Los Angeles", result.data.addresses[1].city)
        assertEquals(setOf("vip", "premium", "verified"), result.data.tags)
        assertEquals(assistantMessage, result.message)
    }
}

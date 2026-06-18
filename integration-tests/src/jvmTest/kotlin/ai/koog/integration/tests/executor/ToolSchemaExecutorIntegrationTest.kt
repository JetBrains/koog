package ai.koog.integration.tests.executor

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.integration.tests.utils.Models
import ai.koog.integration.tests.utils.RetryUtils.withRetry
import ai.koog.integration.tests.utils.getLLMClientForProvider
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.params.LLMParams.ToolChoice
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class ToolSchemaExecutorIntegrationTest {
    @OptIn(ExperimentalSerializationApi::class)
    private val toolCallJson = Json {
        decodeEnumsCaseInsensitive = true
    }

    companion object {
        @JvmStatic
        fun openAIModels(): Stream<LLModel> {
            return Models.openAIModels()
        }

        @JvmStatic
        fun anthropicModels(): Stream<LLModel> {
            return Models.anthropicModels()
        }

        @JvmStatic
        fun googleModels(): Stream<LLModel> {
            return Models.googleModels()
        }

        @JvmStatic
        fun openRouterModels(): Stream<LLModel> {
            return Models.openRouterModels()
        }

        @JvmStatic
        fun bedrockModels(): Stream<LLModel> {
            return Models.bedrockModels()
        }

        @JvmStatic
        fun mistralModels(): Stream<LLModel> {
            return Models.mistralModels()
        }

        @JvmStatic
        fun invalidToolDescriptors(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(
                    ToolDescriptor(
                        name = "",
                        description = "Tool with empty name",
                        requiredParameters = listOf(
                            ToolParameterDescriptor("param", "A parameter", ToolParameterType.String)
                        )
                    ),
                    "Invalid 'tools[0].function.name': empty string. Expected a string with minimum length 1, but got an empty string instead."
                ),
                // Uncomment when KG-185 is fixed
                /*Arguments.of(
                    ToolDescriptor(
                        name = "test_tool",
                        description = "",
                        requiredParameters = listOf(
                            ToolParameterDescriptor("param", "A parameter", ToolParameterType.String)
                        )
                    ),
                    "Invalid 'tools[0].function.description': empty string. Expected a string with minimum length 1, but got an empty string instead."
                ),
                Arguments.of(
                    ToolDescriptor(
                        name = "param_name_test",
                        description = "Tool to test parameter name validation",
                        requiredParameters = listOf(
                            ToolParameterDescriptor("", "Parameter with empty name", ToolParameterType.String)
                        )
                    ),
                    "Invalid 'tools[0].function.requiredParameters[0]': empty string. Expected a string with minimum length 1, but got an empty string instead."
                )*/
            )
        }
    }

    class FileTools : ToolSet {

        @Tool
        @LLMDescription(
            "Writes content to a file (creates new or overwrites existing). BOTH filePath AND content parameters are REQUIRED."
        )
        fun writeFile(
            @LLMDescription("Full path where the file should be created") filePath: String,
            @LLMDescription("Content to write to the file - THIS IS REQUIRED AND CANNOT BE EMPTY") content: String,
            @LLMDescription("Whether to overwrite if file exists (default: false)") overwrite: Boolean = false
        ) {
            println("Writing '$content' to file '$filePath' with overwrite=$overwrite")
        }
    }

    @Serializable
    data class FileOperation(
        val filePath: String,
        val content: String,
        val overwrite: Boolean = false
    )

    @Serializable
    data class NullableDeliveryRequest(
        val recipient: NullableRecipient,
        val requestedSlots: List<NullableDeliverySlot?>,
        val fallbackAddress: NullableAddress? = null,
    )

    @Serializable
    data class NullableRecipient(
        val name: String,
        val contact: NullableContact? = null,
    )

    @Serializable
    data class NullableContact(
        val email: String? = null,
        val phone: String? = null,
    )

    @Serializable
    data class NullableDeliverySlot(
        val day: NullableDeliveryDay,
        val address: NullableAddress? = null,
    )

    @Serializable
    data class NullableAddress(
        val city: String,
        val street: String? = null,
    )

    @Serializable
    enum class NullableDeliveryDay {
        MONDAY,
        TUESDAY,
        WEDNESDAY,
    }

    private val nullableDeliveryTool = ToolDescriptor(
        name = "schedule_nullable_delivery",
        description = "Schedules a delivery with nullable nested address and contact fields.",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "recipient",
                description = "The recipient for the delivery.",
                type = ToolParameterType.Object(
                    properties = listOf(
                        ToolParameterDescriptor("name", "Recipient name.", ToolParameterType.String),
                        ToolParameterDescriptor(
                            "contact",
                            "Optional contact details.",
                            nullableObject(
                                ToolParameterDescriptor(
                                    "email",
                                    "Optional email address.",
                                    nullableScalar(ToolParameterType.String)
                                ),
                                ToolParameterDescriptor(
                                    "phone",
                                    "Optional phone number.",
                                    nullableScalar(ToolParameterType.String)
                                ),
                            )
                        ),
                    ),
                    requiredProperties = listOf("name"),
                )
            ),
            ToolParameterDescriptor(
                name = "requestedSlots",
                description = "Requested delivery slots. Include a null item when a slot is intentionally unknown.",
                type = ToolParameterType.List(
                    ToolParameterType.AnyOf(
                        arrayOf(
                            ToolParameterDescriptor("slot", "Known slot.", deliverySlotType()),
                            ToolParameterDescriptor("null", "Unknown slot.", ToolParameterType.Null),
                        )
                    )
                )
            ),
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor(
                name = "fallbackAddress",
                description = "Optional fallback address.",
                type = nullableAddressType(),
            )
        )
    )

    @ParameterizedTest
    @MethodSource(
        "anthropicModels",
        "googleModels",
        "openAIModels",
        "openRouterModels",
        "bedrockModels",
        "mistralModels"
    )
    fun integration_testToolSchemaExecutor(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.supports(LLMCapability.Tools), "Model $model does not support tools")
        assumeTrue(model.supports(LLMCapability.ToolChoice), "Model $model does not support tool choice")

        val fileTools = FileTools()

        val toolsFromCallable = fileTools.asTools()

        val tools = toolsFromCallable.map { it.descriptor }

        val writeFileTool = tools.first { it.name == "writeFile" }

        val prompt = prompt("test-write-file", params = LLMParams(toolChoice = ToolChoice.Required)) {
            system("You are a helpful assistant with access to a file writing tool. ALWAYS use tools.")
            user("Please write 'Hello, World!' to a file named 'hello.txt'.")
        }

        withRetry {
            val response = getLLMClientForProvider(model.provider).execute(prompt, model, listOf(writeFileTool))
            val toolCall = response.parts.filterIsInstance<MessagePart.Tool.Call>().firstOrNull()
            toolCall.shouldNotBeNull {
                with(Json.decodeFromString<FileOperation>(args)) {
                    filePath shouldEndWith "hello.txt"
                    content.trim().shouldContain("Hello, World!")
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("anthropicModels", "googleModels", "openAIModels", "openRouterModels")
    fun integration_testNestedNullableToolSchemaExecutor(model: LLModel) = runTest(timeout = 300.seconds) {
        Models.assumeAvailable(model.provider)
        assumeTrue(model.supports(LLMCapability.Tools), "Model $model does not support tools")

        val prompt = prompt("test-nested-nullable-tool", params = LLMParams(toolChoice = ToolChoice.Required)) {
            system(
                """
                You are a scheduler. You MUST call schedule_nullable_delivery exactly once.
                Use recipient name Ada Lovelace. Set contact email to null and phone to +1-555-0100.
                Requested slots must contain one known WEDNESDAY slot in Paris and one null slot.
                Set fallbackAddress to null.
                """.trimIndent()
            )
            user("Schedule the delivery now.")
        }

        withRetry(times = 3, testName = "integration_testNestedNullableToolSchemaExecutor[${model.id}]") {
            val response = getLLMClientForProvider(model.provider).execute(prompt, model, listOf(nullableDeliveryTool))
            val toolCall = response.parts.filterIsInstance<MessagePart.Tool.Call>().firstOrNull()

            toolCall.shouldNotBeNull {
                tool shouldContain nullableDeliveryTool.name
                val request = toolCallJson.decodeFromString<NullableDeliveryRequest>(args)

                request.recipient.name shouldContain "Ada"
                request.recipient.contact.shouldNotBeNull {
                    phone shouldContain "555"
                    email shouldBe null
                }
                request.requestedSlots.shouldContain(null)
                request.requestedSlots.filterNotNull().first().day shouldBe NullableDeliveryDay.WEDNESDAY
                request.requestedSlots.filterNotNull().first().address.shouldNotBeNull {
                    city shouldContain "Paris"
                }
                request.fallbackAddress shouldBe null
            }
        }
    }

    @ParameterizedTest
    @MethodSource("invalidToolDescriptors")
    fun integration_testInvalidToolDescriptorShouldFail(invalidToolDescriptor: ToolDescriptor, message: String) =
        runTest(timeout = 300.seconds) {
            val model = OpenAIModels.Chat.GPT4o

            assertFailsWith<Exception> {
                getLLMClientForProvider(model.provider).execute(
                    prompt("test-invalid-tool", params = LLMParams(toolChoice = ToolChoice.Required)) {
                        system("You are a helpful assistant with access to tools.")
                        user("Hi.")
                    },
                    model,
                    listOf(invalidToolDescriptor)
                )
            }.message.shouldNotBeNull {
                shouldContain(
                    message
                )
            }
        }

    private fun nullableScalar(type: ToolParameterType): ToolParameterType =
        ToolParameterType.AnyOf(
            arrayOf(
                ToolParameterDescriptor("value", "Present value.", type),
                ToolParameterDescriptor("null", "Absent value.", ToolParameterType.Null),
            )
        )

    private fun nullableObject(vararg properties: ToolParameterDescriptor): ToolParameterType =
        ToolParameterType.AnyOf(
            arrayOf(
                ToolParameterDescriptor(
                    "value",
                    "Present object.",
                    ToolParameterType.Object(
                        properties = properties.toList(),
                        requiredProperties = emptyList(),
                    )
                ),
                ToolParameterDescriptor("null", "Absent object.", ToolParameterType.Null),
            )
        )

    private fun nullableAddressType(): ToolParameterType =
        ToolParameterType.AnyOf(
            arrayOf(
                ToolParameterDescriptor("value", "Known address.", addressType()),
                ToolParameterDescriptor("null", "Unknown address.", ToolParameterType.Null),
            )
        )

    private fun addressType(): ToolParameterType.Object =
        ToolParameterType.Object(
            properties = listOf(
                ToolParameterDescriptor("city", "Address city.", ToolParameterType.String),
                ToolParameterDescriptor("street", "Optional street.", nullableScalar(ToolParameterType.String)),
            ),
            requiredProperties = listOf("city"),
        )

    private fun deliverySlotType(): ToolParameterType.Object =
        ToolParameterType.Object(
            properties = listOf(
                ToolParameterDescriptor(
                    "day",
                    "Delivery day.",
                    ToolParameterType.Enum(NullableDeliveryDay.entries.map { it.name }.toTypedArray())
                ),
                ToolParameterDescriptor("address", "Optional slot address.", nullableAddressType()),
            ),
            requiredProperties = listOf("day"),
        )
}

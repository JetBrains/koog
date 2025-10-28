package ai.koog.prompt.executor.clients.openai.models

import ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMRequest
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIBaseLLMResponse
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIChoiceLogProbs
import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
import ai.koog.prompt.executor.clients.openai.base.models.ServiceTier
import ai.koog.prompt.executor.clients.serialization.AdditionalPropertiesFlatteningSerializer
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.jvm.JvmInline

/**
 * OpenAI Responses API Request
 *
 * @property background Whether to run the model response in the background.
 * @property include Specify additional output data to include in the model response.
 * Currently supported values are:
 *
 * - `code_interpreter_call.outputs`: Includes the outputs of python code execution in code interpreter tool call items.
 * - `computer_call_output.output.image_url`: Include image urls from the computer call output.
 * - `file_search_call.results`: Include the search results of the file search tool call.
 * - `message.input_image.image_url`: Include image urls from the input message.
 * - `message.output_text.logprobs`: Include logprobs with assistant messages.
 * - `reasoning.encrypted_content`: Includes an encrypted version of reasoning tokens in reasoning item outputs.
 *    This enables reasoning items to be used in multi-turn conversations when using the Responses API statelessly
 *    (like when the store parameter is set to false or when an organization is enrolled in the zero data retention program).
 *
 * @property input Text, image, or file inputs to the model, used to generate a response.
 * @property instructions A system (or developer) message inserted into the model's context.
 *
 * When using along with `previous_response_id`,
 * the instructions from a previous response will not be carried over to the next response.
 * This makes it simple to swap out system (or developer) messages in new responses.
 * @property maxOutputTokens An upper bound for the number of tokens that can be generated for a response,
 * including visible output tokens and reasoning tokens.
 * @property maxToolCalls The maximum number of total calls to built-in tools that can be processed in a response.
 * This maximum number applies across all built-in tool calls, not per individual tool.
 * Any further attempts to call a tool by the model will be ignored.
 * @property metadata Set of 16 key-value pairs that can be attached to an object.
 * This can be useful for storing additional information about the object in a structured format
 * and querying for objects via API or the dashboard.
 *
 * Keys are strings with a maximum length of 64 characters. Values are strings with a maximum length of 512 characters.
 * @property model Model ID used to generate the response, like `gpt-4o` or `o3`.
 * OpenAI offers a wide range of models with different capabilities, performance characteristics, and price points
 * @property parallelToolCalls Whether to allow the model to run tool calls in parallel.
 * @property previousResponseId The unique ID of the previous response to the model.
 * Use this to create multi-turn conversations
 * @property prompt Reference to a prompt template and its variables
 * @property promptCacheKey Used by OpenAI to cache responses for similar requests to optimize your cache hit rates.
 * Replaces the [user] field
 * @property reasoning (**gpt-5 and o-series models only**) Configuration options for reasoning models.
 * @property safetyIdentifier A stable identifier used to help detect users of your application that may be violating
 * OpenAI's usage policies.
 * The IDs should be a string that uniquely identifies each user.
 * We recommend hashing their username or email address to avoid sending us any identifying information
 * @property serviceTier Specifies the processing type used for serving the request.
 * @property store Whether to store the generated model response for later retrieval via API.
 * @property stream If set to true,
 * the model response data will be streamed to the client as it is generated using server-sent events.
 * @property streamOptions Options for streaming responses. Only set this when you set` stream: true`.
 * @property temperature What sampling temperature to use, between 0 and 2.
 * Higher values like 0.8 will make the output more random,
 * while lower values like 0.2 will make it more focused and deterministic.
 * We generally recommend altering this or [topP] but not both.
 * @property text Configuration options for a text response from the model.
 * Can be plain text or structured JSON data.
 * @property toolChoice How the model should select which tool (or tools) to use when generating a response.
 * See the `tools` parameter to see how to specify which tools the model can call.
 * @property tools An array of tools the model may call while generating a response.
 * You can specify which tool to use by setting the tool_choice parameter.
 *
 * The two categories of tools you can provide the model are:
 * - Built-in tools: Tools that are provided by OpenAI that extend the model's capabilities,
 * like web search or file search.
 * - Function calls (custom tools): Functions that are defined by you,
 * enabling the model to call your own code with strongly typed arguments and outputs.
 * You can also use custom tools to call your own code.
 * @property topLogprobs An integer between 0 and 20 specifying the number of most likely tokens to return
 * at each token position, each with an associated log probability.
 * @property topP An alternative to sampling with temperature, called nucleus sampling,
 * where the model considers the results of the tokens with top_p probability mass.
 * So 0.1 means only the tokens comprising the top 10% probability mass are considered.
 *
 * We generally recommend altering this or [temperature] but not both.
 * @property truncation The truncation strategy to use for the model response.
 *
 * - `auto`: If the context of this response and previous ones exceeds the model's context window size,
 * the model will truncate the response to fit the context window
 * by dropping input items in the middle of the conversation.
 * - `disabled` (default): If a model response will exceed the context window size for a model,
 * the request will fail with a 400 error.
 * @property user This field is being replaced by [safetyIdentifier] and [promptCacheKey].
 * Use [promptCacheKey] instead to maintain caching optimizations.
 * A stable identifier for your end-users.
 * Used to boost cache hit rates by better bucketing similar requests and to help OpenAI detect and prevent abuse
 *
 * @see <a href="https://platform.openai.com/docs/api-reference/responses/create">Responses API Request</a>
 */
@Serializable
internal class OpenAIResponsesAPIRequest(
    val background: Boolean? = null,
    val include: List<OpenAIInclude>? = null,
    val input: List<Item>? = null,
    val instructions: String? = null,
    val maxOutputTokens: Int? = null,
    val maxToolCalls: Int? = null,
    val metadata: Map<String, String>? = null,
    override val model: String? = null,
    val parallelToolCalls: Boolean? = null,
    val previousResponseId: String? = null,
    val prompt: OpenAIPromptReference? = null,
    val promptCacheKey: String? = null,
    val reasoning: ReasoningConfig? = null,
    val safetyIdentifier: String? = null,
    val serviceTier: ServiceTier? = null,
    val store: Boolean? = null,
    override val stream: Boolean? = null,
    val streamOptions: OpenAIResponsesAPIStreamOptions? = null,
    override val temperature: Double? = null,
    val text: OpenAITextConfig? = null,
    val toolChoice: OpenAIResponsesToolChoice? = null,
    val tools: List<OpenAIResponsesTool>? = null,
    override val topLogprobs: Int? = null,
    override val topP: Double? = null,
    val truncation: Truncation? = null,
    @Deprecated("Use safetyIdentifier and promptCacheKey instead")
    val user: String? = null,
    val additionalProperties: Map<String, JsonElement>? = null,
) : OpenAIBaseLLMRequest

/**
 * Specify additional output data to include in the model response. Currently supported values are:
 *
 * web_search_call.action.sources: Include the sources of the web search tool call.
 * code_interpreter_call.outputs: Includes the outputs of python code execution in code interpreter tool call items.
 * computer_call_output.output.image_url: Include image urls from the computer call output.
 * file_search_call.results: Include the search results of the file search tool call.
 * message.input_image.image_url: Include image urls from the input message.
 * message.output_text.logprobs: Include logprobs with assistant messages.
 * reasoning.encrypted_content: Includes an encrypted version of reasoning tokens in reasoning item outputs. This enables reasoning items to be used in multi-turn conversations when using the Responses API statelessly (like when the store parameter is set to false, or when an organization is enrolled in the zero data retention program).
 */
@Serializable
public enum class OpenAIInclude {
    @SerialName("web_search_call.action.sources")
    WEB_SEARCH_CALL_ACTION_SOURCES,

    @SerialName("code_interpreter_call.outputs")
    CODE_INTERPRETER_CALL_OUTPUTS,

    @SerialName("computer_call_output.output.image_url")
    COMPUTER_CALL_OUTPUT_IMAGE_URL,

    @SerialName("file_search_call.results")
    FILE_SEARCH_CALL_RESULTS,

    @SerialName("message.input_image.image_url")
    INPUT_IMAGE_URL,

    @SerialName("message.output_text.logprobs")
    OUTPUT_TEXT_LOGPROBS,

    @SerialName("reasoning.encrypted_content")
    REASONING_ENCRYPTED_CONTENT,
}

@Serializable(with = ItemPolymorphicSerializer::class)
internal sealed interface Item {

    /**
     * A text input to the model, equivalent to a text input with the `user` role.
     */
    @JvmInline
    @Serializable(with = ItemTextSerializer::class)
    value class Text(val value: String) : Item

    /**
     * A message input to the model with a role indicating instruction following hierarchy.
     * Instructions given with the developer or system role take precedence over instructions given with the user role.
     * Messages with the assistant role are presumed to have been generated by the model in previous interactions.
     * @property content Text, image, or audio input to the model, used to generate a response.
     * Can also contain previous assistant responses.
     * @property role The role of the message input. One of `user`, `assistant`, `system`, or `developer`.
     * @property status The status of item.
     * One of `in_progress`, `completed`, or `incomplete`. Populated when items are returned via API.
     */
    @Serializable
    class InputMessage(
        val content: List<InputContent>,
        val role: String,
        val status: OpenAIInputStatus? = null
    ) : Item {
        val type: String = "message"
    }

    /**
     * An output message from the model.
     * @property content The content of the output message.
     * @property id The unique ID of the output message.
     * @property role The role of the output message. Always `assistant`.
     * @property status The status of the message input. One of `in_progress`, `completed`, or `incomplete`.
     * Populated when input items are returned via API.
     */
    @Serializable
    class OutputMessage(
        val content: List<OutputContent>,
        val id: String? = null,
        val role: String = "assistant",
        val status: OpenAIInputStatus? = null
    ) : Item {
        val type: String = "message"

        fun text(): String {
            return content.joinToString(" ") {
                when (it) {
                    is OutputContent.Text -> it.text
                    is OutputContent.Refusal -> it.refusal
                }
            }
        }
    }

    /**
     * The results of a file search tool call
     * @property id The unique ID of the file search tool call.
     * @property queries The queries used to search for files.
     * @property status The status of the file search tool call. One of `in_progress`, `searching`, `incomplete` or `failed`
     * @property results The results of the file search tool call.
     */
    @Serializable
    class FileSearchToolCall(
        val id: String,
        val queries: List<String>,
        val status: String,
        val results: List<FileSearchToolResult>? = null
    ) : Item {
        val type: String = "file_search_call"

        /**
         * The results of the file search tool call.
         * @property attributes Set of 16 key-value pairs that can be attached to an object.
         * This can be useful for storing additional information about the object in a structured format
         * and querying for objects via API or the dashboard. Keys are strings with a maximum length of 64 characters.
         * Values are strings with a maximum length of 512 characters, booleans, or numbers.
         * @property fileId The unique ID of the file.
         * @property filename The name of the file.
         * @property score The relevance score of the file - a value between 0 and 1.
         * @property text The text that was retrieved from the file.
         */
        @Serializable
        internal class FileSearchToolResult(
            val attributes: Map<String, String>? = null,
            val fileId: String? = null,
            val filename: String? = null,
            val score: Double? = null,
            val text: String? = null,
        )
    }

    /**
     * A tool call to a computer use tool
     *
     * @property action
     * @property callId An identifier used when responding to the tool call with output.
     * @property id The unique ID of the computer call.
     * @property pendingSafetyChecks The pending safety checks for the computer call.
     * @property status The status of the item. One of `in_progress`, `completed`, or `incomplete`.
     * Populated when items are returned via API.
     */
    @Serializable
    class ComputerToolCall(
        val action: Action,
        val callId: String,
        val id: String,
        val pendingSafetyChecks: List<PendingSafetyCheck>,
        val status: String
    ) : Item {
        val type: String = "computer_call"

        @Serializable
        @JsonClassDiscriminator("type")
        internal sealed interface Action {

            /**
             * A click button
             * @property button Indicates which mouse button was pressed during the click.
             * One of `left`, `right`, `wheel`, `back`, or `forward`.
             * @property x The x-coordinate where the click occurred.
             * @property y The y-coordinate where the click occurred.
             */
            @Serializable
            @SerialName("click")
            class Click(val button: String, val x: Int, val y: Int) : Action

            /**
             * A double-click action.
             * @property x The x-coordinate where the double click occurred.
             * @property y The y-coordinate where the double click occurred.
             */
            @Serializable
            @SerialName("double_click")
            class DoubleClick(val x: Int, val y: Int) : Action

            /**
             * A drag action.
             * @property path An array of coordinates representing the path of the drag action.
             * Coordinates will appear as an array of objects
             */
            @Serializable
            @SerialName("drag")
            class Drag(val path: List<Coordinates>) : Action

            /**
             * A collection of keypresses the model would like to perform.
             * @property keys The combination of keys the model is requesting to be pressed.
             * This is an array of strings, each representing a key.
             */
            @Serializable
            @SerialName("keypress")
            class KeyPress(val keys: List<String>) : Action

            /**
             * A mouse move action.
             * @property x The x-coordinate to move to.
             * @property y The y-coordinate to move to.
             */
            @Serializable
            @SerialName("move")
            class Move(val x: Int, val y: Int) : Action

            /**
             * A screenshot action.
             */
            @Serializable
            @SerialName("screenshot")
            class Screenshot() : Action

            /**
             * A scroll action.
             * @property scrollX The horizontal scroll distance.
             * @property scrollY The vertical scroll distance.
             * @property x The x-coordinate where the scroll occurred.
             * @property y The y-coordinate where the scroll occurred.
             */
            @Serializable
            @SerialName("scroll")
            class Scroll(val scrollX: Int, val scrollY: Int, val x: Int, val y: Int) : Action

            /**
             * An action to type in text.
             * @property text The text to type.
             */
            @Serializable
            @SerialName("type")
            class Type(val text: String) : Action

            /**
             * A wait action.
             */
            @Serializable
            @SerialName("wait")
            class Wait() : Action

            @Serializable
            class Coordinates(val x: Int, val y: Int)
        }

        /**
         * The pending safety check for the computer call.
         *
         * @property code The type of the pending safety check.
         * @property id The ID of the pending safety check.
         * @property message Details about the pending safety check.
         */
        @Serializable
        internal class PendingSafetyCheck(val code: String, val id: String, val message: String)
    }

    /**
     * The output of a computer tool call.
     * @property callId The ID of the computer tool call that produced the output.
     * @property output A computer screenshot image used with the computer use tool.
     * @property acknowledgedSafetyChecks The safety checks reported by the API that have been acknowledged by the developer.
     * @property id The ID of the computer tool call output.
     * @property status The status of the message input.
     * One of `in_progress`, `completed`, or `incomplete`. Populated when input items are returned via API.
     */
    @Serializable
    class ComputerToolCallOutput(
        val callId: String,
        val output: Output,
        val acknowledgedSafetyChecks: List<AcknowledgedSafetyCheck>? = null,
        val id: String? = null,
        val status: OpenAIInputStatus? = null,
    ) : Item {
        val type: String = "computer_call_output"

        /**
         * A computer screenshot image used with the computer use tool.
         * @property type Specifies the event type.
         * For a computer screenshot, this property is always set to `computer_screenshot`.
         * @property fileId The identifier of an uploaded file that contains the screenshot.
         * @property imageUrl The URL of the screenshot image.
         */
        @Serializable
        internal class Output(val fileId: String? = null, val imageUrl: String? = null) {
            val type: String = "computer_screenshot"
        }

        /**
         * The safety check reported by the API that have been acknowledged by the developer.
         * @property id The ID of the pending safety check.
         * @property code The type of the pending safety check.
         * @property message Details about the pending safety check.
         */
        @Serializable
        internal class AcknowledgedSafetyCheck(val id: String, val code: String? = null, val message: String? = null)
    }

    /**
     * The results of a web search tool call
     * @property action An object describing the specific action taken in this web search call.
     * Includes details on how the model used the web (search, open_page, find).
     * @property id The unique ID of the web search tool call.
     * @property status The status of the web search tool call.
     */
    @Serializable
    class WebSearchToolCall(val action: Action, val id: String, val status: String) : Item {
        val type: String = "web_search_call"

        @Serializable
        @JsonClassDiscriminator("type")
        internal sealed interface Action {
            /**
             * Action type "search" - Performs a web search query.
             * @property query The search query.
             */
            @Serializable
            @SerialName("search")
            class Search(val query: String) : Action

            /**
             * Action type "open_page" - Opens a specific URL from search results.
             * @property url The URL opened by the model.
             */
            @Serializable
            @SerialName("open_page")
            class OpenPage(val url: String) : Action

            /**
             * Action type "find": Searches for a pattern within a loaded page.
             * @property pattern The pattern or text to search for within the page.
             * @property url The URL of the page searched for the pattern.
             */
            @Serializable
            @SerialName("find")
            class Find(val pattern: String, val url: String) : Action
        }
    }

    /**
     * A tool call to run a function
     * @property arguments A JSON string of the arguments to pass to the function.
     * @property callId The unique ID of the function tool call generated by the model.
     * @property name The name of the function to run.
     * @property id The unique ID of the function tool call.
     * @property status The status of the item. One of `in_progress`, `completed`, or `incomplete`
     */
    @Serializable
    class FunctionToolCall(
        val arguments: String,
        val callId: String,
        val name: String,
        val id: String? = null,
        val status: OpenAIInputStatus? = null,
    ) : Item {
        val type: String = "function_call"
    }

    /**
     * The output of a function tool call.
     * @property callId The unique ID of the function tool call generated by the model.
     * @property output A JSON string of the output of the function tool call.
     * @property id The unique ID of the function tool call output. Populated when this item is returned via API.
     * @property status The status of the item. One of in_progress, completed, or incomplete
     */
    @Serializable
    class FunctionToolCallOutput(
        val callId: String,
        val output: String,
        val id: String? = null,
        val status: OpenAIInputStatus? = null,
    ) : Item {
        val type: String = "function_call_output"
    }

    /**
     * A description of the chain of thought used by a reasoning model while generating a response.
     * Be sure to include these items in your `input` to the Responses API for subsequent turns of a conversation
     * if you are manually managing context.
     * @property id The unique identifier of the reasoning content.
     * @property summary Reasoning summary content.
     * @property content Reasoning text content.
     * @property encryptedContent The encrypted content of the reasoning item -
     * populated when a response is generated with `reasoning.encrypted_content` in the `include` parameter.
     * @property status The status of the item. One of `in_progress`, `completed`, or `incomplete`
     */
    @Serializable
    class Reasoning(
        val id: String,
        val summary: List<Summary>,
        val content: List<Content>? = null,
        val encryptedContent: String? = null,
        val status: OpenAIInputStatus? = null,
    ) : Item {
        val type: String = "reasoning"

        /**
         * Reasoning summary content.
         * @property text A summary of the reasoning output from the model so far.
         * @property type The type of the object. Always `summary_text`.
         */
        @Serializable
        class Summary(val text: String) {
            val type: String = "summary_text"
        }

        /**
         * Reasoning text content.
         * @property text Reasoning text output from the model.
         * @property type The type of the object. Always `reasoning_text`.
         */
        @Serializable
        class Content(val text: String) {
            val type: String = "reasoning_text"
        }
    }

    /**
     * An image generation request made by the model.
     * @property id The unique ID of the image generation call.
     * @property result The generated image encoded in base64.
     * @property status The status of the image generation call.
     */
    @Serializable
    class ImageGenerationCall(val id: String, val result: String?, val status: String) : Item {
        val type: String = "image_generation_call"
    }

    /**
     * A tool call to run code.
     * @property code The code to run, or null if not available.
     * @property containerId The ID of the container used to run the code.
     * @property id The unique ID of the code interpreter tool call.
     * @property outputs The outputs generated by the code interpreter, such as logs or images.
     * Can be null if no outputs are available.
     * @property status The status of the code interpreter tool call.
     * Valid values are `in_progress`, `completed`, `incomplete`, `interpreting`, and `failed`.
     */
    @Serializable
    class CodeInterpreterToolCall(
        val code: String?,
        val containerId: String,
        val id: String,
        val outputs: List<Output>?,
        val status: OpenAIInputStatus,
    ) : Item {
        val type: String = "code_interpreter_call"

        @Serializable
        @JsonClassDiscriminator("type")
        sealed interface Output {
            /**
             * The logs output from the code interpreter.
             * @property logs The logs output from the code interpreter.
             */
            @Serializable
            @SerialName("logs")
            class Logs(val logs: String) : Output

            /**
             * The image output from the code interpreter.
             * @property url The URL of the image output from the code interpreter.
             */
            @Serializable
            @SerialName("image")
            class Image(val url: String) : Output
        }
    }

    /**
     * A tool call to run a command on the local shell.
     * @property action Execute a shell command on the server.
     * @property callId The unique ID of the local shell tool call generated by the model.
     * @property id The unique ID of the local shell call.
     * @property status The status of the local shell call.
     */
    @Serializable
    class LocalShellCall(val action: Action, val callId: String, val id: String, val status: String) : Item {
        val type: String = "local_shell_call"

        /**
         * Execute a shell command on the server.
         * @property command The command to run.
         * @property end Environment variables to set for the command.
         * @property type The type of the local shell action. Always `exec`.
         * @property timeoutMs Optional timeout in milliseconds for the command.
         * @property user Optional user to run the command as.
         * @property workingDirectory Optional working directory to run the command in.
         */
        @Serializable
        class Action(
            val command: List<String>,
            val end: JsonObject,
            val timeoutMs: Int?,
            val user: String? = null,
            val workingDirectory: String? = null,
        ) {
            val type: String = "exec"
        }
    }

    /**
     * The output of a local shell tool call.
     * @property id The unique ID of the local shell tool call generated by the model.
     * @property output A JSON string of the output of the local shell tool call.
     * @property status The status of the item. One of `in_progress`, `completed`, or `incomplete`.
     */
    @Serializable
    class LocalShellCallOutput(val id: String, val output: String, val status: OpenAIInputStatus? = null) : Item {
        val type: String = "local_shell_call_output"
    }

    /**
     * A list of tools available on an MCP server.
     * @property id The unique ID of the list.
     * @property serverLabel The label of the MCP server.
     * @property tools The tools available on the server.
     * @property error Error message if the server could not list tools.
     */
    @Serializable
    class McpListTools(
        val id: String,
        val serverLabel: String,
        val tools: List<McpTool>,
        val error: String? = null
    ) : Item {
        val type: String = "mcp_list_tools"
    }

    /**
     * A request for human approval of a tool invocation.
     * @property arguments A JSON string of arguments for the tool.
     * @property id The unique ID of the approval request.
     * @property name The name of the tool to run.
     * @property serverLabel The label of the MCP server making the request.
     */
    @Serializable
    class McpApprovalRequest(val arguments: String, val id: String, val name: String, val serverLabel: String) : Item {
        val type: String = "mcp_approval_request"
    }

    /**
     * A response to an MCP approval request.
     * @property approvalRequestId The ID of the approval request being answered.
     * @property approve Whether the request was approved.
     * @property id The unique ID of the approval response
     * @property reason Optional reason for the decision.
     */
    @Serializable
    class McpApprovalResponse(
        val approvalRequestId: String,
        val approve: Boolean,
        val id: String? = null,
        val reason: String? = null,
    ) : Item {
        val type: String = "mcp_approval_response"
    }

    /**
     * An invocation of a tool on an MCP server.
     * @property arguments A JSON string of the arguments passed to the tool.
     * @property id The unique ID of the tool call.
     * @property name The name of the tool that was run.
     * @property serverLabel The label of the MCP server running the tool.
     * @property error The error from the tool call, if any.
     * @property output The output from the tool call.
     */
    @Serializable
    class McpToolCall(
        val arguments: String,
        val id: String,
        val name: String,
        val serverLabel: String,
        val error: String? = null,
        val output: String? = null,
    ) : Item {
        val type: String = "mcp_call"
    }

    /**
     * The output of a custom tool call from your code, being sent back to the model.
     * @property callId The call ID, used to map this custom tool call output to a custom tool call.
     * @property output The output from the custom tool call generated by your code.
     * @property id The unique ID of the custom tool call output in the OpenAI platform.
     */
    @Serializable
    class CustomToolCallOutput(val callId: String, val output: String, val id: String? = null) : Item {
        val type: String = "custom_tool_call_output"
    }

    /**
     * A call to a custom tool created by the model.
     * @property callId An identifier used to map this custom tool call to a tool call output.
     * @property input The input for the custom tool call generated by the model.
     * @property name The name of the custom tool being called.
     * @property id The unique ID of the custom tool call in the OpenAI platform.
     */
    @Serializable
    class CustomToolCall(val callId: String, val input: String, val name: String, val id: String? = null) : Item {
        val type: String = "custom_tool_call"
    }

    /**
     * An internal identifier for an item to reference.
     *
     * @property id The ID of the item to reference.
     */
    @Serializable
    class ItemReference(val id: String) : Item {
        val type: String = "item_reference"
    }
}

@Serializable
@JsonClassDiscriminator("type")
internal sealed interface InputContent {

    /**
     * A text input to the model.
     *
     * @property text The text input to the model.
     */
    @Serializable
    @SerialName("input_text")
    class Text(val text: String) : InputContent

    /**
     * An image input to the model
     *
     * @property detail The detail level of the image to be sent to the model.
     * One of `high`, `low`, or `auto`. Defaults to `auto`.
     * @property fileId The ID of the file to be sent to the model.
     * @property imageUrl The URL of the image to be sent to the model.
     * A fully qualified URL or base64 encoded image in a data URL.
     */
    @Serializable
    @SerialName("input_image")
    class Image(val detail: String = "auto", val fileId: String? = null, val imageUrl: String? = null) : InputContent

    /**
     * A file input to the model
     *
     * @property fileData The content of the file to be sent to the model.
     * @property fileId The ID of the file to be sent to the model.
     * @property fileUrl The URL of the file to be sent to the model.
     * @property filename The name of the file to be sent to the model.
     */
    @Serializable
    @SerialName("input_file")
    class File(
        val fileData: String? = null,
        val fileId: String? = null,
        val fileUrl: String? = null,
        val filename: String? = null
    ) : InputContent
}

@Serializable
@JsonClassDiscriminator("type")
internal sealed interface OutputContent {

    /**
     * A text output from the model.
     *
     * @property annotations
     * @property text The text output from the model.
     */
    @Serializable
    @SerialName("output_text")
    class Text(
        val annotations: List<OpenAIAnnotations>,
        val text: String,
        val logprobs: List<OpenAIChoiceLogProbs.ContentLogProbs>? = null
    ) : OutputContent

    /**
     * A refusal from the model.
     * @property refusal The refusal explanation from the model.
     */
    @Serializable
    @SerialName("refusal")
    class Refusal(val refusal: String) : OutputContent
}

/**
 * The annotations of the text output.
 */
@Serializable
@JsonClassDiscriminator("type")
internal sealed interface OpenAIAnnotations {
    /**
     * A citation to a file.
     *
     * @property fileId The ID of the file.
     * @property filename The filename of the file cited.
     * @property index The index of the file in the list of files.
     */
    @Serializable
    @SerialName("file_citation")
    class FileCitation(val fileId: String, val filename: String, val index: Int) : OpenAIAnnotations

    /**
     * A citation for a web resource used to generate a model response.
     *
     * @property endIndex The index of the last character of the URL citation in the message.
     * @property startIndex The index of the first character of the URL citation in the message.
     * @property title The title of the web resource.
     * @property url The URL of the web resource.
     */
    @Serializable
    @SerialName("url_citation")
    class UrlCitation(val endIndex: Int, val startIndex: Int, val title: String, val url: String) : OpenAIAnnotations

    /**
     * A citation for a container file used to generate a model response.
     *
     * @property containerId The ID of the container file.
     * @property endIndex The index of the last character of the container file citation in the message.
     * @property fileId The ID of the file.
     * @property filename The filename of the container file cited.
     * @property startIndex The index of the first character of the container file citation in the message.
     */
    @Serializable
    @SerialName("container_file_citation")
    class ContainerFileCitation(
        val containerId: String,
        val endIndex: Int,
        val fileId: String,
        val filename: String,
        val startIndex: Int
    ) : OpenAIAnnotations

    /**
     * A path to a file.
     *
     * @property fileId The ID of the file.
     * @property index The index of the file in the list of files.
     */
    @Serializable
    @SerialName("file_path")
    class FilePath(val fileId: String, val index: Int) : OpenAIAnnotations
}

@Serializable
internal enum class OpenAIInputStatus {
    @SerialName("in_progress")
    IN_PROGRESS,

    @SerialName("completed")
    COMPLETED,

    @SerialName("searching")
    SEARCHING,

    @SerialName("failed")
    FAILED,

    @SerialName("interpreting")
    INTERPRETING,

    @SerialName("incomplete")
    INCOMPLETE,

    @SerialName("cancelled")
    CANCELLED,

    @SerialName("queued")
    QUEUED
}

/**
 * The mcp tool
 * @property inputSchema The JSON schema describing the tool's input.
 * @property name The name of the tool.
 * @property annotations Additional annotations about the tool.
 * @property description The description of the tool.
 */
@Serializable
internal class McpTool(
    val inputSchema: JsonObject,
    val name: String,
    val annotations: JsonObject? = null,
    val description: String? = null,
)

/**
 * Reference to a prompt template and its variables
 * @property id The unique identifier of the prompt template to use.
 * @property variables Optional map of values to substitute in for variables in your prompt.
 * The substitution values can either be strings or other Response input types like images or files.
 * @property version Optional version of the prompt template.
 */
@Serializable
internal class OpenAIPromptReference(
    val id: String,
    val variables: Map<String, String>? = null,
    val version: String? = null
)

/**
 * Configuration options for reasoning models.
 * @property effort Constrains effort on reasoning for reasoning models.
 * Currently supported values are `minimal`, `low`, `medium`, and `high`.
 * Reducing reasoning effort can result in faster responses and fewer tokens used on reasoning in a response.
 * @property summary A summary of the reasoning performed by the model.
 * This can be useful for debugging and understanding the model's reasoning process. One of `auto`, `concise`,
 * or `detailed`.
 */
@Serializable
public class ReasoningConfig(public val effort: ReasoningEffort? = null, public val summary: ReasoningSummary? = null)

/**
 * Represents different levels of reasoning summary that can be used to specify the desired detail
 * in responses.
 *
 * The levels include:
 * - AUTO: Automatically determines the level of reasoning detail.
 * - CONCISE: Provides a brief and to-the-point reasoning.
 * - DETAILED: Provides extensive and thorough reasoning.
 */
@Serializable
public enum class ReasoningSummary {
    @SerialName("auto")
    AUTO,

    @SerialName("concise")
    CONCISE,

    @SerialName("detailed")
    DETAILED
}

/**
 * Options for streaming responses.
 * @property includeObfuscation When true, stream obfuscation will be enabled.
 * Stream obfuscation adds random characters to an `obfuscation` field on streaming delta events to normalize
 * payload sizes as mitigation to certain side-channel attacks.
 * These obfuscation fields are included by default but add a small amount of overhead to the data stream.
 * You can set `include_obfuscation` to false to optimize for bandwidth if you trust the network links
 * between your application and the OpenAI API.
 */
@Serializable
internal class OpenAIResponsesAPIStreamOptions(val includeObfuscation: Boolean? = null)

/**
 * Configuration options for a text response from the model. Can be plain text or structured JSON data
 * @property format An object specifying the format that the model must output.
 * @property verbosity Constrains the verbosity of the model's response.
 * Lower values will result in more concise responses,
 * while higher values will result in more verbose responses. Currently supported values are `low`, `medium`, and `high`
 */
@Serializable
internal class OpenAITextConfig(val format: OpenAIOutputFormat? = null, val verbosity: TextVerbosity? = null)

@Serializable
@JsonClassDiscriminator("type")
internal sealed interface OpenAIOutputFormat {
    /**
     * Default response format. Used to generate text responses.
     */
    @Serializable
    @SerialName("text")
    class Text() : OpenAIOutputFormat

    /**
     * JSON Schema response format. Used to generate structured JSON responses
     * @property name The name of the response format.
     * Must be a-z, A-Z, 0-9, or contain underscores and dashes, with a maximum length of 64.
     * @property schema The schema for the response format, described as a JSON Schema object.
     * @property description A description of what the response format is for,
     * used by the model to determine how to respond in the format.
     * @property strict Whether to enable strict schema adherence when generating the output.
     * If set to true, the model will always follow the exact schema defined in the `schema` field.
     * Only a subset of JSON Schema is supported when `strict` is `true`
     */
    @Serializable
    @SerialName("json_schema")
    class JsonSchema(
        val name: String,
        val schema: kotlinx.serialization.json.JsonObject,
        val description: String? = null,
        val strict: Boolean? = null
    ) : OpenAIOutputFormat

    /**
     * JSON object response format.
     * An older method of generating JSON responses.
     * Using `json_schema` is recommended for models that support it.
     * Note that the model will not generate JSON without a system or user message instructing it to do so.
     */
    @Serializable
    @SerialName("json_object")
    class JsonObject() : OpenAIOutputFormat
}

/**
 * Represents the verbosity level for text output.
 *
 * The verbosity levels determine the amount of detail included in the text.
 */
@Serializable
public enum class TextVerbosity {
    @SerialName("low")
    LOW,

    @SerialName("medium")
    MEDIUM,

    @SerialName("high")
    HIGH
}

@Serializable(with = OpenAIResponsesToolChoiceSerializer::class)
internal sealed interface OpenAIResponsesToolChoice {
    /**
     * Controls which (if any) tool is called by the model.
     *
     * - `none` means the model will not call any tool and instead generates a message.
     * - `auto` means the model can pick between generating a message or calling one or more tools.
     * - `required` means the model must call one or more tools.
     */
    @JvmInline
    @Serializable
    value class Mode(val mode: String) : OpenAIResponsesToolChoice

    /**
     * Constrains the tools available to the model to a pre-defined set.
     * @property mode Constrains the tools available to the model to a pre-defined set.
     *
     * - `auto` allows the model to pick from among the allowed tools and generate a message.
     * - `required` requires the model to call one or more of the allowed tools.
     * @property tools A list of tool definitions that the model should be allowed to call.
     *
     * For the Responses API, the list of tool definitions might look like:
     * ```json
     * [
     *   { "type": "function", "name": "get_weather" },
     *   { "type": "mcp", "server_label": "deepwiki" },
     *   { "type": "image_generation" }
     * ]
     * ```
     * @property type Allowed tool configuration type. Always `allowed_tools`.
     */
    @Serializable
    class AllowedTools(val mode: String, val tools: List<JsonObject>) : OpenAIResponsesToolChoice {
        val type: String = "allowed_tools"
    }

    /**
     * Indicates that the model should use a built-in tool to generate a response.
     * @property type The type of hosted tool the model should to use.
     *
     * Allowed values are:
     *
     * - `file_search`
     * - `web_search_preview`
     * - `computer_use_preview`
     * - `code_interpreter`
     * - `image_generation`
     */
    @Serializable
    class HostedTool(val type: String) : OpenAIResponsesToolChoice

    /**
     * Use this option to force the model to call a specific function.
     * @property name The name of the function to call.
     * @property type For function calling, the type is always `function`.
     */
    @Serializable
    class FunctionTool(val name: String) : OpenAIResponsesToolChoice {
        val type: String = "function"
    }

    /**
     * Use this option to force the model to call a specific tool on a remote MCP
     * @property serverLabel The label of the MCP server to use.
     * @property name The name of the tool to call on the server.
     * @property type For MCP tools, the type is always `mcp`.
     */
    @Serializable
    class McpTool(val serverLabel: String, val name: String? = null) : OpenAIResponsesToolChoice {
        val type: String = "mcp"
    }

    /**
     * Use this option to force the model to call a specific custom tool.
     * @property name The name of the custom tool to call.
     * @property type For custom tool calling, the type is always `custom`.
     */
    @Serializable
    class CustomTool(val name: String) : OpenAIResponsesToolChoice {
        val type: String = "custom"
    }
}

@Serializable
@JsonClassDiscriminator("type")
internal sealed interface OpenAIResponsesTool {
    /**
     * Defines a function in your own code the model can choose to call
     * @property name The name of the function to call.
     * @property parameters A JSON schema object describing the parameters of the function.
     * @property strict Whether to enforce strict parameter validation. Default `true`.
     * @property description A description of the function.
     * Used by the model to determine whether or not to call the function.
     */
    @Serializable
    @SerialName("function")
    class Function(
        val name: String,
        val parameters: JsonObject,
        val strict: Boolean? = null,
        val description: String? = null,
    ) : OpenAIResponsesTool

    /**
     * A tool that searches for relevant content from uploaded files.
     * @property vectorStoreIds The IDs of the vector stores to search.
     * @property filters A filter to apply.
     * @property maxNumResults The maximum number of results to return. This number should be between 1 and 50 inclusive.
     * @property rankingOptions Ranking options for search.
     */
    @Serializable
    @SerialName("file_search")
    class FileSearch(
        val vectorStoreIds: List<String>,
        val filters: JsonObject? = null,
        val maxNumResults: Int? = null,
        val rankingOptions: JsonObject? = null,
    ) : OpenAIResponsesTool

    /**
     * This tool searches the web for relevant results to use in a response.
     * @property searchContextSize High level guidance for the amount of context window space to use for the search.
     * One of `low`, `medium`, or `high`. `medium` is the default.
     * @property userLocation The user's location.
     */
    @Serializable
    @SerialName("web_search_preview")
    class WebSearchPreview(
        val searchContextSize: String? = null,
        val userLocation: UserLocation? = null
    ) : OpenAIResponsesTool {
        /**
         * User location
         * @property city Free text input for the city of the user, e.g. `San Francisco`.
         * @property country The two-letter ISO country code of the user, e.g. `US`.
         * @property region Free text input for the region of the user, e.g. `California`.
         * @property timezone The IANA timezone of the user, e.g. `America/Los_Angeles`.
         */
        @Serializable
        class UserLocation(
            val city: String? = null,
            val country: String? = null,
            val region: String? = null,
            val timezone: String? = null
        ) {
            val type: String = "approximate"
        }
    }

    /**
     * A tool that controls a virtual computer
     * @property displayHeight The height of the computer display.
     * @property displayWidth The width of the computer display.
     * @property environment The type of computer environment to control.
     */
    @Serializable
    @SerialName("computer_use_preview")
    class ComputerUsePreview(
        val displayHeight: Int,
        val displayWidth: Int,
        val environment: String,
    ) : OpenAIResponsesTool

    /**
     * Give the model access to additional tools via remote Model Context Protocol (MCP) servers.
     * @property serverLabel A label for this MCP server, used to identify it in tool calls.
     * @property serverUrl The URL for the MCP server.
     * @property allowedTools List of allowed tool names or a filter object.
     * @property headers Optional HTTP headers to send to the MCP server. Use for authentication or other purposes.
     * @property requireApproval Specify which of the MCP server's tools require approval.
     * @property serverDescription Optional description of the MCP server, used to provide more context.
     */
    @Serializable
    @SerialName("mcp")
    class McpTool(
        val serverLabel: String,
        val serverUrl: String,
        val allowedTools: List<String>? = null,
        val headers: JsonObject? = null,
        val requireApproval: String? = null,
        val serverDescription: String? = null
    ) : OpenAIResponsesTool

    /**
     * A tool that runs Python code to help generate a response to a prompt.
     * @property container The code interpreter container.
     * Can be a container ID or an object that specifies uploaded file IDs to make available to your code.
     */
    @Serializable
    @SerialName("code_interpreter")
    class CodeInterpreter(val container: String) : OpenAIResponsesTool

    /**
     * A tool that generates images using a model like `gpt-image-1`.
     * @property background Background type for the generated image. One of `transparent`, `opaque`, or `auto`.
     * Default: `auto`.
     * @property inputFidelity Control how much effort the model will exert to match the style and features,
     * especially facial features, of input images.
     * This parameter is only supported for `gpt-image-1`. Supports `high` and `low`. Defaults to `low`.
     * @property inputImageMask Optional mask for inpainting.
     * Contains `image_url` (string, optional) and `file_id` (string, optional).
     * @property model The image generation model to use. Default: `gpt-image-1`.
     * @property moderation Moderation level for the generated image. Default: `auto`.
     * @property outputCompression Compression level for the output image. Default: 100.
     * @property outputFormat The output format of the generated image. One of `png`, `webp`, or `jpeg`. Default: `png`.
     * @property partialImages Number of partial images to generate in streaming mode, from 0 (default value) to 3.
     * @property quality The quality of the generated image. One of `low`, `medium`, `high`, or `auto`. Default: `auto`.
     * @property size The size of the generated image. One of `1024x1024`, `1024x1536`, `1536x1024`, or `auto`.
     * Default: `auto`.
     */
    @Serializable
    @SerialName("image_generation")
    class ImageGenerationTool(
        val background: String? = null,
        val inputFidelity: String? = null,
        val inputImageMask: JsonObject? = null,
        val model: String? = null,
        val moderation: String? = null,
        val outputCompression: Int? = null,
        val outputFormat: String? = null,
        val partialImages: Int? = null,
        val quality: String? = null,
        val size: String? = null,
    ) : OpenAIResponsesTool

    /**
     * A tool that allows the model to execute shell commands in a local environment.
     */
    @Serializable
    @SerialName("local_shell")
    class LocalShellTool() : OpenAIResponsesTool

    /**
     * A custom tool that processes input using a specified format.
     * @property name The name of the custom tool, used to identify it in tool calls.
     * @property description Optional description of the custom tool, used to provide more context.
     * @property format The input format for the custom tool. Default is unconstrained text.
     */
    @Serializable
    @SerialName("custom")
    class CustomTool(
        val name: String,
        val description: String? = null,
        val format: Format? = null
    ) : OpenAIResponsesTool {
        @Serializable
        sealed interface Format {
            /**
             * Unconstrained free-form text.
             */
            @Serializable
            @SerialName("text")
            class Text() : Format

            /**
             * A grammar defined by the user.
             * @property definition The grammar definition.
             * @property syntax The syntax of the grammar definition. One of `lark` or `regex`.
             */
            @Serializable
            @SerialName("grammar")
            class Grammar(val definition: String, val syntax: String) : Format
        }
    }
}

/**
 * Represents the truncation behavior for processing inputs.
 *
 * This enum defines the following modes:
 * - AUTO: Automatically handles truncation of inputs based on predefined logic.
 * - DISABLED: Disables truncation, requiring inputs to fit within allowed limits.
 */
@Serializable
public enum class Truncation {
    @SerialName("auto")
    AUTO,

    @SerialName("disabled")
    DISABLED
}

/**
 * OpenAI Responses API Response
 *
 * @property background Whether to run the model response in the background
 * @property created Unix timestamp (in seconds) of when this Response was created.
 * @property error An error object returned when the model fails to generate a Response.
 * @property id Unique identifier for this Response.
 * @property incompleteDetails Details about why the response is incomplete.
 * @property instructions A system (or developer) message inserted into the model's context.
 *
 * When using along with `previous_response_id`,
 * the instructions from a previous response will not be carried over to the next response.
 * This makes it simple to swap out system (or developer) messages in new responses.
 * @property maxOutputTokens An upper bound for the number of tokens that can be generated for a response,
 * including visible output tokens and reasoning tokens.
 * @property maxToolCalls The maximum number of total calls to built-in tools that can be processed in a response.
 * This maximum number applies across all built-in tool calls, not per individual tool.
 * Any further attempts to call a tool by the model will be ignored.
 * @property metadata Set of 16 key-value pairs that can be attached to an object.
 * This can be useful for storing additional information about the object in a structured format
 * and querying for objects via API or the dashboard.
 *
 * Keys are strings with a maximum length of 64 characters. Values are strings with a maximum length of 512 characters.
 * @property model Model ID used to generate the response, like `gpt-4o` or `o3`.
 * OpenAI offers a wide range of models with different capabilities, performance characteristics, and price points.
 * @property objectType The object type of this resource - always set to `response`.
 * @property output An array of content items generated by the model.
 *
 * - The length and order of items in the `output` array is dependent on the model's response.
 * - Rather than accessing the first item in the output array and assuming it's an assistant message with the content generated by the model, you might consider using the output_text property where supported in SDKs.
 * @property outputText SDK-only convenience property that contains the aggregated text
 * output from all `output_text` items in the `output` array, if any are present
 * @property parallelToolCalls Whether to allow the model to run tool calls in parallel.
 * @property previousResponseId The unique ID of the previous response to the model.
 * Use this to create multi-turn conversations.
 * @property prompt Reference to a prompt template and its variables.
 * @property promptCacheKey sed by OpenAI to cache responses for similar requests to optimize your cache hit rates.
 * Replaces the [user] field.
 * @property reasoning (**gpt-5 and o-series models only**) Configuration options for reasoning models.
 * @property safetyIdentifier A stable identifier used to help detect users of your application
 * that may be violating OpenAI's usage policies. The IDs should be a string that uniquely identifies each user.
 * We recommend hashing their username or email address in order to avoid sending us any identifying information
 * @property serviceTier Specifies the processing type used for serving the request.
 * @property status The status of the response generation.
 * One of `completed`, `failed`, `in_progress`, `cancelled`, `queued`, or `incomplete`.
 * @property temperature What sampling temperature to use, between 0 and 2.
 * Higher values like 0.8 will make the output more random,
 * while lower values like 0.2 will make it more focused and deterministic.
 * We generally recommend altering this or [topP] but not both.
 * @property text Configuration options for a text response from the model. Can be plain text or structured JSON data.
 * @property toolChoice How the model should select which tool (or tools) to use when generating a response.
 * See the [tools] parameter to see how to specify which tools the model can call.
 * @property tools An array of tools the model may call while generating a response.
 * You can specify which tool to use by setting the [toolChoice] parameter.
 *
 * The two categories of tools you can provide the model are:
 *
 * - Built-in tools: Tools that are provided by OpenAI that extend the model's capabilities,
 * like web search or file search.
 * - Function calls (custom tools): Functions that are defined by you, enabling the model to call your own code
 * with strongly typed arguments and outputs. You can also use custom tools to call your own code.
 * @property topLogprobs An integer between 0 and 20 specifying the number of most likely tokens to return
 * at each token position, each with an associated log probability.
 * @property topP An alternative to sampling with temperature, called nucleus sampling,
 * where the model considers the results of the tokens with top_p probability mass.
 * So 0.1 means only the tokens comprising the top 10% probability mass are considered.
 *
 * We generally recommend altering this or [temperature] but not both.
 * @property truncation The truncation strategy to use for the model response.
 *
 * - `auto`: If the context of this response and previous ones exceeds the model's context window size,
 * the model will truncate the response to fit the context window by dropping input items in the middle of the conversation.
 * - `disabled` (default): If a model response will exceed the context window size for a model,
 * the request will fail with a 400 error.
 * @property usage Represents token usage details including input tokens, output tokens,
 * a breakdown of output tokens, and the total tokens used.
 * @property user This field is being replaced by [safetyIdentifier] and [promptCacheKey].
 * Use [promptCacheKey] instead to maintain caching optimizations. A stable identifier for your end-users.
 * Used to boost cache hit rates by better bucketing similar requests and to help OpenAI detect and prevent abuse
 *
 * @see <a href="https://platform.openai.com/docs/api-reference/responses/object">Responses API Response</a>
 */
@Serializable
internal class OpenAIResponsesAPIResponse(
    val background: Boolean? = null,
    @SerialName("created_at")
    override val created: Long,
    val error: ResponseError? = null,
    override val id: String,
    val incompleteDetails: IncompleteDetails? = null,
    val instructions: List<Item>? = null,
    val maxOutputTokens: Int? = null,
    val maxToolCalls: Int? = null,
    val metadata: Map<String, String>? = null,
    override val model: String,
    @SerialName("object")
    val objectType: String = "response",
    val output: List<Item>,
    val outputText: String? = null,
    val parallelToolCalls: Boolean,
    val previousResponseId: String? = null,
    val prompt: OpenAIPromptReference? = null,
    val promptCacheKey: String? = null,
    val reasoning: ReasoningConfig? = null,
    val safetyIdentifier: String? = null,
    val serviceTier: ServiceTier? = null,
    val status: OpenAIInputStatus,
    val temperature: Double? = null,
    val text: OpenAITextConfig,
    val toolChoice: OpenAIResponsesToolChoice? = null,
    val tools: List<OpenAIResponsesTool>? = null,
    val topLogprobs: Int? = null,
    val topP: Double? = null,
    val truncation: String? = null,
    val usage: Usage? = null,
    @Deprecated("Use safetyIdentifier and promptCacheKey instead")
    val user: String? = null,
) : OpenAIBaseLLMResponse {
    /**
     * @property code The error code for the response.
     * @property message A human-readable description of the error.
     */
    @Serializable
    internal class ResponseError(val code: String, val message: String)

    /**
     * Details about why the response is incomplete.
     * @property reason The reason why the response is incomplete.
     */
    @Serializable
    internal class IncompleteDetails(val reason: String)

    /**
     * Represents token usage details including input tokens,
     * output tokens, a breakdown of output tokens, and the total tokens used.
     * @property inputTokens The number of input tokens.
     * @property inputTokensDetails A detailed breakdown of the input tokens.
     * @property outputTokens The number of output tokens.
     * @property outputTokensDetails A detailed breakdown of the output tokens.
     * @property totalTokens The total number of tokens used.
     */
    @Serializable
    internal class Usage(
        val inputTokens: Int,
        val inputTokensDetails: InputTokensDetails,
        val outputTokens: Int,
        val outputTokensDetails: OutputTokensDetails,
        val totalTokens: Int
    ) {
        @Serializable
        class InputTokensDetails(val cachedTokens: Int)

        @Serializable
        class OutputTokensDetails(val reasoningTokens: Int)
    }
}

/**
 * OpenAI Responses API Streaming Event
 */
@Serializable
@JsonClassDiscriminator("type")
internal sealed interface OpenAIStreamEvent {

    /**
     * An event that is emitted when a response is created.
     * @property response The response that was created.
     * @property sequenceNumber The sequence number for this event.
     */
    @Serializable
    @SerialName("response.created")
    class ResponseCreated(
        val response: OpenAIResponsesAPIResponse,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when the response is in progress.
     * @property response The response that is in progress.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.in_progress")
    class ResponseInProgress(
        val response: OpenAIResponsesAPIResponse,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when the model response is complete.
     * @property response Properties of the completed response.
     * @property sequenceNumber The sequence number for this event.
     */
    @Serializable
    @SerialName("response.completed")
    class ResponseCompleted(
        val response: OpenAIResponsesAPIResponse,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * An event that is emitted when a response fails.
     * @property response The response that failed.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.failed")
    class ResponseFailed(
        val response: OpenAIResponsesAPIResponse,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * An event that is emitted when a response finishes as incomplete.
     * @property response The response that was incomplete.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.incomplete")
    class ResponseIncomplete(
        val response: OpenAIResponsesAPIResponse,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * An event that is emitted when a response finishes as incomplete.
     * @property item The output item that was added.
     * @property outputIndex The index of the output item that was added.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.output_item.added")
    class ResponseOutputItemAdded(
        val item: Item,
        val outputIndex: Int,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * An event that is emitted when a response finishes as incomplete.
     * @property item The output item that was marked done.
     * @property outputIndex The index of the output item that was marked done.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.output_item.done")
    class ResponseOutputItemDone(
        val item: Item,
        val outputIndex: Int,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when a new content part is added.
     * @property itemId The ID of the output item that the content part was added to.
     * @property outputIndex The index of the output item that the content part was added to.
     * @property contentIndex The index of the content part that was added.
     * @property part The content part that was added.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.content_part.added")
    class ResponseContentPartAdded(
        val itemId: String,
        val outputIndex: Int,
        val contentIndex: Int,
        val part: OutputContent,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when a content part is done.
     * @property itemId The ID of the output item that the content part was added to.
     * @property outputIndex The index of the output item that the content part was added to.
     * @property contentIndex The index of the content part that is done.
     * @property part The content part that is done.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.content_part.done")
    class ResponseContentPartDone(
        val itemId: String,
        val outputIndex: Int,
        val contentIndex: Int,
        val part: OutputContent,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when there is an additional text delta.
     * @property itemId The ID of the output item that the text delta was added to.
     * @property outputIndex The index of the output item that the text delta was added to.
     * @property contentIndex The index of the content part that the text delta was added to.
     * @property delta The text delta that was added.
     * @property logprobs The log probabilities of the tokens in the delta.
     * @property sequenceNumber The sequence number for this event.
     */
    @Serializable
    @SerialName("response.output_text.delta")
    class ResponseOutputTextDelta(
        val itemId: String,
        val outputIndex: Int,
        val contentIndex: Int,
        val delta: String,
        val logprobs: List<LogProbWithTop>? = null,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when text content is finalized.
     * @property itemId The ID of the output item that the text content is finalized.
     * @property outputIndex The index of the output item that the text content is finalized.
     * @property contentIndex The index of the content part that the text content is finalized.
     * @property text The text content that is finalized.
     * @property logprobs The log probabilities of the tokens in the delta.
     * @property sequenceNumber The sequence number for this event.
     */
    @Serializable
    @SerialName("response.output_text.done")
    class ResponseOutputTextDone(
        val itemId: String,
        val outputIndex: Int,
        val contentIndex: Int,
        val text: String,
        val logprobs: List<LogProbWithTop>? = null,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when there is a partial refusal text.
     * @property itemId The ID of the output item that the refusal text is added to.
     * @property outputIndex The index of the output item that the refusal text is added to.
     * @property contentIndex The index of the content part that the refusal text is added to.
     * @property delta The refusal text that is added.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.refusal.delta")
    class ResponseRefusalDelta(
        val itemId: String,
        val outputIndex: Int,
        val contentIndex: Int,
        val delta: String,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when refusal text is finalized.
     * @property itemId The ID of the output item that the refusal text is finalized.
     * @property outputIndex The index of the output item that the refusal text is finalized.
     * @property contentIndex The index of the content part that the refusal text is finalized.
     * @property refusal The refusal text that is finalized.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.refusal.done")
    class ResponseRefusalDone(
        val itemId: String,
        val outputIndex: Int,
        val contentIndex: Int,
        val refusal: String,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when there is a partial function-call arguments delta.
     * @property itemId The ID of the output item that the function-call arguments delta is added to.
     * @property outputIndex The index of the output item that the function-call arguments delta is added to.
     * @property delta The function-call arguments delta that is added.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.function_call_arguments.delta")
    class ResponseFunctionCallArgumentsDelta(
        val itemId: String,
        val outputIndex: Int,
        val delta: String,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when function-call arguments are finalized.
     * @property itemId The ID of the item.
     * @property outputIndex The index of the output item.
     * @property arguments The function-call arguments.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.function_call_arguments.done")
    class ResponseFunctionCallArgumentsDone(
        val itemId: String,
        val outputIndex: Int,
        val arguments: String,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when a file search call is initiated.
     * @property itemId The ID of the output item that the file search call is initiated.
     * @property outputIndex The index of the output item that the file search call is initiated.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.file_search_call.in_progress")
    class ResponseFileSearchCallInProgress(
        val itemId: String,
        val outputIndex: Int,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when a file search is currently searching.
     * @property itemId The ID of the output item that the file search call is initiated.
     * @property outputIndex The index of the output item that the file search call is searching.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.file_search_call.searching")
    class ResponseFileSearchCallSearching(
        val itemId: String,
        val outputIndex: Int,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when a file search call is completed (results found).
     * @property itemId The ID of the output item that the file search call is initiated.
     * @property outputIndex The index of the output item that the file search call is initiated.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.file_search_call.completed")
    class ResponseFileSearchCallCompleted(
        val itemId: String,
        val outputIndex: Int,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when a web search call is initiated.
     * @property itemId Unique ID for the output item associated with the web search call.
     * @property outputIndex The index of the output item that the web search call is associated with.
     * @property sequenceNumber The sequence number of the web search call being processed.
     */
    @Serializable
    @SerialName("response.web_search_call.in_progress")
    class ResponseWebSearchCallInProgress(
        val itemId: String,
        val outputIndex: Int,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when a web search call is executing.
     * @property itemId Unique ID for the output item associated with the web search call.
     * @property outputIndex The index of the output item that the web search call is associated with.
     * @property sequenceNumber The sequence number of the web search call being processed.
     */
    @Serializable
    @SerialName("response.web_search_call.searching")
    class ResponseWebSearchCallSearching(
        val itemId: String,
        val outputIndex: Int,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when a web search call is completed.
     * @property itemId Unique ID for the output item associated with the web search call.
     * @property outputIndex The index of the output item that the web search call is associated with.
     * @property sequenceNumber The sequence number of the web search call being processed.
     */
    @Serializable
    @SerialName("response.web_search_call.completed")
    class ResponseWebSearchCallCompleted(
        val itemId: String,
        val outputIndex: Int,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when a new reasoning summary part is added.
     * @property itemId The ID of the item this summary part is associated with.
     * @property outputIndex The index of the output item this summary part is associated with.
     * @property summaryIndex The index of the summary part within the reasoning summary.
     * @property part The summary part that was added.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.reasoning_summary_part.added")
    class ResponseReasoningSummaryPartAdded(
        val itemId: String,
        val outputIndex: Int,
        val summaryIndex: Int,
        val part: SummaryPart,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when a reasoning summary part is completed.
     * @property itemId The ID of the item this summary part is associated with.
     * @property outputIndex The index of the output item this summary part is associated with.
     * @property summaryIndex The index of the summary part within the reasoning summary.
     * @property part The completed summary part.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.reasoning_summary_part.done")
    class ResponseReasoningSummaryPartDone(
        val itemId: String,
        val outputIndex: Int,
        val summaryIndex: Int,
        val part: SummaryPart,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when a delta is added to a reasoning summary text.
     * @property itemId The ID of the item this summary text delta is associated with.
     * @property outputIndex The index of the output item this summary text delta is associated with.
     * @property summaryIndex The index of the summary part within the reasoning summary.
     * @property delta The text delta that was added to the summary.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.reasoning_summary_text.delta")
    class ResponseReasoningSummaryTextDelta(
        val itemId: String,
        val outputIndex: Int,
        val summaryIndex: Int,
        val delta: String,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when a reasoning summary text is completed.
     * @property itemId The ID of the item this summary text is associated with.
     * @property outputIndex The index of the output item this summary text is associated with.
     * @property summaryIndex The index of the summary part within the reasoning summary.
     * @property text The full text of the completed reasoning summary.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.reasoning_summary_text.done")
    class ResponseReasoningSummaryTextDone(
        val itemId: String,
        val outputIndex: Int,
        val summaryIndex: Int,
        val text: String,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when a delta is added to a reasoning text.
     * @property itemId The ID of the item this reasoning text delta is associated with.
     * @property outputIndex The index of the output item this reasoning text delta is associated with.
     * @property contentIndex The index of the reasoning content part this delta is associated with.
     * @property delta The text delta that was added to the reasoning content.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.reasoning_text.delta")
    class ResponseReasoningTextDelta(
        val itemId: String,
        val outputIndex: Int,
        val contentIndex: Int,
        val delta: String,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when a reasoning text is completed.
     * @property itemId The ID of the item this reasoning text is associated with.
     * @property outputIndex The index of the output item this reasoning text is associated with.
     * @property contentIndex The index of the reasoning content part.
     * @property text The full text of the completed reasoning content.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.reasoning_text.done")
    class ResponseReasoningTextDone(
        val itemId: String,
        val outputIndex: Int,
        val contentIndex: Int,
        val text: String,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when an image generation tool call has completed and the final image is available.
     * @property itemId The unique identifier of the image generation item being processed.
     * @property outputIndex The index of the output item in the response's output array.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.image_generation_call.completed")
    class ResponseImageGenerationCallCompleted(
        val itemId: String,
        val outputIndex: Int,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when an image generation tool call is actively generating an image (intermediate state).
     * @property itemId The unique identifier of the image generation item being processed.
     * @property outputIndex The index of the output item in the response's output array.
     * @property sequenceNumber The sequence number of the image generation item being processed.
     */
    @Serializable
    @SerialName("response.image_generation_call.generating")
    class ResponseImageGenerationCallGenerating(
        val itemId: String,
        val outputIndex: Int,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when an image generation tool call is in progress.
     * @property itemId The unique identifier of the image generation item being processed.
     * @property outputIndex The index of the output item in the response's output array.
     * @property sequenceNumber The sequence number of the image generation item being processed.
     */
    @Serializable
    @SerialName("response.image_generation_call.in_progress")
    class ResponseImageGenerationCallInProgress(
        val itemId: String,
        val outputIndex: Int,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when a partial image is available during image generation streaming.
     * @property itemId The unique identifier of the image generation item being processed.
     * @property outputIndex The index of the output item in the response's output array.
     * @property partialImageIndex 0-based index for the partial image.
     * @property partialImageB64 Base64-encoded partial image data, suitable for rendering as an image.
     * @property sequenceNumber The sequence number of the image generation item being processed.
     */
    @Serializable
    @SerialName("response.image_generation_call.partial_image")
    class ResponseImageGenerationCallPartialImage(
        val itemId: String,
        val outputIndex: Int,
        val partialImageIndex: Int,
        val partialImageB64: String,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when there is a delta (partial update) to the arguments of an MCP tool call.
     * @property itemId The unique identifier of the MCP tool call item being processed.
     * @property outputIndex The index of the output item in the response's output array.
     * @property delta A JSON string containing the partial update to the arguments for the MCP tool call.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.mcp_call_arguments.delta")
    class ResponseMcpCallArgumentsDelta(
        val itemId: String,
        val outputIndex: Int,
        val delta: String,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when the arguments for an MCP tool call are finalized.
     * @property itemId The unique identifier of the MCP tool call item being processed.
     * @property outputIndex The index of the output item in the response's output array.
     * @property arguments A JSON string containing the finalized arguments for the MCP tool call.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.mcp_call_arguments.done")
    class ResponseMcpCallArgumentsDone(
        val itemId: String,
        val outputIndex: Int,
        val arguments: String,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when an MCP tool call has completed successfully.
     * @property itemId The ID of the MCP tool call item that completed.
     * @property outputIndex The index of the output item that completed.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.mcp_call.completed")
    class ResponseMcpCallCompleted(
        val itemId: String,
        val outputIndex: Int,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when an MCP tool call has failed.
     * @property itemId The ID of the MCP tool call item that failed.
     * @property outputIndex The index of the output item that failed.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.mcp_call.failed")
    class ResponseMcpCallFailed(
        val itemId: String,
        val outputIndex: Int,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when an MCP tool call is in progress.
     * @property itemId The unique identifier of the MCP tool call item being processed.
     * @property outputIndex The index of the output item in the response's output array.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.mcp_call.in_progress")
    class ResponseMcpCallInProgress(
        val itemId: String,
        val outputIndex: Int,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when the list of available MCP tools has been successfully retrieved.
     * @property itemId The ID of the MCP tool call item that produced this output.
     * @property outputIndex The index of the output item that was processed.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.mcp_list_tools.completed")
    class ResponseMcpListToolsCompleted(
        val itemId: String,
        val outputIndex: Int,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when the attempt to list available MCP tools has failed.
     * @property itemId The ID of the MCP tool call item that failed.
     * @property outputIndex The index of the output item that failed.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.mcp_list_tools.failed")
    class ResponseMcpListToolsFailed(
        val itemId: String,
        val outputIndex: Int,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when the system is in the process of retrieving the list of available MCP tools.
     * @property itemId The ID of the MCP tool call item that is being processed.
     * @property outputIndex The index of the output item that is being processed.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.mcp_list_tools.in_progress")
    class ResponseMcpListToolsInProgress(
        val itemId: String,
        val outputIndex: Int,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when a code interpreter call is in progress.
     * @property itemId The unique identifier of the code interpreter tool call item.
     * @property outputIndex The index of the output item in the response for which the code interpreter call is in progress.
     * @property sequenceNumber The sequence number of this event, used to order streaming events.
     */
    @Serializable
    @SerialName("response.code_interpreter_call.in_progress")
    class ResponseCodeInterpreterCallInProgress(
        val itemId: String,
        val outputIndex: Int,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when the code interpreter is actively interpreting the code snippet.
     * @property itemId The unique identifier of the code interpreter tool call item.
     * @property outputIndex The index of the output item in the response for which the code interpreter is interpreting code.
     * @property sequenceNumber The sequence number of this event, used to order streaming events.
     */
    @Serializable
    @SerialName("response.code_interpreter_call.interpreting")
    class ResponseCodeInterpreterCallInterpreting(
        val itemId: String,
        val outputIndex: Int,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when the code interpreter call is completed.
     * @property itemId The unique identifier of the code interpreter tool call item.
     * @property outputIndex The index of the output item in the response for which the code interpreter call is completed.
     * @property sequenceNumber The sequence number of this event, used to order streaming events.
     */
    @Serializable
    @SerialName("response.code_interpreter_call.completed")
    class ResponseCodeInterpreterCallCompleted(
        val itemId: String,
        val outputIndex: Int,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when a partial code snippet is streamed by the code interpreter.
     * @property itemId The unique identifier of the code interpreter tool call item.
     * @property outputIndex The index of the output item in the response for which the code is being streamed.
     * @property delta The partial code snippet being streamed by the code interpreter.
     * @property sequenceNumber The sequence number of this event, used to order streaming events.
     */
    @Serializable
    @SerialName("response.code_interpreter_call_code.delta")
    class ResponseCodeInterpreterCallCodeDelta(
        val itemId: String,
        val outputIndex: Int,
        val delta: String,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when the code snippet is finalized by the code interpreter.
     * @property itemId The unique identifier of the code interpreter tool call item.
     * @property outputIndex The index of the output item in the response for which the code is finalized.
     * @property code The final code snippet output by the code interpreter.
     * @property sequenceNumber The sequence number of this event, used to order streaming events.
     */
    @Serializable
    @SerialName("response.code_interpreter_call_code.done")
    class ResponseCodeInterpreterCallCodeDone(
        val itemId: String,
        val outputIndex: Int,
        val code: String,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when an annotation is added to output text content.
     * @property itemId The unique identifier of the item to which the annotation is being added.
     * @property outputIndex The index of the output item in the response's output array.
     * @property contentIndex The index of the content part within the output item.
     * @property annotationIndex The index of the annotation within the content part.
     * @property annotation The annotation object being added.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.output_text.annotation.added")
    class ResponseOutputTextAnnotationAdded(
        val itemId: String,
        val outputIndex: Int,
        val contentIndex: Int,
        val annotationIndex: Int,
        val annotation: JsonObject,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when a response is queued and waiting to be processed.
     * @property response The full response object that is queued.
     * @property sequenceNumber The sequence number for this event.
     */
    @Serializable
    @SerialName("response.queued")
    class ResponseQueued(
        val response: OpenAIResponsesAPIResponse,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Event representing a delta (partial update) to the input of a custom tool call.
     * @property itemId Unique identifier for the API item associated with this event.
     * @property outputIndex The index of the output this delta applies to.
     * @property delta The incremental input data (delta) for the custom tool call.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.custom_tool_call_input.delta")
    class ResponseCustomToolCallInputDelta(
        val itemId: String,
        val outputIndex: Int,
        val delta: String,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Event indicating that input for a custom tool call is complete.
     * @property itemId Unique identifier for the API item associated with this event.
     * @property outputIndex The index of the output this event applies to.
     * @property input The complete input data for the custom tool call.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("response.custom_tool_call_input.done")
    class ResponseCustomToolCallInputDone(
        val itemId: String,
        val outputIndex: Int,
        val input: String,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    /**
     * Emitted when an error occurs.
     * @property code The error code.
     * @property message The error message.
     * @property param The error parameter.
     * @property sequenceNumber The sequence number of this event.
     */
    @Serializable
    @SerialName("error")
    class Error(
        val code: String? = null,
        val message: String,
        val param: String? = null,
        val sequenceNumber: Int,
    ) : OpenAIStreamEvent

    @Serializable
    class LogProbWithTop(val logprob: Double, val token: String, val topLogprobs: List<LogProb>)

    @Serializable
    class LogProb(val logprob: Double, val token: String)

    @Serializable
    class SummaryPart(val text: String) {
        val type = "summary_part"
    }
}

internal object ItemTextSerializer : KSerializer<Item.Text> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ai.koog.prompt.executor.clients.openai.models.Item.Text", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Item.Text) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): Item.Text {
        return Item.Text(decoder.decodeString())
    }
}

internal object ItemPolymorphicSerializer : JsonContentPolymorphicSerializer<Item>(Item::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Item> {
        return when (element) {
            is JsonPrimitive -> Item.Text.serializer()
            is JsonObject -> {
                when (val type = element["type"]?.jsonPrimitive?.content) {
                    "message" -> {
                        if (element.containsKey("id")) {
                            Item.OutputMessage.serializer()
                        } else {
                            Item.InputMessage.serializer()
                        }
                    }

                    "file_search_call" -> Item.FileSearchToolCall.serializer()
                    "computer_call" -> Item.ComputerToolCall.serializer()
                    "computer_call_output" -> Item.ComputerToolCallOutput.serializer()
                    "web_search_call" -> Item.WebSearchToolCall.serializer()
                    "function_call" -> Item.FunctionToolCall.serializer()
                    "function_call_output" -> Item.FunctionToolCallOutput.serializer()
                    "reasoning" -> Item.Reasoning.serializer()
                    "image_generation_call" -> Item.ImageGenerationCall.serializer()
                    "code_interpreter_call" -> Item.CodeInterpreterToolCall.serializer()
                    "local_shell_call" -> Item.LocalShellCall.serializer()
                    "local_shell_call_output" -> Item.LocalShellCallOutput.serializer()
                    "mcp_list_tools" -> Item.McpListTools.serializer()
                    "mcp_approval_request" -> Item.McpApprovalRequest.serializer()
                    "mcp_approval_response" -> Item.McpApprovalResponse.serializer()
                    "mcp_call" -> Item.McpToolCall.serializer()
                    "custom_tool_call_output" -> Item.CustomToolCallOutput.serializer()
                    "custom_tool_call" -> Item.CustomToolCall.serializer()
                    "item_reference" -> Item.ItemReference.serializer()
                    else -> throw SerializationException("Unknown Item type: $type")
                }
            }

            else -> throw SerializationException("Invalid Item format")
        }
    }
}

internal object OpenAIResponsesToolChoiceSerializer : KSerializer<OpenAIResponsesToolChoice> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor =
        buildSerialDescriptor("OpenAIResponsesToolChoice", SerialKind.CONTEXTUAL)

    override fun serialize(encoder: Encoder, value: OpenAIResponsesToolChoice) {
        when (value) {
            is OpenAIResponsesToolChoice.Mode -> encoder.encodeString(value.mode)
            is OpenAIResponsesToolChoice.AllowedTools -> encoder.encodeSerializableValue(
                OpenAIResponsesToolChoice.AllowedTools.serializer(),
                value
            )

            is OpenAIResponsesToolChoice.HostedTool -> encoder.encodeSerializableValue(
                OpenAIResponsesToolChoice.HostedTool.serializer(),
                value
            )

            is OpenAIResponsesToolChoice.FunctionTool -> encoder.encodeSerializableValue(
                OpenAIResponsesToolChoice.FunctionTool.serializer(),
                value
            )

            is OpenAIResponsesToolChoice.McpTool -> encoder.encodeSerializableValue(
                OpenAIResponsesToolChoice.McpTool.serializer(),
                value
            )

            is OpenAIResponsesToolChoice.CustomTool -> encoder.encodeSerializableValue(
                OpenAIResponsesToolChoice.CustomTool.serializer(),
                value
            )
        }
    }

    override fun deserialize(decoder: Decoder): OpenAIResponsesToolChoice {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("OpenAIResponsesToolChoice can only be deserialized from JSON")

        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> {
                OpenAIResponsesToolChoice.Mode(element.content)
            }

            is JsonObject -> {
                when (element["type"]?.jsonPrimitive?.content) {
                    "allowed_tools" -> jsonDecoder.json.decodeFromJsonElement(
                        OpenAIResponsesToolChoice.AllowedTools.serializer(),
                        element
                    )

                    "function" -> jsonDecoder.json.decodeFromJsonElement(
                        OpenAIResponsesToolChoice.FunctionTool.serializer(),
                        element
                    )

                    "mcp" -> jsonDecoder.json.decodeFromJsonElement(
                        OpenAIResponsesToolChoice.McpTool.serializer(),
                        element
                    )

                    "custom" -> jsonDecoder.json.decodeFromJsonElement(
                        OpenAIResponsesToolChoice.CustomTool.serializer(),
                        element
                    )

                    "file_search", "web_search_preview", "computer_use_preview", "code_interpreter", "image_generation" ->
                        jsonDecoder.json.decodeFromJsonElement(
                            OpenAIResponsesToolChoice.HostedTool.serializer(),
                            element
                        )

                    else -> throw SerializationException("Not recognize tool choice type: ${element["type"]?.jsonPrimitive?.content}")
                }
            }

            else -> throw SerializationException("Tool choice must be either a string or an object")
        }
    }
}

internal object OpenAIResponsesAPIRequestSerializer :
    AdditionalPropertiesFlatteningSerializer<OpenAIResponsesAPIRequest>(OpenAIResponsesAPIRequest.serializer())

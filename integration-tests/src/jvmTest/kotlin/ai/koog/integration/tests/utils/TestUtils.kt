package ai.koog.integration.tests.utils

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.filterTextOnly
import ai.koog.prompt.structure.RegisteredStandardJsonSchemaGenerators
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredOutput
import ai.koog.prompt.structure.StructuredOutputConfig
import ai.koog.prompt.structure.StructuredResponse
import ai.koog.prompt.structure.annotations.InternalStructuredOutputApi
import ai.koog.prompt.structure.json.JsonStructuredData
import ai.koog.prompt.structure.json.generator.StandardJsonSchemaGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

object TestUtils {
    fun readTestAnthropicKeyFromEnv(): String {
        return System.getenv("ANTHROPIC_API_TEST_KEY")
            ?: error("ERROR: environment variable `ANTHROPIC_API_TEST_KEY` is not set")
    }

    fun readTestOpenAIKeyFromEnv(): String {
        return System.getenv("OPEN_AI_API_TEST_KEY")
            ?: error("ERROR: environment variable `OPEN_AI_API_TEST_KEY` is not set")
    }

    fun readTestGoogleAIKeyFromEnv(): String {
        return System.getenv("GEMINI_API_TEST_KEY")
            ?: error("ERROR: environment variable `GEMINI_API_TEST_KEY` is not set")
    }

    fun readTestOpenRouterKeyFromEnv(): String {
        return System.getenv("OPEN_ROUTER_API_TEST_KEY")
            ?: error("ERROR: environment variable `OPEN_ROUTER_API_TEST_KEY` is not set")
    }

    fun readTestMistralAiKeyFromEnv(): String {
        return System.getenv("MISTRAL_AI_API_TEST_KEY")
            ?: error("ERROR: environment variable `MISTRAL_AI_API_TEST_KEY` is not set")
    }

    fun readAwsAccessKeyIdFromEnv(): String {
        return System.getenv("AWS_ACCESS_KEY_ID")
            ?: error("ERROR: environment variable `AWS_ACCESS_KEY_ID` is not set")
    }

    fun readAwsSecretAccessKeyFromEnv(): String {
        return System.getenv("AWS_SECRET_ACCESS_KEY")
            ?: error("ERROR: environment variable `AWS_SECRET_ACCESS_KEY` is not set")
    }

    fun readAwsSessionTokenFromEnv(): String? {
        return System.getenv("AWS_SESSION_TOKEN")
            ?: null.also {
                println("WARNING: environment variable `AWS_SESSION_TOKEN` is not set, using default session token")
            }
    }

    @Serializable
    @SerialName("WeatherReport")
    @LLMDescription("Weather report for a specific location")
    data class WeatherReport(
        @property:LLMDescription("Name of the city")
        val city: String,
        @property:LLMDescription("Temperature in Celsius")
        val temperature: Int,
        @property:LLMDescription("Brief weather description")
        val description: String,
        @property:LLMDescription("Humidity percentage")
        val humidity: Int
    )

    @Serializable
    enum class CalculatorOperation {
        ADD,
        SUBTRACT,
        MULTIPLY,
        DIVIDE
    }

    @Serializable
    enum class Colors {
        WHITE,
        BLACK,
        RED,
        ORANGE,
        YELLOW,
        GREEN,
        BLUE,
        INDIGO,
        VIOLET
    }

    @Serializable
    data class SimpleCalculatorArgs(
        @property:LLMDescription("The operation to perform.")
        val operation: CalculatorOperation,
        @property:LLMDescription("The first argument (number)")
        val a: Int,
        @property:LLMDescription("The second argument (number)")
        val b: Int
    )

    object SimpleCalculatorTool : SimpleTool<SimpleCalculatorArgs>() {
        override val argsSerializer = SimpleCalculatorArgs.serializer()

        override val name: String = "calculator"
        override val description: String =
            "A simple calculator that can add, subtract, multiply, and divide two numbers."

        override suspend fun doExecute(args: SimpleCalculatorArgs): String {
            return when (args.operation) {
                CalculatorOperation.ADD -> (args.a + args.b).toString()
                CalculatorOperation.SUBTRACT -> (args.a - args.b).toString()
                CalculatorOperation.MULTIPLY -> (args.a * args.b).toString()
                CalculatorOperation.DIVIDE -> {
                    if (args.b == 0) {
                        "Error: Division by zero"
                    } else {
                        (args.a / args.b).toString()
                    }
                }
            }
        }
    }

    @Serializable
    object CalculatorToolNoArgs : SimpleTool<Unit>() {
        override val argsSerializer = Unit.serializer()

        override val name: String = "calculator"
        override val description: String =
            "A simple calculator that performs basic calculations. No parameters needed."

        override suspend fun doExecute(args: Unit): String {
            return "The result of 123 + 456 is 579"
        }
    }

    object CalculatorTool : Tool<CalculatorTool.Args, Int>() {
        @Serializable
        data class Args(
            @property:LLMDescription("The operation to perform.")
            val operation: CalculatorOperation,
            @property:LLMDescription("The first argument (number)")
            val a: Int,
            @property:LLMDescription("The second argument (number)")
            val b: Int
        )

        override val argsSerializer = Args.serializer()
        override val resultSerializer: KSerializer<Int> = Int.serializer()

        override val name: String = "calculator"
        override val description: String =
            "A simple calculator that can add, subtract, multiply, and divide two numbers."

        override suspend fun execute(args: Args): Int = when (args.operation) {
            CalculatorOperation.ADD -> args.a + args.b
            CalculatorOperation.SUBTRACT -> args.a - args.b
            CalculatorOperation.MULTIPLY -> args.a * args.b
            CalculatorOperation.DIVIDE -> args.a / args.b
        }
    }

    sealed interface OperationResult<T> {
        class Success<T>(val result: T) : OperationResult<T>
        class Failure<T>(val error: String) : OperationResult<T>
    }

    class MockFileSystem {
        private val fileContents: MutableMap<String, String> = mutableMapOf()

        fun create(path: String, content: String): OperationResult<Unit> {
            if (path in fileContents) return OperationResult.Failure("File already exists")
            fileContents[path] = content
            return OperationResult.Success(Unit)
        }

        fun delete(path: String): OperationResult<Unit> {
            if (path !in fileContents) return OperationResult.Failure("File does not exist")
            fileContents.remove(path)
            return OperationResult.Success(Unit)
        }

        fun read(path: String): OperationResult<String> {
            if (path !in fileContents) return OperationResult.Failure("File does not exist")
            return OperationResult.Success(fileContents[path]!!)
        }

        fun ls(path: String): OperationResult<List<String>> {
            if (path in fileContents) {
                return OperationResult.Failure("Path $path points to a file, but not a directory!")
            }
            val matchingFiles = fileContents
                .filter { (filePath, _) -> filePath.startsWith(path) }
                .map { (filePath, _) -> filePath }

            if (matchingFiles.isEmpty()) {
                return OperationResult.Failure("No files in the directory. Directory doesn't exist or is empty.")
            }
            return OperationResult.Success(matchingFiles)
        }

        fun fileCount(): Int = fileContents.size
    }

    class CreateFile(private val fs: MockFileSystem) : Tool<CreateFile.Args, CreateFile.Result>() {
        @Serializable
        data class Args(
            @property:LLMDescription("The path to create the file")
            val path: String,
            @property:LLMDescription("The content to create the file")
            val content: String
        )

        @Serializable
        data class Result(val successful: Boolean, val message: String? = null)

        override val argsSerializer = Args.serializer()
        override val resultSerializer: KSerializer<Result> = Result.serializer()

        override val name: String = "create_file"
        override val description: String =
            "Create a file and writes the given text content to it"

        override suspend fun execute(args: Args): Result {
            return when (val res = fs.create(args.path, args.content)) {
                is OperationResult.Success -> Result(successful = true)
                is OperationResult.Failure -> Result(successful = false, message = res.error)
            }
        }
    }

    class DeleteFile(private val fs: MockFileSystem) : Tool<DeleteFile.Args, DeleteFile.Result>() {
        @Serializable
        data class Args(
            @property:LLMDescription("The path of the file to be deleted")
            val path: String
        )

        @Serializable
        data class Result(val successful: Boolean, val message: String? = null)

        override val argsSerializer = Args.serializer()
        override val resultSerializer: KSerializer<Result> = Result.serializer()

        override val name: String = "delete_file"
        override val description: String = "Deletes a file"

        override suspend fun execute(args: Args): Result {
            return when (val res = fs.delete(args.path)) {
                is OperationResult.Success -> Result(successful = true)
                is OperationResult.Failure -> Result(successful = false, message = res.error)
            }
        }
    }

    class ReadFile(private val fs: MockFileSystem) : Tool<ReadFile.Args, ReadFile.Result>() {
        @Serializable
        data class Args(
            @property:LLMDescription("The path of the file to read")
            val path: String
        )

        @Serializable
        data class Result(
            val successful: Boolean,
            val message: String? = null,
            val content: String? = null
        )

        override val argsSerializer = Args.serializer()
        override val resultSerializer: KSerializer<Result> = Result.serializer()

        override val name: String = "read_file"
        override val description: String = "Reads a file"

        override suspend fun execute(args: Args): Result {
            return when (val res = fs.read(args.path)) {
                is OperationResult.Success<String> -> Result(successful = true, content = res.result)
                is OperationResult.Failure -> Result(successful = false, message = res.error)
            }
        }
    }

    class ListFiles(private val fs: MockFileSystem) : Tool<ListFiles.Args, ListFiles.Result>() {
        @Serializable
        data class Args(
            @property:LLMDescription("The path of the directory")
            val path: String
        )

        @Serializable
        data class Result(
            val successful: Boolean,
            val message: String? = null,
            val children: List<String>? = null
        )

        override val argsSerializer = Args.serializer()
        override val resultSerializer: KSerializer<Result> = Result.serializer()

        override val name: String = "list_files"
        override val description: String = "List all files inside the given path of the directory"

        override suspend fun execute(args: Args): Result {
            return when (val res = fs.ls(args.path)) {
                is OperationResult.Success<List<String>> -> Result(successful = true, children = res.result)
                is OperationResult.Failure -> Result(successful = false, message = res.error)
            }
        }
    }

    @Serializable
    data class GetTransactionsArgs(
        @property:LLMDescription("Start date in format YYYY-MM-DD")
        val startDate: String,
        @property:LLMDescription("End date in format YYYY-MM-DD")
        val endDate: String
    )

    object GetTransactionsTool : SimpleTool<GetTransactionsArgs>() {
        override val argsSerializer = GetTransactionsArgs.serializer()

        override val name: String = "get_transactions"
        override val description: String = "Get all transactions between two dates"

        override suspend fun doExecute(args: GetTransactionsArgs): String {
            // Simulate returning transactions
            return """
            [
              {date: "${args.startDate}", amount: -100.00, description: "Grocery Store"},
              {date: "${args.startDate}", amount: +1000.00, description: "Salary Deposit"},
              {date: "${args.endDate}", amount: -500.00, description: "Rent Payment"},
              {date: "${args.endDate}", amount: -200.00, description: "Utilities"}
            ]
            """.trimIndent()
        }
    }

    @Serializable
    data class CalculateSumArgs(
        @property:LLMDescription("List of amounts to sum")
        val amounts: List<Double>
    )

    object CalculateSumTool : SimpleTool<CalculateSumArgs>() {
        override val argsSerializer = CalculateSumArgs.serializer()

        override val name: String = "calculate_sum"
        override val description: String = "Calculate the sum of a list of amounts"

        override suspend fun doExecute(args: CalculateSumArgs): String {
            val sum = args.amounts.sum()
            return sum.toString()
        }
    }

    /**
     * Address type enum.
     */
    @Serializable
    enum class AddressType {
        HOME,
        WORK,
        OTHER
    }

    /**
     * An address with multiple fields.
     */
    @Serializable
    data class Address(
        @property:LLMDescription("The type of address (HOME, WORK, or OTHER)")
        val type: AddressType,
        @property:LLMDescription("The street address")
        val street: String,
        @property:LLMDescription("The city")
        val city: String,
        @property:LLMDescription("The state or province")
        val state: String,
        @property:LLMDescription("The ZIP or postal code")
        val zipCode: String
    )

    /**
     * A user profile with nested structures.
     */
    @Serializable
    data class UserProfile(
        @property:LLMDescription("The user's full name")
        val name: String,
        @property:LLMDescription("The user's email address")
        val email: String,
        @property:LLMDescription("The user's addresses")
        val addresses: List<Address>
    )

    /**
     * Arguments for the complex nested tool.
     */
    @Serializable
    data class ComplexNestedToolArgs(
        @property:LLMDescription("The user profile to process")
        val profile: UserProfile
    )

    /**
     * A complex nested tool that demonstrates the JSON schema validation error.
     * This tool has parameters with complex nested structures that would trigger
     * the error in the Anthropic API before the fix.
     */
    object ComplexNestedTool : SimpleTool<ComplexNestedToolArgs>() {
        override val argsSerializer = ComplexNestedToolArgs.serializer()

        override val name = "complex_nested_tool"

        override val description = "A tool that processes user profiles with complex nested structures."

        override suspend fun doExecute(args: ComplexNestedToolArgs): String {
            // Process the user profile
            val profile = args.profile
            val addressesInfo = profile.addresses.joinToString("\n") { address ->
                "- ${address.type} Address: ${address.street}, ${address.city}, ${address.state} ${address.zipCode}"
            }

            return """
                Successfully processed user profile:
                Name: ${profile.name}
                Email: ${profile.email}
                Addresses:
                $addressesInfo
            """.trimIndent()
        }
    }

    const val DELAY_MILLIS = 500L

    @Serializable
    data class DelayArgs(
        @property:LLMDescription("The number of milliseconds to delay")
        val milliseconds: Int = DELAY_MILLIS.toInt()
    )

    object DelayTool : SimpleTool<DelayArgs>() {
        override val argsSerializer = DelayArgs.serializer()

        override val name = "delay"
        override val description = "A tool that introduces a delay to simulate a time-consuming operation."

        override suspend fun doExecute(args: DelayArgs): String {
            kotlinx.coroutines.delay(args.milliseconds.toLong())
            return "Delayed for ${args.milliseconds} milliseconds"
        }
    }

    @Serializable
    data class Country(
        val name: String,
        val capital: String,
        val population: String,
        val language: String
    )

    fun markdownCountryDefinition(): String {
        return """
            # Country Name
            * Capital: [capital city]
            * Population: [approximate population]
            * Language: [official language]
        """.trimIndent()
    }

    fun markdownStreamingParser(block: MarkdownParserBuilder.() -> Unit): MarkdownParser {
        val builder = MarkdownParserBuilder().apply(block)
        return builder.build()
    }

    class MarkdownParserBuilder {
        private var headerHandler: ((String) -> Unit)? = null
        private var bulletHandler: ((String) -> Unit)? = null
        private var finishHandler: (() -> Unit)? = null

        fun onHeader(handler: (String) -> Unit) {
            headerHandler = handler
        }

        fun onBullet(handler: (String) -> Unit) {
            bulletHandler = handler
        }

        fun onFinishStream(handler: () -> Unit) {
            finishHandler = handler
        }

        fun build(): MarkdownParser {
            return MarkdownParser(headerHandler, bulletHandler, finishHandler)
        }
    }

    class MarkdownParser(
        private val headerHandler: ((String) -> Unit)?,
        private val bulletHandler: ((String) -> Unit)?,
        private val finishHandler: (() -> Unit)?
    ) {
        suspend fun parseStream(stream: Flow<String>) {
            val buffer = kotlin.text.StringBuilder()

            stream.collect { chunk ->
                buffer.append(chunk)
                processBuffer(buffer)
            }

            processBuffer(buffer, isEnd = true)

            finishHandler?.invoke()
        }

        private fun processBuffer(buffer: StringBuilder, isEnd: Boolean = false) {
            val text = buffer.toString()
            val lines = text.split("\n")

            val completeLines = lines.dropLast(1)

            for (line in completeLines) {
                val trimmedLine = line.trim()

                if (trimmedLine.startsWith("# ")) {
                    val headerText = trimmedLine.substring(2).trim()
                    headerHandler?.invoke(headerText)
                } else if (trimmedLine.startsWith("* ")) {
                    val bulletText = trimmedLine.substring(2).trim()
                    bulletHandler?.invoke(bulletText)
                }
            }

            if (completeLines.isNotEmpty()) {
                buffer.clear()
                buffer.append(lines.last())
            }

            if (isEnd) {
                val lastLine = buffer.toString().trim()
                if (lastLine.isNotEmpty()) {
                    if (lastLine.startsWith("# ")) {
                        val headerText = lastLine.substring(2).trim()
                        headerHandler?.invoke(headerText)
                    } else if (lastLine.startsWith("* ")) {
                        val bulletText = lastLine.substring(2).trim()
                        bulletHandler?.invoke(bulletText)
                    }
                }
                buffer.clear()
            }
        }
    }

    fun parseMarkdownStreamToCountries(markdownStream: Flow<StreamFrame>): Flow<Country> {
        return flow {
            val countries = mutableListOf<Country>()
            var currentCountryName = ""
            val bulletPoints = mutableListOf<String>()

            val parser = markdownStreamingParser {
                onHeader { headerText ->
                    if (currentCountryName.isNotEmpty() && bulletPoints.size >= 3) {
                        val capital = bulletPoints.getOrNull(0)?.substringAfter("Capital: ")?.trim() ?: ""
                        val population = bulletPoints.getOrNull(1)?.substringAfter("Population: ")?.trim() ?: ""
                        val language = bulletPoints.getOrNull(2)?.substringAfter("Language: ")?.trim() ?: ""
                        val country = Country(currentCountryName, capital, population, language)
                        countries.add(country)
                    }

                    currentCountryName = headerText
                    bulletPoints.clear()
                }

                onBullet { bulletText ->
                    bulletPoints.add(bulletText)
                }

                onFinishStream {
                    if (currentCountryName.isNotEmpty() && bulletPoints.size >= 3) {
                        val capital = bulletPoints.getOrNull(0)?.substringAfter("Capital: ")?.trim() ?: ""
                        val population = bulletPoints.getOrNull(1)?.substringAfter("Population: ")?.trim() ?: ""
                        val language = bulletPoints.getOrNull(2)?.substringAfter("Language: ")?.trim() ?: ""
                        val country = Country(currentCountryName, capital, population, language)
                        countries.add(country)
                    }
                }
            }

            parser.parseStream(markdownStream.filterTextOnly())

            countries.forEach { emit(it) }
        }
    }

    object StructuredTest {
        @OptIn(InternalStructuredOutputApi::class)
        fun getStructure(model: LLModel) = JsonStructuredData.createJsonStructure<WeatherReport>(
            json = Json,
            schemaGenerator = RegisteredStandardJsonSchemaGenerators[model.provider] ?: StandardJsonSchemaGenerator,
            descriptionOverrides = mapOf(
                "WeatherReport.city" to "Name of the city or location",
                "WeatherReport.temperature" to "Current temperature in Celsius degrees"
            ),
            examples = listOf(
                WeatherReport("Moscow", 20, "Sunny", 50)
            )
        )

        fun getConfigNoFixingParserNative(model: LLModel) =
            StructuredOutputConfig(default = StructuredOutput.Native(getStructure(model)))

        fun getConfigFixingParserNative(model: LLModel) = StructuredOutputConfig(
            default = StructuredOutput.Native(getStructure(model)),
            fixingParser = StructureFixingParser(
                fixingModel = model,
                retries = 3
            )
        )

        fun getConfigNoFixingParserManual(model: LLModel) =
            StructuredOutputConfig(default = StructuredOutput.Manual(getStructure(model)))

        fun getConfigFixingParserManual(model: LLModel) = StructuredOutputConfig(
            default = StructuredOutput.Manual(getStructure(model)),
            fixingParser = StructureFixingParser(
                fixingModel = model,
                retries = 3
            )
        )

        val prompt = Prompt.build("test-structured-json") {
            system(
                """
                You are a weather forecasting assistant.
                When asked for a weather forecast, provide a realistic but fictional forecast.
                """.trimIndent()
            )
            user(
                "What is the weather forecast for London? Please provide temperature, description, and humidity if available."
            )
        }

        fun checkResponse(result: Result<StructuredResponse<WeatherReport>>) {
            val response = result.getOrThrow().structure
            assertNotNull(response)

            assertEquals("London", response.city, "City should be London, got: ${response.city}")
            assertTrue(
                response.temperature in -50..60,
                "Temperature should be realistic, got: ${response.temperature}"
            )
            assertTrue(response.description.isNotBlank(), "Description should not be empty")
            assertTrue(
                response.humidity >= 0,
                "Humidity should be a valid percentage, got: ${response.humidity}"
            )
        }
    }

    fun singlePropertyObjectSchema(provider: LLMProvider, propName: String, type: String) = buildJsonObject {
        put("type", JsonPrimitive("object"))
        put(
            "properties",
            buildJsonObject {
                put(propName, buildJsonObject { put("type", JsonPrimitive(type)) })
            }
        )
        put("required", buildJsonArray { add(JsonPrimitive(propName)) })
        if (provider !is LLMProvider.Google) {
            // Google response_schema does not support additionalProperties at the root
            put("additionalProperties", JsonPrimitive(false))
        }
    }

    fun assertExceptionMessageContains(ex: Throwable, vararg substrings: String) {
        val msg = ex.message ?: ""
        val matches = substrings.any { needle -> msg.contains(needle, ignoreCase = true) }
        assertTrue(matches, "Exception message doesn't contain expected error: ${ex.message}")
    }

    fun isValidJson(str: String): Boolean = try {
        Json.parseToJsonElement(str)
        true
    } catch (_: Exception) {
        false
    }
}

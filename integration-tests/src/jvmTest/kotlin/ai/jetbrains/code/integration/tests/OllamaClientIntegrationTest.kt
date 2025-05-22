package ai.jetbrains.code.integration.tests

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.dsl.Prompt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@EnabledOnOs(OS.LINUX)
@ExtendWith(OllamaTestFixtureExtension::class)
class OllamaClientIntegrationTest {
    companion object {
        @field:InjectOllamaTestFixture
        private lateinit var fixture: OllamaTestFixture
        private val executor get() = fixture.executor
        private val model get() = fixture.model
    }

    @Test
    fun `integration_test execute simple prompt`() = runTest(timeout = 600.seconds) {
        val prompt = Prompt.Companion.build("test") {
            system("You are a helpful assistant.")
            user("What is the capital of France?")
        }

        val response = executor.execute(prompt, model)

        assertTrue(response.isNotEmpty(), "Response should not be empty")
        assertTrue(response.contains("Paris"), "Response should contain 'Paris'")
    }

    @Test
    fun `integration_test execute tools with required parameters`() = runTest(timeout = 600.seconds) {
        val searchTool = ToolDescriptor(
            name = "search",
            description = "Search for information",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "query",
                    description = "The search query",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "limit",
                    description = "Maximum number of results",
                    type = ToolParameterType.Integer
                )
            )
        )

        val prompt = Prompt.Companion.build("test-tools") {
            system("You are a helpful assistant that uses tools.")
            user("Search for information about Paris with a limit of 5 results")
        }

        val response = executor.execute(prompt, model, listOf(searchTool))
        println(response)
        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @Test
    fun `integration_test execute tools with required and optional parameters`() = runTest(timeout = 600.seconds) {
        val searchTool = ToolDescriptor(
            name = "search",
            description = "Search for information",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "query",
                    description = "The search query",
                    type = ToolParameterType.String
                )
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor(
                    name = "limit",
                    description = "Maximum number of results",
                    type = ToolParameterType.Integer
                )
            )
        )

        val prompt = Prompt.Companion.build("test") {
            system("You are a helpful assistant that uses tools.")
            user("Search for information about Paris with a limit of 5 results")
        }

        val response = executor.execute(prompt, model, listOf(searchTool))
        println(response)
        assertTrue(response.isNotEmpty(), "Response should not be empty")
    }

    @Test
    fun `integration_test execute tools with optional parameters`() = runTest(timeout = 600.seconds) {
        val searchTool = ToolDescriptor(
            name = "search",
            description = "Search for information",
            requiredParameters = listOf(),
            optionalParameters = listOf(
                ToolParameterDescriptor(
                    name = "query",
                    description = "The search query",
                    type = ToolParameterType.String
                ),
                ToolParameterDescriptor(
                    name = "limit",
                    description = "Maximum number of results",
                    type = ToolParameterType.Integer
                )
            )
        )

        val prompt = Prompt.Companion.build("test") {
            system("You are a helpful assistant that uses tools.")
            user("Search for information about Paris with a limit of 5 results")
        }

        val response = executor.execute(prompt, model, listOf(searchTool))
        println(response)
        assertTrue(response.isNotEmpty(), "response should not be empty")
    }

    @Test
    fun `integration_test execute tools with no parameters`() = runTest(timeout = 600.seconds) {
        val getTimeTool = ToolDescriptor(
            name = "getTime",
            description = "Get the current time"
        )

        val prompt = Prompt.Companion.build("test") {
            system("You are a helpful assistant that uses tools.")
            user("What time is it?")
        }

        val response = executor.execute(prompt, model, listOf(getTimeTool))
        println(response)
        assertTrue(response.isNotEmpty(), "response should not be empty")
    }

    @Test
    fun `integration_test execute tools with int parameter`() = runTest(timeout = 600.seconds) {
        val setLimitTool = ToolDescriptor(
            name = "setLimit",
            description = "Set the limit",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "limit",
                    description = "The limit value",
                    type = ToolParameterType.Integer
                )
            )
        )

        val prompt = Prompt.Companion.build("test") {
            system("You are a helpful assistant that uses tools.")
            user("Set the limit to 42")
        }

        val response = executor.execute(prompt, model, listOf(setLimitTool))
        println(response)
        assertTrue(response.isNotEmpty(), "response should not be empty")
    }

    @Test
    fun `integration_test execute tools with float parameter`() = runTest(timeout = 600.seconds) {
        val printValueTool = ToolDescriptor(
            name = "printValue",
            description = "Print the value",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "value",
                    description = "The value",
                    type = ToolParameterType.Float
                )
            )
        )

        val prompt = Prompt.Companion.build("test") {
            system("You are a helpful assistant.")
            user("What's the value of 2/3")
        }

        val response = executor.execute(prompt, model, listOf(printValueTool))
        println(response)
        assertTrue(response.isNotEmpty(), "response should not be empty")
    }

    @Test
    fun `integration_test execute tools with string parameter`() = runTest(timeout = 600.seconds) {
        val setNameTool = ToolDescriptor(
            name = "setName",
            description = "Set the name",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "name",
                    description = "The name value",
                    type = ToolParameterType.String
                )
            )
        )

        val prompt = Prompt.Companion.build("test") {
            system("You are a helpful assistant that uses tools.")
            user("Set the name to John")
        }

        val response = executor.execute(prompt, model, listOf(setNameTool))
        println(response)
        assertTrue(response.isNotEmpty(), "response should not be empty")
    }

    @Test
    fun `integration_test execute tools with enum parameter`() = runTest(timeout = 600.seconds) {
        val setColor = ToolDescriptor(
            name = "setColor",
            description = "Set the color",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "color",
                    description = "The color value",
                    type = ToolParameterType.Enum(arrayOf("red", "green", "blue"))
                )
            )
        )

        val prompt = Prompt.Companion.build("test") {
            system("You are a helpful assistant that uses tools.")
            user("Set the color to blue")
        }

        val response = executor.execute(prompt, model, listOf(setColor))
        println(response)
        assertTrue(response.isNotEmpty(), "response should not be empty")
    }

    @Serializable
    enum class CalculatorOperation {
        ADD, SUBTRACT, MULTIPLY, DIVIDE
    }

    @Test
    fun `integration_test execute tools with serializable enum parameter`() = runTest(timeout = 600.seconds) {
        val calculatorTool = ToolDescriptor(
            name = "calculator",
            description = "A simple calculator that can add, subtract, multiply, and divide two numbers.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "operation",
                    description = "The operation to perform.",
                    type = ToolParameterType.Enum(CalculatorOperation.entries.map { it.name }.toTypedArray())
                ),
                ToolParameterDescriptor(
                    name = "a",
                    description = "The first argument (number)",
                    type = ToolParameterType.Integer
                ),
                ToolParameterDescriptor(
                    name = "b",
                    description = "The second argument (number)",
                    type = ToolParameterType.Integer
                )
            )
        )

        val prompt = Prompt.Companion.build("test") {
            system("You are a helpful assistant with access to a calculator tool.")
            user("What is 123 + 456?")
        }

        val response = executor.execute(prompt, model, listOf(calculatorTool))
        println(response)
        assertTrue(response.isNotEmpty(), "response should not be empty")
    }

    @Test
    fun `integration_test execute tools with list of strings parameter`() = runTest(timeout = 600.seconds) {
        val setTags = ToolDescriptor(
            name = "setTags",
            description = "Set the tags",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "tags",
                    description = "The tags",
                    type = ToolParameterType.List(ToolParameterType.String)
                )
            )
        )

        val prompt = Prompt.Companion.build("test") {
            system("You are a helpful assistant that uses tools.")
            user("Set the tags to important, urgent, and critical")
        }

        val response = executor.execute(prompt, model, listOf(setTags))
        println(response)
        assertTrue(response.isNotEmpty(), "response should not be empty")
    }

    @Test
    fun `integration_test execute tools with list of integers parameter`() = runTest(timeout = 600.seconds) {
        val setValues = ToolDescriptor(
            name = "setValues",
            description = "Set the values",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "values",
                    description = "The values",
                    type = ToolParameterType.List(ToolParameterType.Integer)
                )
            )
        )

        val prompt = Prompt.Companion.build("test") {
            system("You are a helpful assistant that uses tools.")
            user("Set the values to 1, 2, and 3")
        }

        val response = executor.execute(prompt, model, listOf(setValues))
        println(response)
        assertTrue(response.isNotEmpty(), "response should not be empty")
    }

    @Test
    fun `integration_test execute tools with list of floats parameter`() = runTest(timeout = 600.seconds) {
        val setValues = ToolDescriptor(
            name = "setValues",
            description = "Set the values",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "values",
                    description = "The values",
                    type = ToolParameterType.List(ToolParameterType.Float)
                )
            )
        )

        val prompt = Prompt.Companion.build("test") {
            system("You are a helpful assistant that uses tools.")
            user("Set the min, the max and the avg values in range from 0 to 1 with a step of 0.01.")
        }

        val response = executor.execute(prompt, model, listOf(setValues))
        println(response)
        assertTrue(response.isNotEmpty(), "response should not be empty")
    }

    @Serializable
    enum class Tag {
        IMPORTANT,
        URGENT,
        CRITICAL,
        NORMAL,
        LOW;
    }

    @Test
    fun `integration_test execute tools with list of enums parameter`() = runTest(timeout = 600.seconds) {
        val setTags = ToolDescriptor(
            name = "setTags",
            description = "Set the tags",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "tags",
                    description = "The tags",
                    type = ToolParameterType.List(ToolParameterType.Enum(Tag.entries.map { it.name }.toTypedArray()))
                )
            )
        )

        val prompt = Prompt.Companion.build("test") {
            system("You are a helpful assistant that uses tools.")
            user("Select two tags of the highest priority.")
        }

        val response = executor.execute(prompt, model, listOf(setTags))
        println(response)
        assertTrue(response.isNotEmpty(), "response should not be empty")
    }

    @Test
    fun `integration_test execute tools with list of lists parameter`() = runTest(timeout = 600.seconds) {
        val setTags = ToolDescriptor(
            name = "setTags",
            description = "Set the tags",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "tags",
                    description = "The tags",
                    type = ToolParameterType.List(ToolParameterType.List(ToolParameterType.String))
                )
            )
        )

        val prompt = Prompt.Companion.build("test") {
            system("You are a helpful assistant that uses tools.")
            user("Select two tags of the highest priority.")
            user("Then select two tags of the lowest priority.")
        }

        val response = executor.execute(prompt, model, listOf(setTags))
        println(response)
        assertTrue(response.isNotEmpty(), "response should not be empty")
    }

    @Test
    fun integration_testStreamingApiWithLargeText() = runTest(timeout = 600.seconds) {
        val prompt = Prompt.Companion.build("test") {
            system("You are a helpful assistant.")
            user("Write a detailed essay about the history of artificial intelligence, including its origins, major milestones, key figures, and current state. Please make it at least 1000 words.")
        }

        val flow = executor.executeStreaming(prompt, model)

        var totalText = ""
        flow.collect { chunk ->
            println(chunk)
            totalText += chunk
        }

        assertTrue(totalText.isNotEmpty(), "Total text should not be empty")
        assertTrue(totalText.length > 100, "Total text should be substantial")
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

        fun onHeader(level: Int, handler: (String) -> Unit) {
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

    fun parseMarkdownStreamToCountries(markdownStream: Flow<String>): Flow<Country> {
        return flow {
            val countries = mutableListOf<Country>()
            var currentCountryName = ""
            val bulletPoints = mutableListOf<String>()

            val parser = markdownStreamingParser {
                onHeader(1) { headerText ->
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

            parser.parseStream(markdownStream)

            countries.forEach { emit(it) }
        }
    }

    @Test
    fun `integration_test execute streaming API with structured data`() = runTest(timeout = 600.seconds) {
        val countries = mutableListOf<Country>()
        val countryDefinition = markdownCountryDefinition()

        val prompt = Prompt.Companion.build("test-structured-streaming") {
            system("You are a helpful assistant.")
            user(
                """
                Please provide information about 30 European countries in this format:

                $countryDefinition

                Make sure to follow this exact format with the # for country names and * for details.
            """.trimIndent()
            )
        }

        val markdownStream = executor.executeStreaming(prompt, model)

        parseMarkdownStreamToCountries(markdownStream).collect { country ->
            countries.add(country)
        }

        assertTrue(countries.isNotEmpty(), "Countries list should not be empty")

        countries.forEach { country ->
            println("Country: ${country.name}")
            println("  Capital: ${country.capital}")
            println("  Population: ${country.population}")
            println("  Language: ${country.language}")
            println()
        }
    }
}
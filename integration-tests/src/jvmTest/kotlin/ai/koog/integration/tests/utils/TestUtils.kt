package ai.koog.integration.tests.utils

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable

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
    data class CalculatorArgs(
        val operation: CalculatorOperation,
        val a: Int,
        val b: Int
    ) : ToolArgs

    object CalculatorTool : SimpleTool<CalculatorArgs>() {
        override val argsSerializer = CalculatorArgs.serializer()

        val calculatorToolDescriptor = ToolDescriptor(
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

        override val descriptor = calculatorToolDescriptor

        override suspend fun doExecute(args: CalculatorArgs): String {
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

    val delayMillis = 500L

    @Serializable
    data class DelayArgs(val milliseconds: Int = delayMillis.toInt()) : ToolArgs

    object DelayTool : SimpleTool<DelayArgs>() {
        override val argsSerializer = DelayArgs.serializer()

        val delayToolDescriptor = ToolDescriptor(
            name = "delay",
            description = "A tool that introduces a delay to simulate a time-consuming operation.",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "milliseconds",
                    description = "The number of milliseconds to delay",
                    type = ToolParameterType.Integer
                )
            )
        )

        override val descriptor = delayToolDescriptor

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
}

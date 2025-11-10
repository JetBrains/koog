package ai.koog.integration.tests.utils.structuredOutput

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.prompt.streaming.filterTextOnly
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable

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

val countryStructuredOutputPrompt = Prompt.build("test-structured-streaming") {
    system("You are a helpful assistant.")
    user(
        """
                Please provide information about 3 European countries in this format:

                ${markdownCountryDefinition()}

                Make sure to follow this exact format with the # for country names and * for details.
        """.trimIndent()
    )
}

package ai.koog.prompt.structure.markdown

import ai.koog.prompt.structure.markdown.MarkdownParserBuilder.MarkdownStreamingParser
import kotlinx.coroutines.flow.Flow

/**
 * A builder for creating markdown parsers with event-based handlers.
 */
public class MarkdownParserBuilder {
    private var headerHandlers = mutableMapOf<Int, suspend (String) -> Unit>()
    private var bulletHandler: (suspend (String) -> Unit)? = null
    private var finishedHandler: (suspend (String) -> Unit)? = null
    private var codeBlockHandler: (suspend (String) -> Unit)? = null
    private var lineMatchingHandlers = mutableMapOf<Regex?, suspend (String) -> Unit>()

    /**
     * Registers a handler for headers of the specified level.
     * @param level The header level (1-6)
     * @param handler The handler function that receives the header text
     */
    public fun onHeader(level: Int, handler: suspend (String) -> Unit) {
        require(level in 1..6) { "Header level must be between 1 and 6" }
        headerHandlers[level] = handler
    }

    /**
     * Registers a handler for bullet points.
     * @param handler The handler function that receives the bullet point text
     */
    public fun onBullet(handler: suspend (String) -> Unit) {
        bulletHandler = handler
    }

    /**
     * Registers a handler that is triggered when the stream processing is finished.
     *
     * @param handler The function to handle the final output, receiving the remaining text as a parameter.
     */
    public fun onFinishStream(handler: suspend (String) -> Unit) {
        finishedHandler = handler
    }

    /**
     * Registers a handler for code blocks.
     *
     * @param handler The handler function that receives the code block content and optional language identifier
     */
    public fun onCodeBlock(handler: suspend (String) -> Unit) {
        codeBlockHandler = handler
    }

    /**
     * Registers a handler that is triggered when a line matches the specified regex pattern.
     * If no regex is provided, the handler will be called for every line.
     *
     * @param regex The regex pattern to match against each line, or null to match any line
     * @param handler The function to handle the matched line
     */
    public fun onLineMatching(regex: Regex?, handler: suspend (String) -> Unit) {
        lineMatchingHandlers[regex] = handler
    }

    /**
     * Creates a parser function that processes markdown text and returns a list of result objects.
     * @return A function that takes markdown text and returns a list of result objects
     */
    public fun build(): suspend (String) -> Unit {
        return { markdown ->
            // Split the markdown by lines
            val lines = markdown.split("\n")

            var inCodeBlock = false
            var codeBlockContent = StringBuilder()

            for (line in lines) {
                val trimmedLine = line.trim()

                // Process the line based on its type and current state
                when {
                    // Handle code block markers
                    isBeginningOfCodeBlock(trimmedLine) -> {
                        inCodeBlock = handleCodeBlockMarker(
                            inCodeBlock,
                            codeBlockContent
                        )
                    }

                    // Handle content inside code blocks
                    inCodeBlock -> {
                        codeBlockContent.append(line).append("\n")
                    }

                    // Handle headers
                    trimmedLine.startsWith("#") -> {
                        processHeader(trimmedLine)
                    }

                    // Handle bullet points
                    trimmedLine.startsWith("-") -> {
                        processBulletPoint(trimmedLine)
                    }
                }

                // Always process line matching for non-code-block lines
                if (!inCodeBlock && !isBeginningOfCodeBlock(trimmedLine) && trimmedLine.isNotEmpty()) {
                    processLineMatching(trimmedLine)
                }
            }

            // Handle unclosed code block at the end of the document
            if (inCodeBlock && codeBlockContent.isNotEmpty()) {
                codeBlockHandler?.invoke(codeBlockContent.toString())
            }
        }
    }

    private fun isBeginningOfCodeBlock(line: String): Boolean = line.startsWith("```")

    /**
     * Handles a code block marker line (starting with ```)
     *
     * @param currentlyInCodeBlock Whether we're currently inside a code block
     * @param line The line containing the code block marker
     * @param content The StringBuilder collecting code block content
     * @param language The current code block language
     * @return Whether we're in a code block after processing this line
     */
    private suspend fun handleCodeBlockMarker(
        currentlyInCodeBlock: Boolean,
        content: StringBuilder,
    ): Boolean {
        if (!currentlyInCodeBlock) {
            // Start of code block
            // Extract language identifier if present
            content.clear()
            return true
        } else {
            // End of code block
            // Invoke the handler with the collected content
            codeBlockHandler?.invoke(content.toString())
            return false
        }
    }

    /**
     * Processes a header line (starting with #)
     *
     * @param line The header line to process
     */
    private suspend fun processHeader(line: String) {
        // Count the number of # to determine the header level
        val level = line.takeWhile { it == '#' }.length
        if (level in headerHandlers.keys) {
            // Extract the header text and call the handler
            val headerText = line.substring(level).trim()
            headerHandlers[level]?.invoke(headerText)
        }
    }

    /**
     * Processes a bullet point line (starting with -)
     *
     * @param line The bullet point line to process
     */
    private suspend fun processBulletPoint(line: String) {
        bulletHandler?.let { handler ->
            // Extract the bullet point text and call the handler
            val bulletText = line.substring(1).trim()
            handler(bulletText)
        }
    }

    /**
     * Processes line matching for a given line
     *
     * @param line The line to match against registered patterns
     */
    private suspend fun processLineMatching(line: String) {
        lineMatchingHandlers.forEach { (regex, handler) ->
            // If regex is null or the line matches the regex, invoke the handler
            if (regex == null || regex.matches(line)) {
                handler(line)
            }
        }
    }

    /**
     * Builds and returns a streaming markdown parser.
     *
     * This method constructs a `MarkdownStreamingParser` instance using the configuration
     * setup in the current `MarkdownParserBuilder`. The streaming parser processes markdown
     * text by consuming chunks of input and invoking the appropriate registered handlers for
     * headers, bullet points, code blocks, and other elements, as defined in the builder.
     *
     * @return A `MarkdownStreamingParser` instance capable of handling streaming markdown input.
     */
    public fun buildStreaming(): MarkdownStreamingParser = MarkdownStreamingParser(build())

    /**
     * A class for streaming markdown parsing to process and handle markdown content incrementally.
     * This class provides functionality to parse markdown content received as a flow of strings and
     * invoke the given parser function for each processed segment.
     *
     * @property parser A suspendable function that processes a markdown segment.
     */
    public inner class MarkdownStreamingParser(private val parser: suspend (String) -> Unit) {
        /**
         * Processes a stream of Markdown content provided as a flow of strings.
         *
         * Concatenates chunks of the incoming flow and parses complete sections of Markdown
         * content based on the presence of header or line separators. Each section is then
         * passed to the parser function for processing. Any remaining content in the buffer
         * after all chunks are collected is also processed. Once processing is complete,
         * a handler is invoked to signify the end of parsing.
         *
         * @param markdownStream A flow of strings representing chunks of Markdown content
         * to be parsed. These chunks are concatenated and processed into complete sections
         * for handling.
         */
        public suspend fun parseStream(markdownStream: Flow<String>) {
            var buffer = ""

            markdownStream.collect { chunk ->
                buffer += chunk

                // Check if we have a complete entry in the buffer
                if (buffer.contains("\n# ") || chunk.contains("\n")) {
                    val sections = buffer.split(Regex("(?=\\n# )"))
                    for (i in 0 until sections.size - 1) {
                        var section = sections[i]

                        if (section.isEmpty()) continue

                        if (!section.startsWith("#")) {
                            // Add the header prefix to ensure proper parsing
                            section = "# $section"
                        }

                        // Parse the complete section
                        parser(section)
                    }

                    // Keep only the potentially incomplete last section in the buffer
                    buffer = sections.last()
                }
            }

            // Process any remaining content in the buffer
            if (buffer.isNotEmpty()) {
                parser(buffer)
            }

            finishedHandler?.invoke(buffer)
        }
    }
}

/**
 * Creates a markdown parser with the given configuration.
 * @param config The configuration function for the parser builder
 * @return A function that takes markdown text and returns a list of result objects
 */
public fun markdownParser(config: MarkdownParserBuilder.() -> Unit): suspend (String) -> Unit {
    return MarkdownParserBuilder().apply(config).build()
}

/**
 * Creates a streaming markdown parser with the given configuration.
 * @param collector The configuration function for the parser builder
 * @return A function that takes a flow of markdown chunks and returns a flow of result objects
 */
public fun markdownStreamingParser(collector: MarkdownParserBuilder.() -> Unit): MarkdownStreamingParser {
    return MarkdownParserBuilder().apply(collector).buildStreaming()
}

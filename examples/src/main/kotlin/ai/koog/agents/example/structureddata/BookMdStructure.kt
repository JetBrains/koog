package ai.koog.agents.example.structureddata

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.structure.markdown.MarkdownStructuredDataDefinition
import ai.koog.prompt.structure.markdown.markdownStreamingParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/**
 * Data class representing a book with name, author, and description.
 */
@Serializable
data class Book(
    val bookName: String,
    val author: String,
    val description: String
) : ToolArgs

class BookTool() : SimpleTool<Book>() {
    companion object {
        const val NAME = "book"
    }

    override suspend fun doExecute(args: Book): String {
        println("${args.bookName} by ${args.author}:\n ${args.description}")
        return "Done"
    }

    override val argsSerializer: KSerializer<Book>
        get() = Book.serializer()
    override val descriptor: ToolDescriptor
        get() = ToolDescriptor(
            name = NAME,
            description = "A tool to parse book information from markdown",
            requiredParameters = listOf(),
            optionalParameters = listOf()
        )
}

/**
 * Parses a markdown stream and emits Book objects as they are completed.
 * @return A flow of Book objects parsed from the markdown stream
 */
fun parseMarkdownStreamToBooks(markdownStream: Flow<String>): Flow<Book> {
    return flow {
        markdownStreamingParser {
            var currentBookName = ""
            val bulletPoints = mutableListOf<String>()

            onHeader(1) { headerText ->
                // if we had a previous book, emit it
                if (currentBookName.isNotEmpty() && bulletPoints.isNotEmpty()) {
                    val author = bulletPoints.getOrNull(0) ?: ""
                    val description = bulletPoints.getOrNull(1) ?: ""
                    emit(Book(currentBookName, author, description))
                }

                currentBookName = headerText
                bulletPoints.clear()
            }

            onBullet { bulletText ->
                bulletPoints.add(bulletText)
            }

            onFinishStream {
                // Emit the last book if it exists
                if (currentBookName.isNotEmpty() && bulletPoints.isNotEmpty()) {
                    val author = bulletPoints.getOrNull(0) ?: ""
                    val description = bulletPoints.getOrNull(1) ?: ""
                    emit(Book(currentBookName, author, description))
                }
            }
        }.parseStream(markdownStream)
    }
}

fun markdownBookDefinition(): MarkdownStructuredDataDefinition {
    return MarkdownStructuredDataDefinition("bookList", schema = {
        markdown {
            header(1, "bookName")
            bulleted {
                item("author")
                item("description")
            }
        }
    }, examples = {
        markdown {
            header(1, "The Great Gatsby")
            bulleted {
                item("F. Scott Fitzgerald")
                item(
                    "A novel set in the Jazz Age that tells the story of Jay Gatsby's unrequited love for Daisy Buchanan."
                )
            }

            header(1, "To Kill a Mockingbird")
            bulleted {
                item("Harper Lee")
                item("<description>")
            }
        }
    })
}

fun formatOutput(books: List<Book>): String {
    return buildString {
        appendLine("Parsed Books:")
        appendLine()

        books.forEachIndexed { index, book ->
            appendLine("Book ${index + 1}:")
            appendLine("  Title: ${book.bookName}")
            appendLine("  Author: ${book.author}")
            appendLine("  Description: ${book.description}")
            appendLine()
        }
    }
}

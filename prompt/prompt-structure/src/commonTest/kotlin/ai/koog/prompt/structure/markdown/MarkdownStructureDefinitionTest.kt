package ai.koog.prompt.structure.markdown

import ai.koog.prompt.text.TextContentBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownStructureDefinitionTest {

    @Test
    fun testDefinitionWithSchemaOnly() {
        // Create a MarkdownStructuredDataDefinition with schema only
        val definition = MarkdownStructureDefinition(
            id = "test_format",
            schema = {
                +"# Header"
                +"Some content"
            }
        )

        // Create a TextContentBuilder and call the definition method
        val builder = TextContentBuilder()
        definition.definition(builder)
        val content = builder.build()

        // Verify the content contains expected strings
        assertTrue(content.contains("DEFINITION OF test_format"))
        assertTrue(content.contains("The test_format format is defined only and solely with Markdown"))
        assertTrue(content.contains("You must adhere to the following Markdown schema:"))
        assertTrue(content.contains("# Header"))
        assertTrue(content.contains("Some content"))

        // Verify that examples section is not included
        assertTrue(!content.contains("Here are some examples"))
    }

    @Test
    fun testDefinitionWithSchemaAndExamples() {
        // Create a MarkdownStructuredDataDefinition with schema and examples
        val definition = MarkdownStructureDefinition(
            id = "test_format",
            schema = {
                +"# Header"
                +"Some content"
            },
            examples = {
                +"# Example"
                +"Example content"
            }
        )

        // Create a TextContentBuilder and call the definition method
        val builder = TextContentBuilder()
        definition.definition(builder)
        val content = builder.build()

        // Verify the content contains expected strings
        assertTrue(content.contains("DEFINITION OF test_format"))
        assertTrue(content.contains("The test_format format is defined only and solely with Markdown"))
        assertTrue(content.contains("You must adhere to the following Markdown schema:"))
        assertTrue(content.contains("# Header"))
        assertTrue(content.contains("Some content"))

        // Verify that examples section is included
        assertTrue(content.contains("Here are some examples of the test_format format:"))
        assertTrue(content.contains("# Example"))
        assertTrue(content.contains("Example content"))
    }

    @Test
    fun testDefinitionStructure() {
        // Create a MarkdownStructuredDataDefinition with schema and examples
        val definition = MarkdownStructureDefinition(
            id = "test_format",
            schema = {
                +"# Schema Header"
                +"Schema content"
            },
            examples = {
                +"# Example Header"
                +"Example content"
            }
        )

        // Create a TextContentBuilder and call the definition method
        val builder = TextContentBuilder()
        definition.definition(builder)
        val content = builder.build()

        assertEquals(
            """
                DEFINITION OF test_format
                The test_format format is defined only and solely with Markdown, without any additional characters, backticks or anything similar.
                You must adhere to the following Markdown schema:# Schema Header
                Schema content
                Here are some examples of the test_format format:# Example Header
                Example content
            """.trimIndent(),
            content
        )
    }
}

package ai.koog.prompt.structure.markdown

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownParserLineMatchingTest {

    @Test
    fun testFeatureLineMatching() = runTest {
        val matchedLines = mutableListOf<String>()

        val parser = markdownParser {
            onLineMatching(Regex("^Feature:.*")) { line ->
                matchedLines.add(line)
            }
        }

        val markdown = """
            # Product
            ## Description
            Feature: Basic functionality
            Not a feature line
            Feature: Advanced functionality
        """.trimIndent()

        parser(markdown)

        assertEquals(2, matchedLines.size, "Should match two lines")
        assertTrue(matchedLines.contains("Feature: Basic functionality"), "Should match the first feature line")
        assertTrue(matchedLines.contains("Feature: Advanced functionality"), "Should match the second feature line")
    }

    @Test
    fun testMarkdownParserMatchesAllLines() = runTest {
        val allLines = mutableListOf<String>()

        val parser = markdownParser {
            onLineMatching(null) { line ->
                allLines.add(line)
            }
        }

        val markdown = """
            # Product
            ## Description
            Feature: Basic functionality
            Not a feature line
            Feature: Advanced functionality
        """.trimIndent()

        parser(markdown)

        assertEquals(5, allLines.size, "Should match all lines")
        assertEquals("# Product", allLines[0], "First line should match")
        assertEquals("## Description", allLines[1], "Second line should match")
        assertEquals("Feature: Basic functionality", allLines[2], "Third line should match")
        assertEquals("Not a feature line", allLines[3], "Fourth line should match")
        assertEquals("Feature: Advanced functionality", allLines[4], "Fifth line should match")
    }

    @Test
    fun testMarkdownParserLineMatching() = runTest {
        val featureLines = mutableListOf<String>()
        val headerLines = mutableListOf<String>()
        val allLines = mutableListOf<String>()

        val parser = markdownParser {
            onLineMatching(Regex("^Feature:.*")) { line ->
                featureLines.add(line)
            }

            onLineMatching(Regex("^#.*")) { line ->
                headerLines.add(line)
            }

            onLineMatching(null) { line ->
                allLines.add(line)
            }
        }

        val markdown = """
            # Product
            ## Description
            Feature: Basic functionality
            Not a feature line
            Feature: Advanced functionality
        """.trimIndent()

        parser(markdown)

        assertEquals(2, featureLines.size, "Should match two feature lines")
        assertEquals(2, headerLines.size, "Should match two header lines")
        assertEquals(5, allLines.size, "Should match all lines")
    }

    @Test
    fun testMarkdownParserFeatureLineMatching() = runTest {
        val matchedLines = mutableListOf<String>()

        val parser = markdownStreamingParser {
            onLineMatching(Regex("^Feature:.*")) { line ->
                matchedLines.add(line)
            }
        }

        val markdownFlow = flow {
            emit("# Product\n## Description\n")
            emit("Feature: Basic functionality\n")
            emit("Not a feature line\n")
            emit("Feature: Advanced functionality")
        }

        parser.parseStream(markdownFlow)

        assertEquals(2, matchedLines.size, "Should match two lines")
        assertTrue(matchedLines.contains("Feature: Basic functionality"), "Should match the first feature line")
        assertTrue(matchedLines.contains("Feature: Advanced functionality"), "Should match the second feature line")
    }

    @Test
    fun testFeatureLinesAreParsedCorrectly() = runTest {
        val matchedLines = mutableListOf<String>()

        val parser = markdownStreamingParser {
            onLineMatching(Regex("^Feature:.*")) { line ->
                matchedLines.add(line)
            }
        }

        val markdownFlow = flow {
            emit("# Product\n## Description\n")
            emit("Feature: Basic")
            emit(" functionality\n")
            emit("Not a feature")
            emit("line\n")
            emit("Feature: Advanced functionality")
        }

        parser.parseStream(markdownFlow)

        assertEquals(2, matchedLines.size, "Should match two lines")
        assertTrue(matchedLines.contains("Feature: Basic functionality"), "Should match the first feature line")
        assertTrue(matchedLines.contains("Feature: Advanced functionality"), "Should match the second feature line")
    }
}

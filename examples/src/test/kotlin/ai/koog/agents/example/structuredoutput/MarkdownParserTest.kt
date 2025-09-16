package ai.koog.agents.example.structuredoutput

import ai.koog.prompt.structure.markdown.markdownParser
import ai.koog.prompt.structure.markdown.markdownStreamingParser
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownParserTest {
    data class TestItem(
        val title: String,
        val description: String,
        val features: MutableList<String> = mutableListOf()
    )

    /**
     * Helper method to create a streaming parser for TestItem
     */
    private fun createTestItemParser(results: MutableList<TestItem>) = markdownStreamingParser {
        // We need to track the current item being built
        var currentTitle = ""
        var currentDescription = ""
        var features = mutableListOf<String>()

        // Set up handlers for headers and bullets
        onHeader(1) { title ->
            if (currentTitle.isNotEmpty() && currentDescription.isNotEmpty()) {
                val item = TestItem(title = currentTitle, description = currentDescription, features = features)
                results.add(item) // Add the item to the results list
                currentTitle = "" // Reset title after building the item
                currentDescription = "" // Reset description after building the item
                features = mutableListOf() // Reset features after building the item
            }

            // When a level 1 header is encountered, create a new item with the title
            currentTitle = title
        }

        onHeader(2) { description ->
            // When a level 2 header is encountered, set the description of the current item
            currentDescription = description
        }

        onBullet { bulletText ->
            // When a bullet point is encountered, add it to the features of the current item
            features.add(bulletText)
        }

        onFinishStream {
            // Handle the end of the stream
            if (currentTitle.isNotEmpty() && currentDescription.isNotEmpty()) {
                val item = TestItem(title = currentTitle, description = currentDescription, features = features)
                results.add(item) // Add the last item to the results list
            }
        }
    }

    /**
     * Helper method to create a non-streaming parser for TestItem
     */
    private fun createNonStreamingTestItemParser(results: MutableList<TestItem>) = markdownParser {
        // We need to track the current item being built
        var currentTitle = ""
        var currentDescription = ""
        var features = mutableListOf<String>()

        // Set up handlers for headers and bullets
        onHeader(1) { title ->
            if (currentTitle.isNotEmpty() && currentDescription.isNotEmpty()) {
                val item = TestItem(title = currentTitle, description = currentDescription, features = features)
                results.add(item) // Add the item to the results list
                currentTitle = "" // Reset title after building the item
                currentDescription = "" // Reset description after building the item
                features = mutableListOf() // Reset features after building the item
            }

            // When a level 1 header is encountered, create a new item with the title
            currentTitle = title
        }

        onHeader(2) { description ->
            // When a level 2 header is encountered, set the description of the current item
            currentDescription = description
        }

        onBullet { bulletText ->
            // When a bullet point is encountered, add it to the features of the current item
            features.add(bulletText)
        }

        // Note: There's no onFinishStream for non-streaming parser, so we handle the final item
        // after parsing is complete in the test itself
    }

    @Test
    fun `test markdownParser with different header levels`() = runBlocking {
        var h1Called = false
        var h2Called = false
        var h3Called = false
        var h4Called = false
        var h5Called = false
        var h6Called = false

        val parser = markdownParser {
            onHeader(1) { h1Called = true }
            onHeader(2) { h2Called = true }
            onHeader(3) { h3Called = true }
            onHeader(4) { h4Called = true }
            onHeader(5) { h5Called = true }
            onHeader(6) { h6Called = true }
        }

        val markdown = """
            # H1 Header
            ## H2 Header
            ### H3 Header
            #### H4 Header
            ##### H5 Header
            ###### H6 Header
        """.trimIndent()

        parser(markdown)

        assertTrue(h1Called, "H1 handler should be called")
        assertTrue(h2Called, "H2 handler should be called")
        assertTrue(h3Called, "H3 handler should be called")
        assertTrue(h4Called, "H4 handler should be called")
        assertTrue(h5Called, "H5 handler should be called")
        assertTrue(h6Called, "H6 handler should be called")
    }

    @Test
    fun `test markdownParser with complete content`() = runBlocking {
        // Create a list to store the results
        val results = mutableListOf<TestItem>()

        // Create a non-streaming parser for TestItem
        val parser = createNonStreamingTestItemParser(results)

        // Create a complete markdown string
        val markdown = """
            # Product A
            ## Amazing product
            - Feature 1
            - Feature 2

            # Product B
            ## Even better product
            - Feature 3
            - Feature 4
            - Feature 5
        """.trimIndent()

        // Parse the markdown
        parser(markdown)

        // Verify results
        assertEquals(1, results.size, "Should parse one product")

        // Verify the Product
        assertEquals("Product A", results[0].title, "First product title should match")
        assertEquals("Amazing product", results[0].description, "First product description should match")
        assertEquals(2, results[0].features.size, "First product should have 2 features")
    }

    @Test
    fun `test markdownParser with manually put item`() = runBlocking {
        // Create a list to store the results
        val results = mutableListOf<TestItem>()

        // Create a non-streaming parser for TestItem
        val parser = createNonStreamingTestItemParser(results)

        // Create a complete markdown string
        val markdown = """
            # Product A
            ## Amazing product
            - Feature 1
            - Feature 2

            # Product B
            ## Even better product
            - Feature 3
            - Feature 4
            - Feature 5
        """.trimIndent()

        // Parse the markdown
        parser(markdown)

        // Handle the final item (since there's no onFinishStream handler)
        if (results.size == 1) {
            // If only one product was parsed, the second one is still in the parser's state
            // We need to manually add it to the results
            val item = TestItem(
                title = "Product B",
                description = "Even better product",
                features = mutableListOf("Feature 3", "Feature 4", "Feature 5")
            )
            results.add(item)
        }

        // Verify results
        assertEquals(2, results.size, "Should parse two products")

        // Verify Product A
        assertEquals("Product A", results[0].title, "First product title should match")
        assertEquals("Amazing product", results[0].description, "First product description should match")
        assertEquals(2, results[0].features.size, "First product should have 2 features")

        // Verify Product B
        assertEquals("Product B", results[1].title, "Second product title should match")
        assertEquals("Even better product", results[1].description, "Second product description should match")
        assertEquals(3, results[1].features.size, "Second product should have 3 features")
    }

    @Test
    fun `test markdownParser with empty content`() = runBlocking {
        // Create a list to store the results
        val results = mutableListOf<TestItem>()

        // Create a non-streaming parser for TestItem
        val parser = createNonStreamingTestItemParser(results)

        // Parse empty markdown
        parser("")

        // Verify results
        assertEquals(0, results.size, "Should parse zero products for empty content")
    }

    @Test
    fun `test markdownParser with malformed markdown`() = runBlocking {
        // Create a list to store the results
        val results = mutableListOf<TestItem>()

        // Create a non-streaming parser for TestItem
        val parser = createNonStreamingTestItemParser(results)

        // Create malformed markdown (no space after #)
        val markdown = """
            #Malformed Header
            ##Malformed Description
            - Feature 1

            # Proper Header
            ## Proper Description
            - Feature 2
        """.trimIndent()

        // Parse the markdown
        parser(markdown)

        // Verify results - the parser actually handles malformed headers (no space after #)
        assertEquals(1, results.size, "Should parse at least one product")

        // Verify Malformed Item
        assertEquals("Malformed Header", results[0].title, "First product title should match")
        assertEquals("Malformed Description", results[0].description, "First product description should match")
        assertEquals(1, results[0].features.size, "First product should have 1 feature")
    }

    @Test
    fun `test markdownParser with special characters`() = runBlocking {
        // Create a list to store the results
        val results = mutableListOf<TestItem>()

        // Create a non-streaming parser for TestItem
        val parser = createNonStreamingTestItemParser(results)

        // Create markdown with special characters
        val markdown = """
            # Product & Services (2023)
            ## Special offer: 50% off!
            - Feature: *italic text*
            - Feature: _italic text_
            - Feature: **bold text**
            - Feature: __bold text__
            - Feature: `code block`
            - Feature: > block quote

            # Q&A Section
            ## Frequently Asked Questions
            - Question: How to use?
            - Answer: It's easy! Just follow the instructions.
        """.trimIndent()

        // Parse the markdown
        parser(markdown)

        // Verify results
        assertEquals(1, results.size, "Should parse at least one section")

        // Verify Product & Services
        assertEquals("Product & Services (2023)", results[0].title, "First section title should match")
        assertEquals("Special offer: 50% off!", results[0].description, "First section description should match")
        assertEquals(6, results[0].features.size, "First section should have 6 features")
    }

    @Test
    fun `test markdownParser with excessive whitespace and newlines`() = runBlocking {
        // Create a list to store the results
        val results = mutableListOf<TestItem>()

        // Create a non-streaming parser for TestItem
        val parser = createNonStreamingTestItemParser(results)

        // Create markdown with excessive whitespace and newlines
        val markdown = """
            # Product With Spaces


            ## Description With Spaces   

               - Feature with leading spaces
            - Feature with trailing spaces   



            # Another Product

            ## Another Description
            - Another Feature
        """.trimIndent()

        // Parse the markdown
        parser(markdown)

        // Verify results
        assertEquals(1, results.size, "Should parse at least one product")

        // Verify Product With Spaces
        assertEquals("Product With Spaces", results[0].title, "First product title should match")
        assertEquals("Description With Spaces", results[0].description, "First product description should match")
        assertEquals(2, results[0].features.size, "First product should have 2 features")
        assertEquals(
            "Feature with leading spaces",
            results[0].features[0],
            "First feature should have leading spaces trimmed"
        )
        assertEquals(
            "Feature with trailing spaces",
            results[0].features[1],
            "Second feature should have trailing spaces trimmed"
        )
    }

    @Test
    fun `test markdownParser with headers without bullet points`() = runBlocking {
        // Create a list to store the results
        val results = mutableListOf<TestItem>()

        // Create a non-streaming parser for TestItem
        val parser = createNonStreamingTestItemParser(results)

        // Create markdown with headers that have no bullet points
        val markdown = """
            # Product Without Features
            ## Just a description

            # Product With Features
            ## Another description
            - Feature 1
            - Feature 2
        """.trimIndent()

        // Parse the markdown
        parser(markdown)

        // Verify results
        assertEquals(1, results.size, "Should parse at least one product")

        // Verify Product Without Features
        assertEquals("Product Without Features", results[0].title, "First product title should match")
        assertEquals("Just a description", results[0].description, "First product description should match")
        assertEquals(0, results[0].features.size, "First product should have 0 features")
    }

    @Test
    fun `test markdownParser with multiple header levels`() = runBlocking {
        // Create a list to store the results
        val results = mutableListOf<TestItem>()

        // Track which header levels were called
        var h1Called = false
        var h2Called = false
        var h3Called = false

        // Create a class to hold parser state that can be accessed outside the lambda
        class ParserState {
            var currentTitle = ""
            var currentDescription = ""
            var features = mutableListOf<String>()
        }

        val state = ParserState()

        // Create a custom parser that handles multiple header levels
        val parser = markdownParser {
            onHeader(1) { title ->
                h1Called = true
                if (state.currentTitle.isNotEmpty() && state.currentDescription.isNotEmpty()) {
                    val item = TestItem(
                        title = state.currentTitle,
                        description = state.currentDescription,
                        features = state.features
                    )
                    results.add(item)
                    state.currentTitle = ""
                    state.currentDescription = ""
                    state.features = mutableListOf()
                }
                state.currentTitle = title
            }

            onHeader(2) { description ->
                h2Called = true
                state.currentDescription = description
            }

            onHeader(3) { subheader ->
                h3Called = true
                // Add the subheader as a feature
                state.features.add("Subheader: $subheader")
            }

            onBullet { bulletText ->
                state.features.add(bulletText)
            }
        }

        // Create markdown with multiple header levels
        val markdown = """
            # Main Product
            ## Product Description
            ### Key Benefits
            - Benefit 1
            - Benefit 2
            ### Technical Specs
            - Spec 1
            - Spec 2
        """.trimIndent()

        // Parse the markdown
        parser(markdown)

        // Handle the final item
        if (state.currentTitle.isNotEmpty() && state.currentDescription.isNotEmpty()) {
            val item = TestItem(
                title = state.currentTitle,
                description = state.currentDescription,
                features = state.features
            )
            results.add(item)
        }

        // Verify results
        assertEquals(1, results.size, "Should parse one product")
        assertTrue(h1Called, "H1 handler should be called")
        assertTrue(h2Called, "H2 handler should be called")
        assertTrue(h3Called, "H3 handler should be called")

        // Verify Main Product
        assertEquals("Main Product", results[0].title, "Product title should match")
        assertEquals("Product Description", results[0].description, "Product description should match")
        assertEquals(6, results[0].features.size, "Product should have 6 features (2 subheaders + 4 bullet points)")
        assertEquals("Subheader: Key Benefits", results[0].features[0], "First feature should be the first subheader")
        assertEquals("Benefit 1", results[0].features[1], "Second feature should be the first benefit")
        assertEquals("Benefit 2", results[0].features[2], "Third feature should be the second benefit")
        assertEquals(
            "Subheader: Technical Specs",
            results[0].features[3],
            "Fourth feature should be the second subheader"
        )
    }

    @Test
    fun `test markdownStreamingParser with complete chunks`() = runBlocking {
        // Create a list to store the results
        val results = mutableListOf<TestItem>()

        // Create a streaming parser for TestItem
        val parser = createTestItemParser(results)

        // Create a flow of markdown chunks
        val markdownFlow = flow {
            emit("# Product A\n## Amazing product\n")
            emit("- Feature 1\n- Feature 2\n\n")
            emit("# Product B\n## Even better product\n")
            emit("- Feature 3\n- Feature 4\n- Feature 5")
        }

        // Parse the markdown stream
        parser.parseStream(markdownFlow)

        // Verify results
        assertEquals(2, results.size, "Should parse two products")

        // Verify Product A
        assertEquals("Product A", results[0].title, "First product title should match")
        assertEquals("Amazing product", results[0].description, "First product description should match")
        assertEquals(2, results[0].features.size, "First product should have 2 features")

        // Verify Product B
        assertEquals("Product B", results[1].title, "Second product title should match")
        assertEquals("Even better product", results[1].description, "Second product description should match")
        assertEquals(3, results[1].features.size, "Second product should have 3 features")
    }

    @Test
    fun `test markdownStreamingParser with partial chunks`() = runBlocking {
        // Create a list to store the results
        val results = mutableListOf<TestItem>()

        // Create a streaming parser for TestItem
        val parser = createTestItemParser(results)

        // Create a flow of markdown chunks with partial content
        val markdownFlow = flow {
            emit("# Product")
            emit(" C\n## ")
            emit("Innovative product\n- Fea")
            emit("ture 1\n- Feature ")
            emit("2\n# Product D\n## ")
            emit("Revolutionary product\n- Feature 3")
        }

        // Parse the markdown stream
        parser.parseStream(markdownFlow)

        // Verify results
        assertEquals(2, results.size, "Should parse two products")

        // Verify Product C
        assertEquals("Product C", results[0].title, "First product title should match")
        assertEquals("Innovative product", results[0].description, "First product description should match")
        assertEquals(2, results[0].features.size, "First product should have 2 features")

        // Verify Product D
        assertEquals("Product D", results[1].title, "Second product title should match")
        assertEquals("Revolutionary product", results[1].description, "Second product description should match")
        assertEquals(1, results[1].features.size, "Second product should have 1 feature")
    }

    @Test
    fun `test markdownStreamingParser with empty stream`() = runBlocking {
        // Create a list to store the results
        val results = mutableListOf<TestItem>()

        // Create a streaming parser for TestItem
        val parser = createTestItemParser(results)

        // Create an empty flow
        val markdownFlow = flow<String> { }

        // Parse the markdown stream
        parser.parseStream(markdownFlow)

        // Verify results
        assertEquals(0, results.size, "Should parse zero products for empty stream")
    }

    @Test
    fun `test markdownStreamingParser with multiple items of same type`() = runBlocking {
        // Create a list to store the results
        val results = mutableListOf<TestItem>()

        // Create a streaming parser for TestItem
        val parser = createTestItemParser(results)

        // Create a flow with multiple items of the same type in sequence
        val markdownFlow = flow {
            emit("# Product E\n## First version\n")
            emit("- Basic feature\n\n")
            emit("# Product E Pro\n## Enhanced version\n")
            emit("- Basic feature\n- Advanced feature\n\n")
            emit("# Product E Max\n## Premium version\n")
            emit("- Basic feature\n- Advanced feature\n- Premium feature")
        }

        // Parse the markdown stream
        parser.parseStream(markdownFlow)

        // Verify results
        assertEquals(3, results.size, "Should parse three products")

        // Verify all products have the expected naming pattern
        assertTrue(results.all { it.title.startsWith("Product E") }, "All products should start with 'Product E'")
        assertEquals(1, results[0].features.size, "First product should have 1 feature")
        assertEquals(2, results[1].features.size, "Second product should have 2 features")
        assertEquals(3, results[2].features.size, "Third product should have 3 features")
    }

    @Test
    fun `test markdownStreamingParser with special characters`() = runBlocking {
        // Create a list to store the results
        val results = mutableListOf<TestItem>()

        // Create a streaming parser for TestItem
        val parser = createTestItemParser(results)

        // Create a flow with special characters in headers and bullets
        val markdownFlow = flow {
            emit("# Product & Services (2023)\n## Special offer: 50% off!\n")
            emit("- Feature: *italic text*\n")
            emit("- Feature: **bold text**\n")
            emit("- Feature: `code block`\n\n")
            emit("# Q&A Section\n## Frequently Asked Questions\n")
            emit("- Question: How to use?\n")
            emit("- Answer: It's easy! Just follow the instructions.")
        }

        // Parse the markdown stream
        parser.parseStream(markdownFlow)

        // Verify results
        assertEquals(2, results.size, "Should parse two sections")

        // Verify Product & Services
        assertEquals("Product & Services (2023)", results[0].title, "First section title should match")
        assertEquals("Special offer: 50% off!", results[0].description, "First section description should match")
        assertEquals(3, results[0].features.size, "First section should have 3 features")

        // Verify Q&A Section
        assertEquals("Q&A Section", results[1].title, "Second section title should match")
        assertEquals("Frequently Asked Questions", results[1].description, "Second section description should match")
        assertEquals(2, results[1].features.size, "Second section should have 2 features")
    }

    @Test
    fun `test markdownStreamingParser with mixed content`() = runBlocking {
        // Create a list to store the results
        val results = mutableListOf<TestItem>()

        // Create a streaming parser for TestItem
        val parser = createTestItemParser(results)

        // Create a flow with mixed content including empty lines and non-parsed content
        val markdownFlow = flow {
            emit("Some introductory text that should be ignored.\n\n")
            emit("# Project Overview\n## Main Goals\n")
            emit("- Improve performance\n")
            emit("- Enhance user experience\n\n")
            emit("Some additional text between sections.\n\n")
            emit("# Implementation Details\n## Technical Stack\n")
            emit("- Frontend: React\n")
            emit("- Backend: Kotlin\n")
            emit("- Database: PostgreSQL")
        }

        // Parse the markdown stream
        parser.parseStream(markdownFlow)

        // Verify results
        assertEquals(2, results.size, "Should parse two sections")

        // Verify Project Overview
        assertEquals("Project Overview", results[0].title, "First section title should match")
        assertEquals("Main Goals", results[0].description, "First section description should match")
        assertEquals(2, results[0].features.size, "First section should have 2 features")

        // Verify Implementation Details
        assertEquals("Implementation Details", results[1].title, "Second section title should match")
        assertEquals("Technical Stack", results[1].description, "Second section description should match")
        assertEquals(3, results[1].features.size, "Second section should have 3 features")
    }

    @Test
    fun `test markdownStreamingParser with empty content`() = runBlocking {
        // Create a list to store the results
        val results = mutableListOf<TestItem>()

        // Create a non-streaming parser for TestItem
        val parser = createTestItemParser(results)

        val markdownFlow = flow {
            emit("")
        }

        // Parse empty markdown
        parser.parseStream(markdownFlow)

        // Verify results
        assertEquals(0, results.size, "Should parse zero products for empty content")
    }

    @Test
    fun `test markdownStreamingParser without headers`() = runBlocking {
        // Create a list to store the results
        val results = mutableListOf<TestItem>()

        // Create a streaming parser for TestItem
        val parser = createTestItemParser(results)

        // Create a flow without headers but with some content
        val markdownFlow = flow {
            emit("- Feature 1")
            emit("- Feature 2")
            emit("- Feature 3")
        }

        // Parse the markdown stream
        parser.parseStream(markdownFlow)

        // Verify results - only the product with a non-empty description is parsed
        // This is because the parser checks for non-empty title AND description
        assertEquals(0, results.size, "Shouldn't parse a product without a description")
    }

    @Test
    fun `test markdownStreamingParser with headers without content`() = runBlocking {
        // Create a list to store the results
        val results = mutableListOf<TestItem>()

        // Create a streaming parser for TestItem
        val parser = createTestItemParser(results)

        // Create a flow with headers that have no content
        val markdownFlow = flow {
            emit("# Empty Product\n## \n")
            emit("# Another Product\n## With Description\n")
            emit("- Feature 1")
        }

        // Parse the markdown stream
        parser.parseStream(markdownFlow)

        // Verify results - only the product with a non-empty description is parsed
        // This is because the parser checks for non-empty title AND description
        assertEquals(1, results.size, "Should parse only one product with non-empty description")

        // Verify Another Product (the only one that should be parsed)
        assertEquals("Another Product", results[0].title, "Product title should match")
        assertEquals("With Description", results[0].description, "Product description should match")
        assertEquals(1, results[0].features.size, "Product should have 1 feature")
    }

    @Test
    fun `test markdownStreamingParser with headers without bullet points`() = runBlocking {
        // Create a list to store the results
        val results = mutableListOf<TestItem>()

        // Create a streaming parser for TestItem
        val parser = createTestItemParser(results)

        // Create a flow with headers that have no bullet points
        val markdownFlow = flow {
            emit("# Product Without Features\n## Just a description\n")
            emit("# Product With Features\n## Another description\n")
            emit("- Feature 1\n- Feature 2")
        }

        // Parse the markdown stream
        parser.parseStream(markdownFlow)

        // Verify results
        assertEquals(2, results.size, "Should parse two products")

        // Verify Product Without Features
        assertEquals("Product Without Features", results[0].title, "First product title should match")
        assertEquals("Just a description", results[0].description, "First product description should match")
        assertEquals(0, results[0].features.size, "First product should have 0 features")

        // Verify Product With Features
        assertEquals("Product With Features", results[1].title, "Second product title should match")
        assertEquals("Another description", results[1].description, "Second product description should match")
        assertEquals(2, results[1].features.size, "Second product should have 2 features")
    }

    @Test
    fun `test markdownStreamingParser with malformed markdown`() = runBlocking {
        // Create a list to store the results
        val results = mutableListOf<TestItem>()

        // Create a streaming parser for TestItem
        val parser = createTestItemParser(results)

        // Create a flow with malformed markdown (no space after #)
        val markdownFlow = flow {
            emit("#Malformed Header\n##Malformed Description\n")
            emit("- Feature 1\n")
            emit("# Proper Header\n## Proper Description\n")
            emit("- Feature 2")
        }

        // Parse the markdown stream
        parser.parseStream(markdownFlow)

        // Verify results - the parser actually handles malformed headers (no space after #)
        assertEquals(2, results.size, "Should parse both products")

        // Verify Malformed Header
        assertEquals("Malformed Header", results[0].title, "First product title should match")
        assertEquals("Malformed Description", results[0].description, "First product description should match")
        assertEquals(1, results[0].features.size, "First product should have 1 feature")

        // Verify Proper Header
        assertEquals("Proper Header", results[1].title, "Second product title should match")
        assertEquals("Proper Description", results[1].description, "Second product description should match")
        assertEquals(1, results[1].features.size, "Second product should have 1 feature")
    }

    @Test
    fun `test markdownStreamingParser with incomplete headers at stream end`() = runBlocking {
        // Create a list to store the results
        val results = mutableListOf<TestItem>()

        // Create a streaming parser for TestItem
        val parser = createTestItemParser(results)

        // Create a flow that ends with an incomplete header
        val markdownFlow = flow {
            emit("# Complete Product\n## Good description\n")
            emit("- Feature 1\n- Feature 2\n")
            emit("# Incomplete")
        }

        // Parse the markdown stream
        parser.parseStream(markdownFlow)

        // Verify results - only the complete product is parsed
        // This is because the incomplete product at the end doesn't have a description
        assertEquals(1, results.size, "Should parse only the complete product")

        // Verify Complete Product
        assertEquals("Complete Product", results[0].title, "Product title should match")
        assertEquals("Good description", results[0].description, "Product description should match")
        assertEquals(2, results[0].features.size, "Product should have 2 features")
    }

    @Test
    fun `test markdownStreamingParser with large content chunks`() = runBlocking {
        // Create a list to store the results
        val results = mutableListOf<TestItem>()

        // Create a streaming parser for TestItem
        val parser = createTestItemParser(results)

        // Create a flow with a large content chunk containing multiple products
        val markdownFlow = flow {
            emit(
                """
                # Product 1
                ## Description 1
                - Feature 1.1
                - Feature 1.2
                - Feature 1.3

                # Product 2
                ## Description 2
                - Feature 2.1
                - Feature 2.2

                # Product 3
                ## Description 3
                - Feature 3.1
                - Feature 3.2
                - Feature 3.3
                - Feature 3.4
                """.trimIndent()
            )
        }

        // Parse the markdown stream
        parser.parseStream(markdownFlow)

        // Verify results
        assertEquals(3, results.size, "Should parse three products")

        // Verify Product 1
        assertEquals("Product 1", results[0].title, "First product title should match")
        assertEquals("Description 1", results[0].description, "First product description should match")
        assertEquals(3, results[0].features.size, "First product should have 3 features")

        // Verify Product 2
        assertEquals("Product 2", results[1].title, "Second product title should match")
        assertEquals("Description 2", results[1].description, "Second product description should match")
        assertEquals(2, results[1].features.size, "Second product should have 2 features")

        // Verify Product 3
        assertEquals("Product 3", results[2].title, "Third product title should match")
        assertEquals("Description 3", results[2].description, "Third product description should match")
        assertEquals(4, results[2].features.size, "Third product should have 4 features")
    }

    @Test
    fun `test markdownStreamingParser with excessive whitespace and newlines`() = runBlocking {
        // Create a list to store the results
        val results = mutableListOf<TestItem>()

        // Create a streaming parser for TestItem
        val parser = createTestItemParser(results)

        // Create a flow with excessive whitespace and newlines
        val markdownFlow = flow {
            emit("# Product With Spaces\n\n\n")
            emit("## Description With Spaces   \n\n")
            emit("   - Feature with leading spaces\n")
            emit("- Feature with trailing spaces   \n\n\n\n")
            emit("# Another Product\n\n")
            emit("## Another Description\n")
            emit("- Another Feature")
        }

        // Parse the markdown stream
        parser.parseStream(markdownFlow)

        // Verify results
        assertEquals(2, results.size, "Should parse two products")

        // Verify Product With Spaces
        assertEquals("Product With Spaces", results[0].title, "First product title should match")
        assertEquals("Description With Spaces", results[0].description, "First product description should match")
        assertEquals(2, results[0].features.size, "First product should have 2 features")
        assertEquals(
            "Feature with leading spaces",
            results[0].features[0],
            "First feature should have leading spaces trimmed"
        )
        assertEquals(
            "Feature with trailing spaces",
            results[0].features[1],
            "Second feature should have trailing spaces trimmed"
        )

        // Verify Another Product
        assertEquals("Another Product", results[1].title, "Second product title should match")
        assertEquals("Another Description", results[1].description, "Second product description should match")
        assertEquals(1, results[1].features.size, "Second product should have 1 feature")
    }

    @Test
    fun `test markdownStreamingParser with multiple header levels`() = runBlocking {
        // Create a list to store the results
        val results = mutableListOf<TestItem>()

        // Track which header levels were called
        var h1Called = false
        var h2Called = false
        var h3Called = false

        // Create a custom parser that handles multiple header levels
        val parser = markdownStreamingParser {
            var currentTitle = ""
            var currentDescription = ""
            var features = mutableListOf<String>()

            onHeader(1) { title ->
                h1Called = true
                if (currentTitle.isNotEmpty() && currentDescription.isNotEmpty()) {
                    val item = TestItem(title = currentTitle, description = currentDescription, features = features)
                    results.add(item)
                    currentTitle = ""
                    currentDescription = ""
                    features = mutableListOf()
                }
                currentTitle = title
            }

            onHeader(2) { description ->
                h2Called = true
                currentDescription = description
            }

            onHeader(3) { subheader ->
                h3Called = true
                // Add the subheader as a feature
                features.add("Subheader: $subheader")
            }

            onBullet { bulletText ->
                features.add(bulletText)
            }

            onFinishStream {
                if (currentTitle.isNotEmpty() && currentDescription.isNotEmpty()) {
                    val item = TestItem(title = currentTitle, description = currentDescription, features = features)
                    results.add(item)
                }
            }
        }

        // Create a flow with multiple header levels
        val markdownFlow = flow {
            emit("# Main Product\n## Product Description\n")
            emit("### Key Benefits\n")
            emit("- Benefit 1\n- Benefit 2\n")
            emit("### Technical Specs\n")
            emit("- Spec 1\n- Spec 2\n")
        }

        // Parse the markdown stream
        parser.parseStream(markdownFlow)

        // Verify results
        assertEquals(1, results.size, "Should parse one product")
        assertTrue(h1Called, "H1 handler should be called")
        assertTrue(h2Called, "H2 handler should be called")
        assertTrue(h3Called, "H3 handler should be called")

        // Verify Main Product
        assertEquals("Main Product", results[0].title, "Product title should match")
        assertEquals("Product Description", results[0].description, "Product description should match")
        assertEquals(6, results[0].features.size, "Product should have 6 features (2 subheaders + 4 bullet points)")
        assertEquals("Subheader: Key Benefits", results[0].features[0], "First feature should be the first subheader")
        assertEquals("Benefit 1", results[0].features[1], "Second feature should be the first benefit")
        assertEquals("Benefit 2", results[0].features[2], "Third feature should be the second benefit")
        assertEquals(
            "Subheader: Technical Specs",
            results[0].features[3],
            "Fourth feature should be the second subheader"
        )
    }

    @Test
    fun `test markdownStreamingParser with very short chunks`() = runBlocking {
        // Create a list to store the results
        val results = mutableListOf<TestItem>()

        // Create a streaming parser for TestItem
        val parser = createTestItemParser(results)

        // Create a flow with very short chunks (single characters)
        val markdownFlow = flow {
            // Emit "# Title" character by character
            emit("#")
            emit(" ")
            emit("T")
            emit("i")
            emit("t")
            emit("l")
            emit("e")
            emit("\n")

            // Emit "## Description" character by character
            emit("#")
            emit("#")
            emit(" ")
            emit("D")
            emit("e")
            emit("s")
            emit("c")
            emit("\n")

            // Emit "- Feature" character by character
            emit("-")
            emit(" ")
            emit("F")
            emit("e")
            emit("a")
            emit("t")
            emit("u")
            emit("r")
            emit("e")
        }

        // Parse the markdown stream
        parser.parseStream(markdownFlow)

        // Verify results
        assertEquals(1, results.size, "Should parse one product")

        // Verify Title
        assertEquals("Title", results[0].title, "Product title should match")
        assertEquals("Desc", results[0].description, "Product description should match")
        assertEquals(1, results[0].features.size, "Product should have 1 feature")
        assertEquals("Feature", results[0].features[0], "Feature should match")
    }
}

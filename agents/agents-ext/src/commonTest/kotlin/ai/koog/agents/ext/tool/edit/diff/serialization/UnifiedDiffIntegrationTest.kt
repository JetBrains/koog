package ai.koog.agents.ext.tool.edit.diff.serialization

import ai.koog.agents.ext.tool.edit.diff.MyersDiffAlgorithm
import ai.koog.agents.ext.tool.edit.diff.UnifiedDiffSerializer
import kotlin.test.Test
import kotlin.test.assertEquals

class UnifiedDiffIntegrationTest {

    @Test
    fun testMultiFileIntegration() {
        val serializer = UnifiedDiffSerializer<String>()

        // Simulate multiple file changes
        val file1Old = listOf("line 1", "line 2", "line 3")
        val file1New = listOf("line 1", "modified line", "line 3")

        val file2Old = listOf("file 2 line 1", "file 2 line 2")
        val file2New = listOf("file 2 line 1", "file 2 line 2", "file 2 line 3")

        // Generate diffs for each file
        val diff1 = serializer.serialize(
            MyersDiffAlgorithm<String>().diff(file1Old, file1New),
            oldPath = "file1.txt",
            newPath = "file1.txt"
        )

        val diff2 = serializer.serialize(
            MyersDiffAlgorithm<String>().diff(file2Old, file2New),
            oldPath = "file2.txt",
            newPath = "file2.txt"
        )

        // Combine with a blank line between
        val combinedDiff = listOf(diff1, diff2)
            .filter { it.isNotBlank() }
            .joinToString("\n")

        val expected = """
              --- a/file1.txt
              +++ b/file1.txt
              @@ -1,3 +1,3 @@
               line 1
              -line 2
              +modified line
               line 3
        
              --- a/file2.txt
              +++ b/file2.txt
              @@ -1,2 +1,3 @@
               file 2 line 1
               file 2 line 2
              +file 2 line 3
              
            """.trimIndent()

        assertEquals(expected, combinedDiff)
    }

    @Test
    fun testPOSIXCompliance() {
        val serializer = UnifiedDiffSerializer<String>()

        val oldContent = listOf("line 1", "line 2", "line 3", "line 4")
        val newContent = listOf("line 1", "modified line 2", "line 3", "line 4", "line 5")

        val diff = serializer.serialize(
            MyersDiffAlgorithm<String>().diff(oldContent, newContent),
            oldPath = "file.txt",
            newPath = "file.txt"
        )

        val expected = """
              --- a/file.txt
              +++ b/file.txt
              @@ -1,4 +1,5 @@
               line 1
              -line 2
              +modified line 2
               line 3
               line 4
              +line 5
              
            """.trimIndent()

        assertEquals(expected, diff)
    }

    @Test
    fun testEdgeCases() {
        val serializer = UnifiedDiffSerializer<String>()

        // empty files
        val emptyDiff = serializer.serialize(
            MyersDiffAlgorithm<String>().diff(emptyList(), emptyList())
        )
        assertEquals("", emptyDiff)

        // binary-like content
        val oldLine = "Binary content " + "x".repeat(1000)
        val newLine = "Binary content " + "y".repeat(1000)
        val binaryDiff = serializer.serialize(
            MyersDiffAlgorithm<String>().diff(listOf(oldLine), listOf(newLine)),
            oldPath = "binary.bin",
            newPath = "binary.bin"
        )

        val expectedBinary = """
              --- a/binary.bin
              +++ b/binary.bin
              @@ -1,1 +1,1 @@
              -$oldLine
              +$newLine
              
            """.trimIndent()

        assertEquals(expectedBinary, binaryDiff)
    }

    @Test
    fun testSmallKotlinFileDiff() {
        val serializer = UnifiedDiffSerializer<String>()
        val oldKotlin = """
            package example

            fun add(a: Int, b: Int): Int {
                return a + b
            }

            fun subtract(a: Int, b: Int): Int {
                return a - b
            }
        """.trimIndent().lines()

        val newKotlin = """
            package example

            fun add(a: Int, b: Int): Int {
                return a + b
            }

            fun subtract(a: Int, b: Int): Int {
                return a - b
            }

            fun multiply(a: Int, b: Int): Int {
                return a * b
            }
        """.trimIndent().lines()

        val diff = serializer.serialize(
            MyersDiffAlgorithm<String>().diff(oldKotlin, newKotlin),
            oldPath = "Calculator.kt",
            newPath = "Calculator.kt"
        )

        val expected = """
            --- a/Calculator.kt
            +++ b/Calculator.kt
            @@ -7,3 +7,7 @@
             fun subtract(a: Int, b: Int): Int {
                 return a - b
             }
            +
            +fun multiply(a: Int, b: Int): Int {
            +    return a * b
            +}

        """.trimIndent()

        assertEquals(expected, diff)
    }

    @Test
    fun testPythonDecoratorAndAsyncDiff() {
        val serializer = UnifiedDiffSerializer<String>()
        val oldPython = """
            def fetch_data(url):
                response = requests.get(url)
                return response.json()
        """.trimIndent().lines()

        val newPython = """
            import asyncio

            @timed
            async def fetch_data(url):
                await asyncio.sleep(1)
                response = requests.get(url)
                return response.json()

            def process(data):
                return [d['id'] for d in data]
        """.trimIndent().lines()

        val diff = serializer.serialize(
            MyersDiffAlgorithm<String>().diff(oldPython, newPython),
            oldPath = "network.py",
            newPath = "network.py"
        )

        val expected = """
            --- a/network.py
            +++ b/network.py
            @@ -1,3 +1,10 @@
            -def fetch_data(url):
            +import asyncio
            +
            +@timed
            +async def fetch_data(url):
            +    await asyncio.sleep(1)
                 response = requests.get(url)
                 return response.json()
            +
            +def process(data):
            +    return [d['id'] for d in data]
            
        """.trimIndent()

        assertEquals(expected, diff)
    }
}

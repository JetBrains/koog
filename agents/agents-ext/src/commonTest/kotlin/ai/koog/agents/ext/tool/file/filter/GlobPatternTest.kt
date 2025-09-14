package ai.koog.agents.ext.tool.file.filter

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlobPatternTest {
    @Test
    fun testBasicMatching() {
        val pattern = GlobPattern("*.txt")
        assertTrue(pattern.matches("file.txt"))
        assertTrue(pattern.matches("test.txt"))
        assertFalse(pattern.matches("file.doc"))
        assertFalse(pattern.matches("path/file.txt"))
    }

    @Test
    fun testDirectoryMatching() {
        val pattern = GlobPattern("src/**/*.kt")
        assertTrue(pattern.matches("src/main/kotlin/File.kt"))
        assertTrue(pattern.matches("src/test/kotlin/Test.kt"))
        assertFalse(pattern.matches("test/src/File.kt"))
        assertFalse(pattern.matches("src/main/kotlin/File.java"))
    }

    @Test
    fun testDirectoryPathMatching() {
        val pattern = GlobPattern("src/**/kotlin")
        assertTrue(pattern.matches("src/main/kotlin"))
        assertTrue(pattern.matches("src/test/kotlin"))
        assertFalse(pattern.matches("test/src/kotlin"))
        assertFalse(pattern.matches("src/main/java"))
    }

    @Test
    fun testWildcardMatching() {
        val pattern = GlobPattern("test-?.txt")
        assertTrue(pattern.matches("test-1.txt"))
        assertTrue(pattern.matches("test-a.txt"))
        assertFalse(pattern.matches("test-12.txt"))
        assertFalse(pattern.matches("test-.txt"))
    }

    @Test
    fun testCharacterSetMatching() {
        val pattern = GlobPattern("file-[abc].txt")
        assertTrue(pattern.matches("file-a.txt"))
        assertTrue(pattern.matches("file-b.txt"))
        assertTrue(pattern.matches("file-c.txt"))
        assertFalse(pattern.matches("file-d.txt"))
    }

    @Test
    fun testMultiplePatterns() {
        val pattern = GlobPattern("src/**/*.[jk]t")
        assertTrue(pattern.matches("src/main/kotlin/File.kt"))
        assertTrue(pattern.matches("src/test/java/Test.jt"))
        assertFalse(pattern.matches("src/main/kotlin/File.txt"))
    }

    @Test
    fun testAlternativesMatching() {
        val pattern = GlobPattern("file.{txt,doc,pdf}")
        assertTrue(pattern.matches("file.txt"))
        assertTrue(pattern.matches("file.doc"))
        assertTrue(pattern.matches("file.pdf"))
        assertFalse(pattern.matches("file.jpg"))
        assertFalse(pattern.matches("file."))
    }

    @Test
    fun testAlternativesWithGlobPatterns() {
        val pattern = GlobPattern("src/{test,main}/**/*.{kt,java}")
        assertTrue(pattern.matches("src/test/kotlin/Test.kt"))
        assertTrue(pattern.matches("src/main/java/Main.java"))
        assertTrue(pattern.matches("src/test/nested/File.kt"))
        assertFalse(pattern.matches("src/other/File.kt"))
        assertFalse(pattern.matches("src/test/File.cpp"))
    }

    @Test
    fun testDoubleStarPrefix() {
        val pattern = GlobPattern("**/a")
        assertTrue(pattern.matches("a"))
        assertTrue(pattern.matches("/a"))
        assertTrue(pattern.matches("b/a"))
        assertTrue(pattern.matches("/b/a"))
        assertFalse(pattern.matches("ab"))
        assertFalse(pattern.matches("/ab"))
    }

    @Test
    fun testRegexElementsMatching() {
        val pattern = GlobPattern("*$")
        assertFalse(pattern.matches("a"))
        assertTrue(pattern.matches("a$"))
    }
}

package ai.koog.skills.discovery

import ai.koog.rag.base.files.JVMFileSystemProvider
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SkillsDiscoveryTest {
    @Test
    fun `test discoverSkills parses skill frontmatter`() = runTest {
        val root = createTempDirectory()
        val skillDirectory = root.resolve("pdf-processing").also { it.createDirectories() }
        skillDirectory.resolve("SKILL.md").writeText(
            """
            ---
            name: pdf-processing
            description: Extract PDF text and merge documents.
            license: Apache-2.0
            compatibility: Requires local filesystem access
            metadata:
              author: koog
              version: "1.0"
            allowed-tools: Bash(git:*) Read
            ---

            Skill body.
            """.trimIndent()
        )

        val discovered = discoverSkills(JVMFileSystemProvider.ReadOnly, listOf(root.absolutePathString()))

        assertEquals(1, discovered.size)
        val skill = discovered.single()
        assertEquals("pdf-processing", skill.name)
        assertEquals("Extract PDF text and merge documents.", skill.description)
        assertEquals("Apache-2.0", skill.license)
        assertEquals("Requires local filesystem access", skill.compatibility)
        assertEquals(mapOf("author" to "koog", "version" to "1.0"), skill.metadata)
        assertEquals("Bash(git:*) Read", skill.allowedTools)
        assertEquals(skillDirectory.resolve("SKILL.md").toAbsolutePath().normalize().toString(), skill.location)
    }

    @Test
    fun `test discoverSkills uses last found by default`() = runTest {
        val firstRoot = createTempDirectory()
        val secondRoot = createTempDirectory()

        firstRoot.resolve("shared").also { it.createDirectories() }.resolve("SKILL.md").writeText(
            """
            ---
            name: code-review
            description: First description.
            ---
            """.trimIndent()
        )

        secondRoot.resolve("shared").also { it.createDirectories() }.resolve("SKILL.md").writeText(
            """
            ---
            name: code-review
            description: Last description.
            ---
            """.trimIndent()
        )

        val discovered = discoverSkills(
            JVMFileSystemProvider.ReadOnly,
            directoriesToSearch = listOf(firstRoot.absolutePathString(), secondRoot.absolutePathString()),
        )

        assertEquals(1, discovered.size)
        assertEquals("Last description.", discovered.single().description)
    }

    @Test
    fun `test discoverSkills respects first found precedence`() = runTest {
        val firstRoot = createTempDirectory()
        val secondRoot = createTempDirectory()

        firstRoot.resolve("shared").also { it.createDirectories() }.resolve("SKILL.md").writeText(
            """
            ---
            name: code-review
            description: First description.
            ---
            """.trimIndent()
        )

        secondRoot.resolve("shared").also { it.createDirectories() }.resolve("SKILL.md").writeText(
            """
            ---
            name: code-review
            description: Last description.
            ---
            """.trimIndent()
        )

        val discovered = discoverSkills(
            JVMFileSystemProvider.ReadOnly,
            directoriesToSearch = listOf(firstRoot.absolutePathString(), secondRoot.absolutePathString()),
            precedenceRule = SkillCollisionPrecedence.FIRST_FOUND,
        )

        assertEquals(1, discovered.size)
        assertEquals("First description.", discovered.single().description)
    }

    @Test
    fun `test discoverSkills skips malformed or incomplete skill files`() = runTest {
        val root = createTempDirectory()

        root.resolve("invalid").also { it.createDirectories() }.resolve("SKILL.md").writeText(
            """
            ---
            name: invalid
            ---
            """.trimIndent()
        )

        val discovered = discoverSkills(JVMFileSystemProvider.ReadOnly, listOf(root.absolutePathString()))

        assertEquals(0, discovered.size)
    }

    @Test
    fun `test discoverSkills logs warnings for ignored directories collisions and malformed formats`() = runTest {
        val firstRoot = createTempDirectory()
        val secondRoot = createTempDirectory()
        val warnings = mutableListOf<String>()

        firstRoot.resolve("node_modules").resolve("ignored").also { it.createDirectories() }.resolve("SKILL.md").writeText(
            """
            ---
            name: ignored-skill
            description: Should be skipped.
            ---
            """.trimIndent()
        )

        firstRoot.resolve("valid-skill").also { it.createDirectories() }.resolve("SKILL.md").writeText(
            """
            ---
            name: shared
            description: First description.
            ---
            """.trimIndent()
        )

        secondRoot.resolve("another-valid").also { it.createDirectories() }.resolve("SKILL.md").writeText(
            """
            ---
            name: shared
            description: Second description.
            ---
            """.trimIndent()
        )

        secondRoot.resolve("broken").also { it.createDirectories() }.resolve("SKILL.md").writeText(
            """
            ---
            name: broken
            """.trimIndent()
        )

        secondRoot.resolve("wrong_name").also { it.createDirectories() }.resolve("SKILL.md").writeText(
            """
            ---
            name: Wrong_Name
            description: Invalid name format.
            ---
            """.trimIndent()
        )

        discoverSkills(
            JVMFileSystemProvider.ReadOnly,
            directoriesToSearch = listOf(firstRoot.absolutePathString(), secondRoot.absolutePathString()),
            warningLogger = warnings::add,
        )

        assertTrue(warnings.any { it.contains("ignored directory 'node_modules'") })
        assertTrue(warnings.any { it.contains("Skill name collision for 'shared'") })
        assertTrue(warnings.any { it.contains("has malformed frontmatter") })
        assertTrue(warnings.any { it.contains("does not match expected name format") })
    }

    @Test
    fun `test discoverSkills respects max depth`() = runTest {
        val root = createTempDirectory()
        val deepDirectory = root.resolve("a").resolve("b").resolve("c").resolve("d")
        deepDirectory.createDirectories()
        deepDirectory.resolve("SKILL.md").writeText(
            """
            ---
            name: deep-skill
            description: Should be discovered only with sufficient depth.
            ---
            """.trimIndent()
        )

        val discoveredWithLowDepth = discoverSkills(
            JVMFileSystemProvider.ReadOnly,
            directoriesToSearch = listOf(root.absolutePathString()),
            maxDepth = 2,
        )
        val discoveredWithEnoughDepth = discoverSkills(
            JVMFileSystemProvider.ReadOnly,
            directoriesToSearch = listOf(root.absolutePathString()),
            maxDepth = 4,
        )

        assertEquals(0, discoveredWithLowDepth.size)
        assertEquals(1, discoveredWithEnoughDepth.size)
        assertEquals("deep-skill", discoveredWithEnoughDepth.single().name)
    }

    @Test
    fun `test discoverSkills skips well known ignored directories`() = runTest {
        val root = createTempDirectory()

        root.resolve("node_modules").resolve("ignored").also { it.createDirectories() }.resolve("SKILL.md").writeText(
            """
            ---
            name: ignored-skill
            description: Should be skipped.
            ---
            """.trimIndent()
        )

        root.resolve(".git").resolve("ignored").also { it.createDirectories() }.resolve("SKILL.md").writeText(
            """
            ---
            name: also-ignored
            description: Should be skipped.
            ---
            """.trimIndent()
        )

        val discovered = discoverSkills(JVMFileSystemProvider.ReadOnly, listOf(root.absolutePathString()), maxDepth = 4)
        assertEquals(0, discovered.size)
    }

    @Test
    fun `test discoverSkills reads metadata only when provided`() = runTest {
        val root = createTempDirectory()
        val skillDirectory = root.resolve("skill").also { it.createDirectories() }
        skillDirectory.resolve("SKILL.md").writeText(
            """
            ---
            name: skill
            description: No metadata.
            ---
            """.trimIndent()
        )

        val discovered = discoverSkills(JVMFileSystemProvider.ReadOnly, listOf(root.absolutePathString()))

        assertEquals(1, discovered.size)
        assertNull(discovered.single().metadata)
    }

    @Test
    fun `test discoverSkills supports custom skill file name`() = runTest {
        val root = createTempDirectory()
        val skillDirectory = root.resolve("custom-file").also { it.createDirectories() }
        skillDirectory.resolve("CUSTOM_SKILL.md").writeText(
            """
            ---
            name: custom-file
            description: Uses non-default skill file name.
            ---
            """.trimIndent()
        )

        val discovered = discoverSkills(
            JVMFileSystemProvider.ReadOnly,
            directoriesToSearch = listOf(root.absolutePathString()),
            skillFileName = "CUSTOM_SKILL.md",
        )

        assertEquals(1, discovered.size)
        assertEquals("custom-file", discovered.single().name)
    }

    @Test
    fun `test discoverSkills supports custom skipped directory names`() = runTest {
        val root = createTempDirectory()

        root.resolve("vendor").resolve("hidden").also { it.createDirectories() }.resolve("SKILL.md").writeText(
            """
            ---
            name: skipped-by-custom-rule
            description: Should be skipped by custom directory set.
            ---
            """.trimIndent()
        )

        val discovered = discoverSkills(
            JVMFileSystemProvider.ReadOnly,
            directoriesToSearch = listOf(root.absolutePathString()),
            skippedDirectoryNames = setOf("vendor"),
        )

        assertEquals(0, discovered.size)
    }

    @Test
    fun `test discoverSkills supports custom skill name pattern`() = runTest {
        val root = createTempDirectory()
        val warnings = mutableListOf<String>()

        root.resolve("my_skill").also { it.createDirectories() }.resolve("SKILL.md").writeText(
            """
            ---
            name: my_skill
            description: Underscore-based name.
            ---
            """.trimIndent()
        )

        discoverSkills(
            JVMFileSystemProvider.ReadOnly,
            directoriesToSearch = listOf(root.absolutePathString()),
            skillNamePattern = Regex("^[a-z_]+$"),
            warningLogger = warnings::add,
        )

        assertTrue(warnings.none { it.contains("does not match expected name format") })
    }

    private fun createTempDirectory(): Path = Files.createTempDirectory("skills-discovery-test-")
}

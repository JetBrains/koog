package prompt

import ai.koog.skills.model.Skill
import ai.koog.skills.prompt.SkillsPromptFormat
import ai.koog.skills.prompt.generateSkillsPrompt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SkillsPromptTest {
    private val skills = listOf(
        Skill(
            name = "pdf-processing",
            description = "Extract PDF text, fill forms, merge files.",
            location = "/home/user/.agents/skills/pdf-processing/SKILL.md",
            license = "Apache-2.0",
            compatibility = "Requires local filesystem access",
            metadata = mapOf("author" to "koog", "version" to "1.0"),
            allowedTools = "Bash(git:*) Read",
        ),
        Skill(
            name = "data-analysis",
            description = "Analyze datasets and create summary reports.",
            location = "/home/user/project/.agents/skills/data-analysis/SKILL.md",
        ),
    )

    @Test
    fun testJsonEscapesAllControlCharacters() {
        for (code in 0..0x1f) {
            assertJsonRoundTrip("before${code.toChar()}after")
        }
    }

    @Test
    fun testJsonPreservesQuotesBackslashesAndUnicode() {
        assertJsonRoundTrip("Quoted \"text\" and \\ paths / café 日本語 😀")
    }

    private fun assertJsonRoundTrip(value: String) {
        val skill = Skill(
            name = value,
            description = value,
            location = value,
            license = value,
            compatibility = value,
            metadata = mapOf(value to value),
            allowedTools = value,
        )
        val prompt = generateSkillsPrompt(
            skills = listOf(skill),
            format = SkillsPromptFormat.JSON,
            includeLicense = true,
            includeCompatibility = true,
            includeMetadata = true,
            includeAllowedTools = true,
        )

        // Some JSON parsers accept raw control characters, so check strings explicitly.
        var insideString = false
        var escaped = false
        for (character in prompt) {
            assertFalse(insideString && character < ' ', "Raw control character U+${character.code.toString(16)}")
            when {
                escaped -> escaped = false
                insideString && character == '\\' -> escaped = true
                character == '"' -> insideString = !insideString
            }
        }

        val parsed = Json.parseToJsonElement(prompt).jsonObject.getValue("available_skills").jsonArray
        assertEquals(listOf(skill), parsed.map { Json.decodeFromJsonElement<Skill>(it) })
    }

    @Test
    fun `test generateSkillsPrompt generates xml prompt with location`() {
        val prompt = generateSkillsPrompt(skills, SkillsPromptFormat.XML)

        val expected =
            """
            <available_skills>
              <skill>
                <name>pdf-processing</name>
                <description>Extract PDF text, fill forms, merge files.</description>
                <location>/home/user/.agents/skills/pdf-processing/SKILL.md</location>
              </skill>
              <skill>
                <name>data-analysis</name>
                <description>Analyze datasets and create summary reports.</description>
                <location>/home/user/project/.agents/skills/data-analysis/SKILL.md</location>
              </skill>
            </available_skills>
            """.trimIndent()

        assertEquals(expected, prompt)
    }

    @Test
    fun `test generateSkillsPrompt generates json prompt without location`() {
        val prompt = generateSkillsPrompt(skills, SkillsPromptFormat.JSON, includeLocation = false)

        val expected =
            """
            {
              "available_skills": [
              {
                "name": "pdf-processing",
                "description": "Extract PDF text, fill forms, merge files."
              },
              {
                "name": "data-analysis",
                "description": "Analyze datasets and create summary reports."
              }
              ]
            }
            """.trimIndent()

        assertEquals(expected, prompt)
    }

    @Test
    fun `test generateSkillsPrompt generates yml prompt with location`() {
        val prompt = generateSkillsPrompt(skills, SkillsPromptFormat.YML)

        val expected =
            """
            available_skills:
              - name: "pdf-processing"
                description: "Extract PDF text, fill forms, merge files."
                location: "/home/user/.agents/skills/pdf-processing/SKILL.md"
              - name: "data-analysis"
                description: "Analyze datasets and create summary reports."
                location: "/home/user/project/.agents/skills/data-analysis/SKILL.md"
            """.trimIndent()

        assertEquals(expected, prompt)
    }

    @Test
    fun `test generateSkillsPrompt generates yml prompt without location`() {
        val prompt = generateSkillsPrompt(skills, SkillsPromptFormat.YML, includeLocation = false)

        val expected =
            """
            available_skills:
              - name: "pdf-processing"
                description: "Extract PDF text, fill forms, merge files."
              - name: "data-analysis"
                description: "Analyze datasets and create summary reports."
            """.trimIndent()

        assertEquals(expected, prompt)
    }

    @Test
    fun `test generateSkillsPrompt includes optional fields in xml`() {
        val prompt = generateSkillsPrompt(
            skills = skills,
            format = SkillsPromptFormat.XML,
            includeLocation = true,
            includeLicense = true,
            includeCompatibility = true,
            includeMetadata = true,
            includeAllowedTools = true,
        )

        val expected =
            """
            <available_skills>
              <skill>
                <name>pdf-processing</name>
                <description>Extract PDF text, fill forms, merge files.</description>
                <location>/home/user/.agents/skills/pdf-processing/SKILL.md</location>
                <license>Apache-2.0</license>
                <compatibility>Requires local filesystem access</compatibility>
                <metadata>
                  <entry key="author">koog</entry>
                  <entry key="version">1.0</entry>
                </metadata>
                <allowed-tools>Bash(git:*) Read</allowed-tools>
              </skill>
              <skill>
                <name>data-analysis</name>
                <description>Analyze datasets and create summary reports.</description>
                <location>/home/user/project/.agents/skills/data-analysis/SKILL.md</location>
              </skill>
            </available_skills>
            """.trimIndent()

        assertEquals(expected, prompt)
    }

    @Test
    fun `test generateSkillsPrompt includes optional fields in json`() {
        val prompt = generateSkillsPrompt(
            skills = skills,
            format = SkillsPromptFormat.JSON,
            includeLocation = true,
            includeLicense = true,
            includeCompatibility = true,
            includeMetadata = true,
            includeAllowedTools = true,
        )

        val expected =
            """
            {
              "available_skills": [
              {
                "name": "pdf-processing",
                "description": "Extract PDF text, fill forms, merge files."
                ,"location": "/home/user/.agents/skills/pdf-processing/SKILL.md"
                ,"license": "Apache-2.0"
                ,"compatibility": "Requires local filesystem access"
                ,"metadata": {
                  "author": "koog",
                  "version": "1.0"
                }
                ,"allowed-tools": "Bash(git:*) Read"
              },
              {
                "name": "data-analysis",
                "description": "Analyze datasets and create summary reports."
                ,"location": "/home/user/project/.agents/skills/data-analysis/SKILL.md"
              }
              ]
            }
            """.trimIndent()

        assertEquals(expected, prompt)
    }

    @Test
    fun `test generateSkillsPrompt includes optional fields in yml`() {
        val prompt = generateSkillsPrompt(
            skills = skills,
            format = SkillsPromptFormat.YML,
            includeLocation = true,
            includeLicense = true,
            includeCompatibility = true,
            includeMetadata = true,
            includeAllowedTools = true,
        )

        val expected =
            """
            available_skills:
              - name: "pdf-processing"
                description: "Extract PDF text, fill forms, merge files."
                location: "/home/user/.agents/skills/pdf-processing/SKILL.md"
                license: "Apache-2.0"
                compatibility: "Requires local filesystem access"
                metadata:
                  author: "koog"
                  version: "1.0"
                allowed-tools: "Bash(git:*) Read"
              - name: "data-analysis"
                description: "Analyze datasets and create summary reports."
                location: "/home/user/project/.agents/skills/data-analysis/SKILL.md"
            """.trimIndent()

        assertEquals(expected, prompt)
    }
}

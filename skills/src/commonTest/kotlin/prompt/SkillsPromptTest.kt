package prompt

import ai.koog.skills.model.Skill
import ai.koog.skills.prompt.SkillsPromptFormat
import ai.koog.skills.prompt.generateSkillsPrompt
import kotlin.test.Test
import kotlin.test.assertEquals

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

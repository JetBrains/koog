package prompt

import ai.koog.skills.model.Skill
import ai.koog.skills.prompt.SkillsPromptFormat
import ai.koog.skills.prompt.generateSkillsPrompt
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.schema.CoreSchema
import kotlin.test.assertEquals

class SkillsPromptYamlTest {
    @ParameterizedTest
    @MethodSource("strings")
    fun testAllFieldsRoundTrip(value: String) {
        val skill = Skill(value, value, value, value, value, mapOf("author" to value), value)
        val yaml = generateSkillsPrompt(listOf(skill), SkillsPromptFormat.YML, true, true, true, true, true)
        assertEquals(
            mapOf(
                "name" to value,
                "description" to value,
                "location" to value,
                "license" to value,
                "compatibility" to value,
                "metadata" to mapOf("author" to value),
                "allowed-tools" to value,
            ),
            parseSkill(yaml),
        )
    }

    @ParameterizedTest
    @MethodSource("strings")
    fun testMetadataKeyRoundTrip(key: String) {
        val skill = Skill("example", "Example", "SKILL.md", metadata = mapOf(key to "value"))
        val yaml = generateSkillsPrompt(listOf(skill), SkillsPromptFormat.YML, includeMetadata = true)
        assertEquals(mapOf(key to "value"), parseSkill(yaml)["metadata"])
    }

    private fun parseSkill(yaml: String): Map<*, *> {
        val document = Load(LoadSettings.builder().setSchema(CoreSchema()).build()).loadFromString(yaml) as Map<*, *>
        return (document["available_skills"] as List<*>).single() as Map<*, *>
    }

    companion object {
        @JvmStatic
        fun strings(): List<String> = listOf(
            "author", "version", "", "true", "True", "TRUE", "false", "False", "FALSE",
            "null", "Null", "NULL", "~", "yes", "no", "on", "off", "y", "n",
            "123", "01", "1.5", ".inf", ".NaN", "a: b", "a:b", "#comment", "[a]", "{a}",
            "*alias", "&anchor", "? key", "- item", " leading", "trailing ", "a\\b", "a\"b",
            "first\nsecond", "first\rsecond", "first\r\nsecond", "\n", "\t", "\u0000", "\u0001", "\u001f",
            "\u007f", "\u0085", "\u009f", "\u2028", "\u2029", "café 日本語 🐨",
        )
    }
}

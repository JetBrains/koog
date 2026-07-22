package ai.koog.skills.prompt

import ai.koog.skills.model.Skill

/**
 * Supported output formats for a generated skills prompt.
 */
public enum class SkillsPromptFormat {
    /** XML representation with the `available_skills` root element. */
    XML,

    /** JSON representation with the `available_skills` root property. */
    JSON,

    /** YAML representation with the `available_skills` root key. */
    YML,
}

/**
 * Generates a textual prompt that describes available skills in the requested [format].
 *
 * The output always includes each skill's `name` and `description`. Additional properties can be
 * included via dedicated `include*` flags.
 *
 * @param skills Skills to include in the prompt.
 * @param format Output format for the generated prompt.
 * @param includeLocation Whether to include the `location` property.
 * @param includeLicense Whether to include the `license` property when present.
 * @param includeCompatibility Whether to include the `compatibility` property when present.
 * @param includeMetadata Whether to include the `metadata` property when present and non-empty.
 * @param includeAllowedTools Whether to include the `allowed-tools` property when present.
 * @return Generated prompt string in the selected [format].
 */
public fun generateSkillsPrompt(
    skills: List<Skill>,
    format: SkillsPromptFormat,
    includeLocation: Boolean = true,
    includeLicense: Boolean = false,
    includeCompatibility: Boolean = false,
    includeMetadata: Boolean = false,
    includeAllowedTools: Boolean = false,
): String = when (format) {
    SkillsPromptFormat.XML -> generateXmlSkillsPrompt(
        skills = skills,
        includeLocation = includeLocation,
        includeLicense = includeLicense,
        includeCompatibility = includeCompatibility,
        includeMetadata = includeMetadata,
        includeAllowedTools = includeAllowedTools,
    )
    SkillsPromptFormat.JSON -> generateJsonSkillsPrompt(
        skills = skills,
        includeLocation = includeLocation,
        includeLicense = includeLicense,
        includeCompatibility = includeCompatibility,
        includeMetadata = includeMetadata,
        includeAllowedTools = includeAllowedTools,
    )
    SkillsPromptFormat.YML -> generateYmlSkillsPrompt(
        skills = skills,
        includeLocation = includeLocation,
        includeLicense = includeLicense,
        includeCompatibility = includeCompatibility,
        includeMetadata = includeMetadata,
        includeAllowedTools = includeAllowedTools,
    )
}

private const val JSON_INDENTATION = 4

private fun generateXmlSkillsPrompt(
    skills: List<Skill>,
    includeLocation: Boolean,
    includeLicense: Boolean,
    includeCompatibility: Boolean,
    includeMetadata: Boolean,
    includeAllowedTools: Boolean,
): String {
    val skillsContent = skills.joinToString(separator = "\n") { skill ->
        buildString {
            appendLine("  <skill>")
            appendLine("    <name>${escapeXml(skill.name)}</name>")
            appendLine("    <description>${escapeXml(skill.description)}</description>")
            if (includeLocation) {
                appendLine("    <location>${escapeXml(skill.location)}</location>")
            }
            if (includeLicense && skill.license != null) {
                appendLine("    <license>${escapeXml(skill.license)}</license>")
            }
            if (includeCompatibility && skill.compatibility != null) {
                appendLine("    <compatibility>${escapeXml(skill.compatibility)}</compatibility>")
            }
            if (includeMetadata && !skill.metadata.isNullOrEmpty()) {
                appendLine("    <metadata>")
                skill.metadata.forEach { (key, value) ->
                    appendLine("      <entry key=\"${escapeXml(key)}\">${escapeXml(value)}</entry>")
                }
                appendLine("    </metadata>")
            }
            if (includeAllowedTools && skill.allowedTools != null) {
                appendLine("    <allowed-tools>${escapeXml(skill.allowedTools)}</allowed-tools>")
            }
            append("  </skill>")
        }
    }

    return buildString {
        appendLine("<available_skills>")
        if (skillsContent.isNotEmpty()) {
            appendLine(skillsContent)
        }
        append("</available_skills>")
    }
}

private fun generateJsonSkillsPrompt(
    skills: List<Skill>,
    includeLocation: Boolean,
    includeLicense: Boolean,
    includeCompatibility: Boolean,
    includeMetadata: Boolean,
    includeAllowedTools: Boolean,
): String {
    val skillsJson = skills.joinToString(separator = ",\n") { skill ->
        buildString {
            appendLine("  {")
            appendLine("    \"name\": \"${escapeJson(skill.name)}\",")
            appendLine("    \"description\": \"${escapeJson(skill.description)}\"")
            if (includeLocation) {
                appendLine("    ,\"location\": \"${escapeJson(skill.location)}\"")
            }
            if (includeLicense && skill.license != null) {
                appendLine("    ,\"license\": \"${escapeJson(skill.license)}\"")
            }
            if (includeCompatibility && skill.compatibility != null) {
                appendLine("    ,\"compatibility\": \"${escapeJson(skill.compatibility)}\"")
            }
            if (includeMetadata && !skill.metadata.isNullOrEmpty()) {
                appendLine("    ,\"metadata\": ${toJsonObject(skill.metadata)}")
            }
            if (includeAllowedTools && skill.allowedTools != null) {
                appendLine("    ,\"allowed-tools\": \"${escapeJson(skill.allowedTools)}\"")
            }
            append("  }")
        }
    }

    return buildString {
        appendLine("{")
        appendLine("  \"available_skills\": [")
        if (skillsJson.isNotEmpty()) {
            appendLine(skillsJson)
        }
        appendLine("  ]")
        append("}")
    }
}

private fun generateYmlSkillsPrompt(
    skills: List<Skill>,
    includeLocation: Boolean,
    includeLicense: Boolean,
    includeCompatibility: Boolean,
    includeMetadata: Boolean,
    includeAllowedTools: Boolean,
): String = buildString {
    appendLine("available_skills:")
    if (skills.isEmpty()) {
        append("  []")
        return@buildString
    }

    skills.forEach { skill ->
        appendLine("  - name: ${escapeYml(skill.name)}")
        appendLine("    description: ${escapeYml(skill.description)}")
        if (includeLocation) {
            appendLine("    location: ${escapeYml(skill.location)}")
        }
        if (includeLicense && skill.license != null) {
            appendLine("    license: ${escapeYml(skill.license)}")
        }
        if (includeCompatibility && skill.compatibility != null) {
            appendLine("    compatibility: ${escapeYml(skill.compatibility)}")
        }
        if (includeMetadata && !skill.metadata.isNullOrEmpty()) {
            appendLine("    metadata:")
            skill.metadata.forEach { (key, value) ->
                appendLine("      ${escapeYmlKey(key)}: ${escapeYml(value)}")
            }
        }
        if (includeAllowedTools && skill.allowedTools != null) {
            appendLine("    allowed-tools: ${escapeYml(skill.allowedTools)}")
        }
    }
}.trimEnd()

private fun toJsonObject(values: Map<String, String>): String {
    val indent = " ".repeat(JSON_INDENTATION)
    val nestedIndent = " ".repeat(JSON_INDENTATION + 2)
    return buildString {
        appendLine("{")
        append(
            values.entries.joinToString(",\n") { (key, value) ->
                "${nestedIndent}\"${escapeJson(key)}\": \"${escapeJson(value)}\""
            }
        )
        appendLine()
        append("$indent}")
    }
}

private fun escapeXml(value: String): String =
    value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

private fun escapeJson(value: String): String = buildString {
    value.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
}

private fun escapeYml(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

private fun escapeYmlKey(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace(":", "\\:")

package ai.grazie.code.agents.tools.registry.tools.composio

import ai.grazie.code.agents.core.tools.serialization.ToolResultStringSerializer
import ai.grazie.code.agents.tools.registry.utils.formatLinesWithNumbers
import kotlinx.serialization.Serializable

@Serializable
enum class Language(val nameForCodeSnippets: String) {
    PYTHON("python"),
    KOTLIN("kotlin"),
    JAVA("java"),
    JAVASCRIPT("js"),
    CPP("cpp"),
    CSHARP("cs")
    // TODO: add more here
}

@Serializable
data class VariableMetaInfo(
    val name: String,
    val containingClass: String
) {
    internal object Serializer : ToolResultStringSerializer<VariableMetaInfo>(
        toStringConverter = { value ->
            "${value.name} | defined in class `${value.containingClass}`"
        }
    )
}

@Serializable
data class MethodMetaInfo(
    /**
     * Full text of the "signature" part of the PSI of the method (with all technical elements, including literals, keywords and annotations)
     *
     * Example: def add(self, a, b):
     * */
    val signatureText: String,
    /**
     * Simple name of the containing class
     * */
    val containingClassName: String
) {
     internal object Serializer : ToolResultStringSerializer<MethodMetaInfo>(
        toStringConverter = { value ->
            "Signature: ${value.signatureText} | Member of `${value.containingClassName}` class "
        }
    )
}

@Serializable
data class FullMethodData(
    val metaInfo: MethodMetaInfo,
    val language: Language,
    val initialLineNumber: Int,
    val bodyText: String
) {
    internal object Serializer : ToolResultStringSerializer<FullMethodData>(
        toStringConverter = { value ->
            val bodyLines = formatLinesWithNumbers(value.bodyText.lines(), value.initialLineNumber)

            """
                ${value.metaInfo}
                ```${value.language.nameForCodeSnippets}
                $bodyLines
                ```
            """.trimIndent()
        }
    )
}


@Serializable
data class ClassMetaInfo(
    /**
     * The whole text (piece of code) containing the class signature and all sub-elements of the corresponding PSI part (with all technical elements, including literals, keywords and annotations)
     */
    val signatureText: String,
    val containingFilePath: String,
    val fqName: String,
    val methods: List<MethodMetaInfo>,
    /**
     * Example:
     * ```
     * @JvmStatic
     * val x: Int
     * ```
     *
     * or
     *
     * ```
     * companion object {
     *   val x: Int
     * }
     * ```
     *
     * */
    val staticClassFields: List<VariableMetaInfo>,
    /**
     * Example:
     * ```
     * class C(val x: Int)
     * ```
     * */
    val fields: List<VariableMetaInfo>,
    /**
     * Example:
     * ```
     * class C {
     *    fun getX() {}
     *    fun setX() {}
     * }
     * ```
     * */
    val properties: List<VariableMetaInfo>,
) {
    internal object Serializer : ToolResultStringSerializer<ClassMetaInfo>(
        toStringConverter = { value ->
            val methodsText = ComposioJson.encodeToString(ComposioListSerializer(MethodMetaInfo.Serializer), value.methods)
            val classVariablesText = ComposioJson.encodeToString(ComposioListSerializer(VariableMetaInfo.Serializer), value.staticClassFields)
            val instanceVariablesText = ComposioJson.encodeToString(ComposioListSerializer(VariableMetaInfo.Serializer), value.fields)
            val propertiesText = ComposioJson.encodeToString(ComposioListSerializer(VariableMetaInfo.Serializer), value.properties)

            """
                Class signature: ${value.signatureText}
                File where defined: ${value.containingFilePath}
                Class full name: ${value.fqName}
                Functions accessible:
                $methodsText
                Class variables accessible:
                $classVariablesText
                Instance variables accessible:
                $instanceVariablesText
                Properties accessible:
                $propertiesText
            """.trimIndent()
        }
    )
}

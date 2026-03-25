package ai.koog.prompt.executor.clients.google.genai

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.prompt.params.LLMParams
import com.google.genai.types.FunctionCallingConfig
import com.google.genai.types.FunctionCallingConfigMode
import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.Tool
import com.google.genai.types.ToolConfig

/**
 * Converts Koog [ToolDescriptor] instances and [LLMParams.ToolChoice] values
 * to the corresponding Google GenAI SDK types.
 */
internal object GoogleGenaiToolConverter {

    /**
     * Converts [ToolDescriptor] list to SDK [Tool.Builder] list.
     * Returns builders so callers can further modify them
     * (e.g. add google search, code execution) before `.build()`.
     */
    fun buildSdkTools(tools: List<ToolDescriptor>): List<Tool.Builder>? {
        if (tools.isEmpty()) return null

        val declarations = tools.map { tool ->
            val properties = (tool.requiredParameters + tool.optionalParameters)
                .associate { it.name to buildParamSchema(it) }

            val schema = mapOf(
                "type" to "object",
                "properties" to properties,
                "required" to tool.requiredParameters.map { it.name }
            )

            FunctionDeclaration.builder()
                .name(tool.name)
                .description(tool.description)
                .parametersJsonSchema(schema)
                .build()
        }

        return listOf(Tool.builder().functionDeclarations(declarations))
    }

    private fun buildParamSchema(param: ToolParameterDescriptor): Map<String, Any?> {
        val schema = mutableMapOf<String, Any?>("description" to param.description)
        putTypeSchema(schema, param.type)
        return schema
    }

    private fun putTypeSchema(schema: MutableMap<String, Any?>, type: ToolParameterType) {
        when (type) {
            ToolParameterType.Boolean -> schema["type"] = "boolean"

            ToolParameterType.Float -> schema["type"] = "number"

            ToolParameterType.Integer -> schema["type"] = "integer"

            ToolParameterType.String -> schema["type"] = "string"

            ToolParameterType.Null -> schema["type"] = "null"

            is ToolParameterType.Enum -> {
                schema["type"] = "string"
                schema["enum"] = type.entries.toList()
            }

            is ToolParameterType.List -> {
                schema["type"] = "array"
                val itemSchema = mutableMapOf<String, Any?>()
                putTypeSchema(itemSchema, type.itemsType)
                schema["items"] = itemSchema
            }

            is ToolParameterType.AnyOf -> {
                schema["anyOf"] = type.types.map { buildParamSchema(it) }
            }

            is ToolParameterType.Object -> {
                schema["type"] = "object"
                schema["properties"] = type.properties.associate { prop ->
                    val propSchema = mutableMapOf<String, Any?>("description" to prop.description)
                    putTypeSchema(propSchema, prop.type)
                    prop.name to propSchema
                }
            }
        }
    }

    /**
     * Converts [LLMParams.ToolChoice] to SDK [ToolConfig].
     */
    fun buildSdkToolConfig(toolChoice: LLMParams.ToolChoice?): ToolConfig? {
        val fcConfig = when (toolChoice) {
            LLMParams.ToolChoice.Auto -> FunctionCallingConfig.builder()
                .mode(FunctionCallingConfigMode.Known.AUTO)
                .build()

            LLMParams.ToolChoice.None -> FunctionCallingConfig.builder()
                .mode(FunctionCallingConfigMode.Known.NONE)
                .build()

            LLMParams.ToolChoice.Required -> FunctionCallingConfig.builder()
                .mode(FunctionCallingConfigMode.Known.ANY)
                .build()

            is LLMParams.ToolChoice.Named -> FunctionCallingConfig.builder()
                .mode(FunctionCallingConfigMode.Known.ANY)
                .allowedFunctionNames(listOf(toolChoice.name))
                .build()

            null -> return null
        }
        return ToolConfig.builder().functionCallingConfig(fcConfig).build()
    }
}

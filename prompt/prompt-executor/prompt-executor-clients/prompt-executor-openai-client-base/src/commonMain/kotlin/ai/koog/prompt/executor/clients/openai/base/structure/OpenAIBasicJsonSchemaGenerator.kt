package ai.koog.prompt.executor.clients.openai.base.structure

import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.JsonSchemaConsts
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Extends [BasicJsonSchemaGenerator] to generate [LLMParams.Schema.JSON.Basic] in custom OpenAI format.
 */
public open class OpenAIBasicJsonSchemaGenerator : BasicJsonSchemaGenerator() {
    /**
     * Default instance of [OpenAIBasicJsonSchemaGenerator].
     * Prefer to use it instead of creating new instates manually.
     *
     * @see [OpenAIBasicJsonSchemaGenerator]
     */
    public companion object Default : OpenAIBasicJsonSchemaGenerator()

    override fun processMap(context: GenerationContext): JsonObject {
        throw UnsupportedOperationException("OpenAI JSON schema doesn't support maps")
    }

    override fun processObject(context: GenerationContext): JsonObject {
        val schema = super.processObject(context).toMutableMap()

        // OpenAI requires all existing properties to be present in "required" list
        schema[JsonSchemaConsts.Keys.REQUIRED] = JsonArray(
            schema.getValue(JsonSchemaConsts.Keys.PROPERTIES).jsonObject
                .keys
                .map { JsonPrimitive(it) }
        )

        context.processedTypeDefs[context.descriptor] = JsonObject(schema)

        return JsonObject(schema)
    }
}

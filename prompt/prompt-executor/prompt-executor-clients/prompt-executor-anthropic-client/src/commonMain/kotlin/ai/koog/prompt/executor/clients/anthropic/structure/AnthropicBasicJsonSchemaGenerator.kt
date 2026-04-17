package ai.koog.prompt.executor.clients.anthropic.structure

import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.JsonSchemaConsts
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Extends [BasicJsonSchemaGenerator] to generate [LLMParams.Schema.JSON.Basic] in custom Anthropic format.
 */
public open class AnthropicBasicJsonSchemaGenerator : BasicJsonSchemaGenerator() {

    /**
     * Default instance of [AnthropicBasicJsonSchemaGenerator].
     * Prefer to use it instead of creating new instances manually.
     *
     * @see [AnthropicBasicJsonSchemaGenerator]
     */
    public companion object : AnthropicBasicJsonSchemaGenerator()

    override fun processMap(context: GenerationContext): JsonObject {
        throw UnsupportedOperationException("Anthropic JSON schema doesn't support maps")
    }

    override fun processObject(context: GenerationContext): JsonObject {
        val schema = super.processObject(context).toMutableMap()

        // Anthropic requires all existing properties to be present in "required" list
        schema[JsonSchemaConsts.Keys.REQUIRED] = JsonArray(
            schema.getValue(JsonSchemaConsts.Keys.PROPERTIES).jsonObject
                .keys
                .map { JsonPrimitive(it) }
        )

        context.processedTypeDefs[context.descriptor] = JsonObject(schema)

        return JsonObject(schema)
    }
}

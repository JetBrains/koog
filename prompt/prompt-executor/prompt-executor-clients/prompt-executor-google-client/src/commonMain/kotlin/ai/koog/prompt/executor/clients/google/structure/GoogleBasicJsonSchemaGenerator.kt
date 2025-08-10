package ai.koog.prompt.executor.clients.google.structure

import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.structure.json.generator.BasicJsonSchemaGenerator
import ai.koog.prompt.structure.json.generator.JsonSchemaConsts
import kotlinx.serialization.json.JsonObject

/**
 * Extends [BasicJsonSchemaGenerator] to generate [LLMParams.Schema.JSON.Basic] in custom Google format.
 */
public open class GoogleBasicJsonSchemaGenerator : BasicJsonSchemaGenerator() {

    /**
     * Default instance of [GoogleBasicJsonSchemaGenerator].
     * Prefer to use it instead of creating new instates manually.
     *
     * @see [GoogleBasicJsonSchemaGenerator]
     */
    public companion object : GoogleBasicJsonSchemaGenerator()

    override fun processObject(context: GenerationContext): JsonObject {
        val schema = super.processObject(context).toMutableMap()

        // Google does not support "additionalProperties" in simple schema.
        schema.remove(JsonSchemaConsts.Keys.ADDITIONAL_PROPERTIES)

        return JsonObject(schema)
    }
}

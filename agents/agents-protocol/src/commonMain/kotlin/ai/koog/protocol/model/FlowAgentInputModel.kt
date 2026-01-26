package ai.koog.protocol.model

import ai.koog.protocol.agent.FlowAgentInput
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 *
 */
internal fun JsonElement.toFlowAgentInput(): FlowAgentInput? {
    return when (this) {
        is JsonPrimitive -> {
            this.toFlowAgentInput()
        }

        is JsonArray -> {
            if (isEmpty()) {
                error("Unsupported empty array input")
            }

            this.toInputArray()
        }

        is JsonObject -> {
            this.toInputObject()
        }

        JsonNull -> null
    }
}

internal fun JsonPrimitive.toFlowAgentInput(): FlowAgentInput {
    return booleanOrNull?.let { data -> FlowAgentInput.InputBoolean(data) }
        ?: intOrNull?.let { data -> FlowAgentInput.InputInt(data) }
        ?: doubleOrNull?.let { data -> FlowAgentInput.InputDouble(data) }
        ?: contentOrNull?.let { data -> FlowAgentInput.InputString(data) }
        ?: error("Unsupported primitive type: $this")
}

internal fun JsonArray.toInputArray(): FlowAgentInput {
    return when {
        all { it.jsonPrimitive.isString } -> {
            FlowAgentInput.InputArrayStrings(mapNotNull { it.jsonPrimitive.contentOrNull }.toTypedArray())
        }
        all { it.jsonPrimitive.booleanOrNull != null } -> {
            FlowAgentInput.InputArrayBooleans(mapNotNull { it.jsonPrimitive.booleanOrNull }.toTypedArray())
        }
        all { it.jsonPrimitive.intOrNull != null } -> {
            FlowAgentInput.InputArrayInt(mapNotNull { it.jsonPrimitive.intOrNull }.toTypedArray())
        }
        all { it.jsonPrimitive.doubleOrNull != null } -> {
            FlowAgentInput.InputArrayDouble(mapNotNull { it.jsonPrimitive.doubleOrNull }.toTypedArray())
        }
        else -> {
            error("Expected an array of uniform primitive types, but got: <$this>")
        }
    }
}

internal fun JsonObject.toInputObject(): FlowAgentInput {
    return this.toInputCritiqueResult()
        ?: error("Unable to create Flow Agent Input type from Json input: $this")
}

internal fun JsonObject.toInputCritiqueResult(): FlowAgentInput.InputCritiqueResult? {
    val success = this["success"]?.jsonPrimitive?.booleanOrNull ?: return null
    val feedback = this["feedback"]?.jsonPrimitive?.contentOrNull ?: return null
    val input = this["input"]?.toFlowAgentInput() ?: return null

    return FlowAgentInput.InputCritiqueResult(success, feedback, input)
}

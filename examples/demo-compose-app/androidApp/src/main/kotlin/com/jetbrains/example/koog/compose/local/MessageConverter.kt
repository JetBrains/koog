package com.jetbrains.example.koog.compose.local

import ai.koog.prompt.message.Message
import com.google.ai.edge.localagents.core.proto.Content
import com.google.ai.edge.localagents.core.proto.FunctionCall
import com.google.ai.edge.localagents.core.proto.FunctionResponse
import com.google.ai.edge.localagents.core.proto.Part
import com.google.protobuf.NullValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

object MessageConverter {

    fun convertToProto(message: Message): Content {
        return when (message) {
            is Message.Tool.Call -> convertToolCallToProto(message)
            is Message.Tool.Result -> convertToolResultToProto(message)
            else -> Content.newBuilder()
                .setRole(message.role.name.lowercase())
                .addParts(Part.newBuilder().setText(message.content))
                .build()
        }
    }


    private fun convertToolCallToProto(message: Message.Tool.Call): Content {
        return Content.newBuilder()
            .setRole(message.role.name.lowercase())
            .addParts(
                Part.newBuilder().setFunctionCall(
                    FunctionCall.newBuilder()
                        .setName(message.tool)
                        .setArgs(convertArgsToProto(message.contentJson))
                        .build()
                )
            ).build()
    }

    private fun convertToolResultToProto(message: Message.Tool.Result): Content {
        return Content.newBuilder()
            .setRole(message.role.name.lowercase())
            .addParts(
                Part.newBuilder().setFunctionResponse(
                    FunctionResponse.newBuilder()
                        .setName(message.tool)
                        .setResponse(
                            Struct.newBuilder().putAllFields(
                                mapOf(
                                    "result" to Value.newBuilder()
                                        .setStringValue(message.content).build()
                                )
                            )
                        )
                        .build()
                )
            ).build()
    }

    private fun convertArgsToProto(jsonObject: JsonObject): Struct {
        val structBuilder = Struct.newBuilder()
        jsonObject.forEach { (key, value) ->
            structBuilder.putFields(key, convertArgValueToProto(value))
        }
        return structBuilder.build()
    }

    private fun convertArgValueToProto(jsonElement: JsonElement): Value {
        return when (jsonElement) {
            is JsonNull -> Value.newBuilder().setNullValue(NullValue.NULL_VALUE)
                .build()

            is JsonPrimitive -> {
                when {
                    jsonElement.isString -> Value.newBuilder()
                        .setStringValue(jsonElement.content).build()

                    jsonElement.booleanOrNull != null -> Value.newBuilder()
                        .setBoolValue(jsonElement.boolean).build()

                    jsonElement.doubleOrNull != null -> Value.newBuilder()
                        .setNumberValue(jsonElement.double).build()

                    // TODO(Support other types)
                    else -> Value.newBuilder().setStringValue(jsonElement.content).build()
                }
            }

            is JsonObject -> {
                val structBuilder = Struct.newBuilder()
                jsonElement.forEach { (key, value) ->
                    structBuilder.putFields(key, convertArgValueToProto(value))
                }
                Value.newBuilder().setStructValue(structBuilder.build()).build()
            }

            is JsonArray -> {
                val listBuilder = com.google.protobuf.ListValue.newBuilder()
                jsonElement.forEach { element ->
                    listBuilder.addValues(convertArgValueToProto(element))
                }
                Value.newBuilder().setListValue(listBuilder.build()).build()
            }
        }
    }
}

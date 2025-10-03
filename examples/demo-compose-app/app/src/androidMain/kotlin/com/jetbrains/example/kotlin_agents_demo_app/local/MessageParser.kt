package com.jetbrains.example.kotlin_agents_demo_app.local

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import com.google.ai.edge.localagents.core.proto.Candidate
import com.google.protobuf.Struct
import com.google.protobuf.Value
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

class MessageParser(val clock: Clock) {

    fun parseMessage(candidate: Candidate): Message.Response {
        val parts = candidate.content.partsList
        if (parts.any { it.hasFunctionCall() }) {
            val functionCallPart = parts.first { it.hasFunctionCall() }
            val functionCall = functionCallPart.functionCall

            return Message.Tool.Call(
                id = null,
                tool = functionCall.name,
                content = Json.encodeToString(parseArgsToJsonObject(functionCall.args)),
                metaInfo = ResponseMetaInfo(clock.now())
            )
        }

        return Message.Assistant(
            content = candidate.content.partsList.firstOrNull()?.text
                ?: error("Empty LLM response"),
            metaInfo = ResponseMetaInfo(clock.now())
        )
    }

    private fun parseArgsToJsonObject(argsStruct: Struct): JsonObject {
        return buildJsonObject {
            argsStruct.fieldsMap.forEach { (key, value) ->
                put(key, parseValueToJsonElement(value))
            }
        }
    }


    private fun parseValueToJsonElement(value: Value): JsonElement {
        return when (value.kindCase) {
            Value.KindCase.NULL_VALUE -> JsonNull
            Value.KindCase.NUMBER_VALUE -> JsonPrimitive(value.numberValue)
            Value.KindCase.STRING_VALUE -> JsonPrimitive(value.stringValue)
            Value.KindCase.BOOL_VALUE -> JsonPrimitive(value.boolValue)
            Value.KindCase.STRUCT_VALUE -> {
                buildJsonObject {
                    value.structValue.fieldsMap.forEach { (key, nestedValue) ->
                        put(key, parseValueToJsonElement(nestedValue))
                    }
                }
            }

            Value.KindCase.LIST_VALUE -> {
                buildJsonArray {
                    value.listValue.valuesList.forEach { listValue ->
                        add(parseValueToJsonElement(listValue))
                    }
                }
            }

            else -> error("Unknown value kind")
        }
    }

}

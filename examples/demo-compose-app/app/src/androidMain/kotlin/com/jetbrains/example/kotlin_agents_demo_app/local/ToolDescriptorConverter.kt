package com.jetbrains.example.kotlin_agents_demo_app.local

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import com.google.ai.edge.localagents.core.proto.FunctionDeclaration
import com.google.ai.edge.localagents.core.proto.Schema
import com.google.ai.edge.localagents.core.proto.Tool
import com.google.ai.edge.localagents.core.proto.Type

object ToolDescriptorConverter {

    fun convertToProto(toolDescriptor: ToolDescriptor): Tool {
        return Tool.newBuilder().addFunctionDeclarations(
            FunctionDeclaration.newBuilder()
                .setName(toolDescriptor.name)
                .setDescription(toolDescriptor.description)
                .setParameters(convertParametersToProto(toolDescriptor))
                .setResponse(
                    Schema.newBuilder().addAllRequired(listOf("result")).setType(Type.STRING)
                        .build()
                )
        ).build()
    }

    private fun convertParametersToProto(toolDescriptor: ToolDescriptor): Schema {
        val allParameters = toolDescriptor.requiredParameters + toolDescriptor.optionalParameters
        val requiredNames = toolDescriptor.requiredParameters.map { it.name }

        val schemaBuilder = Schema.newBuilder().setType(Type.OBJECT)
        if (allParameters.isNotEmpty()) {
            allParameters.forEach { param ->
                schemaBuilder.putProperties(param.name, convertParameterDescriptorToProto(param))
            }

            if (requiredNames.isNotEmpty()) {
                schemaBuilder.addAllRequired(requiredNames)
            }
        }

        return schemaBuilder.build()
    }

    // https://github.com/google-ai-edge/ai-edge-apis/blob/cc5de1ad2f1189eb598e25a25eba8d6d06e2082a/local_agents/function_calling/tool_simulation/demo/Demo_ToolSimulation_Data_and_Eval_Example.ipynb
    private fun convertParameterDescriptorToProto(parameter: ToolParameterDescriptor): Schema {
        val schemaBuilder = Schema.newBuilder()
        when (val parameterType = parameter.type) {
            is ToolParameterType.String -> {
                schemaBuilder.type = Type.STRING
            }

            is ToolParameterType.Integer -> {
                schemaBuilder.type = Type.INTEGER
            }

            is ToolParameterType.Float -> {
                schemaBuilder.type = Type.NUMBER
            }

            is ToolParameterType.Boolean -> {
                schemaBuilder.type = Type.BOOLEAN
            }

            is ToolParameterType.Enum -> {
                schemaBuilder.type = Type.STRING
                schemaBuilder.addAllEnum(parameterType.entries.toList())
            }

            is ToolParameterType.List -> {
                schemaBuilder.type = Type.ARRAY
                schemaBuilder.items = convertParameterDescriptorToProto(
                    ToolParameterDescriptor("", "", parameterType.itemsType)
                )
            }

            is ToolParameterType.Object -> {
                schemaBuilder.type = Type.OBJECT
                parameterType.properties.forEach { prop ->
                    schemaBuilder.putProperties(prop.name, convertParameterDescriptorToProto(prop))
                }
                if (parameterType.requiredProperties.isNotEmpty()) {
                    schemaBuilder.addAllRequired(parameterType.requiredProperties)
                }
            }
        }
        if (parameter.description.isNotEmpty()) {
            schemaBuilder.description = parameter.description
        }

        return schemaBuilder.build()
    }
}

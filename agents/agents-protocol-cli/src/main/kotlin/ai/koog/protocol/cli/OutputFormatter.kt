package ai.koog.protocol.cli

import ai.koog.protocol.agent.FlowDataType

internal object OutputFormatter {
    fun format(result: FlowDataType): String = when (result) {
        is FlowDataType.FlowString -> result.data
        is FlowDataType.FlowInteger -> result.data.toString()
        is FlowDataType.FlowDouble -> result.data.toString()
        is FlowDataType.FlowBoolean -> result.data.toString()
        is FlowDataType.FlowArrayString -> result.data.joinToString("\n")
        is FlowDataType.FlowArrayInteger -> result.data.joinToString("\n")
        is FlowDataType.FlowArrayDouble -> result.data.joinToString("\n")
        is FlowDataType.FlowArrayBoolean -> result.data.joinToString("\n")
        is FlowDataType.FlowCritiqueResult -> "success: ${result.success}\nfeedback: ${result.feedback}"
        is FlowDataType.ParallelExecutionResult -> "[${result.name}] ${format(result.output)}"
        else -> result.toString()
    }
}

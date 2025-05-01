package ai.grazie.code.agents.tools.registry.tools.composio

import ai.grazie.code.agents.core.tools.serialization.ToolResultStringSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.json.Json

internal val ComposioJson = Json

internal class ComposioListSerializer<T>(
    valueSerializer: KSerializer<T>
) : ToolResultStringSerializer<List<T>>(
    descriptor = PrimitiveSerialDescriptor("ComposioCustomFormatList", PrimitiveKind.STRING),
    toStringConverter =  { value ->
        if (value.isEmpty()) {
            "None"
        } else {
            value.joinToString("\n") { "* ${ComposioJson.encodeToString(valueSerializer, it)}" }
        }
    }
)

internal class ComposioSearchResultSerializer<T>(
    valueSerializer: KSerializer<T>
) : ToolResultStringSerializer<List<T>>(
    descriptor = PrimitiveSerialDescriptor("ComposioCustomFormatSearchResult", PrimitiveKind.STRING),
    toStringConverter = { value ->
        value
            .mapIndexed { index, item ->
                """
                ## Details about shortlisted result ID $index:
                ${ComposioJson.encodeToString(valueSerializer, item)}
                """.trimIndent()
            }
            .joinToString("\n")
    }
)
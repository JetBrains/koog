package ai.koog.prompt.executor.clients.serialization

import kotlinx.serialization.SerialInfo
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * Marker attributes are not affected by JsonNamingStrategy during serialization
 * Takes effect when used with RemainSerialNameJsonNamingStrategyWrapper
 */
@SerialInfo
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.PROPERTY)
public annotation class RemainSerialName

/**
 * Retain the original serialization name of properties annotated with {@link RemainSerialName},
 * otherwise use the delegated naming strategy to determine the JSON name.
 */
public class RemainSerialNameJsonNamingStrategyWrapper(private val jsonNamingStrategy: JsonNamingStrategy): JsonNamingStrategy {
    override fun serialNameForJson(
        descriptor: SerialDescriptor,
        elementIndex: Int,
        serialName: String
    ): String {
        return if (descriptor.getElementAnnotations(elementIndex).any { it is RemainSerialName }) {
            serialName
        } else {
            jsonNamingStrategy.serialNameForJson(descriptor, elementIndex, serialName)
        }
    }
}

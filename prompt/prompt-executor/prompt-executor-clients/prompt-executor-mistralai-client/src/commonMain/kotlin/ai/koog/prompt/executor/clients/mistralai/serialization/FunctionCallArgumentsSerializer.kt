package ai.koog.prompt.executor.clients.mistralai.serialization

import ai.koog.prompt.executor.clients.InternalLLMClientApi
import ai.koog.prompt.executor.clients.mistralai.model.FunctionCallArguments
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

@InternalLLMClientApi
public class FunctionCallArgumentsSerializer : KSerializer<FunctionCallArguments> {

    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun deserialize(decoder: Decoder): FunctionCallArguments {
        val element = decoder.decodeSerializableValue(JsonElement.serializer())

        return when (element) {
            is JsonNull -> FunctionCallArguments.NullFunctionCallArguments
            is JsonPrimitive -> {
                when {
                    element.isString -> FunctionCallArguments.StringFunctionCallArguments(element.content)
                    else -> throw SerializationException("Invalid function arguments value")
                }
            }

            else -> throw SerializationException("Invalid function arguments value")
        }
    }

    override fun serialize(
        encoder: Encoder,
        value: FunctionCallArguments
    ) {
        return when (value) {
            FunctionCallArguments.NullFunctionCallArguments -> encoder.encodeNull()
            is FunctionCallArguments.StringFunctionCallArguments -> encoder.encodeString(value.args)
        }
    }


}
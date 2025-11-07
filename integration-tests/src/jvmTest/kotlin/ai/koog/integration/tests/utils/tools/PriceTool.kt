package ai.koog.integration.tests.utils.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

object DoubleOrStringSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DoubleOrString", PrimitiveKind.DOUBLE)

    override fun serialize(encoder: Encoder, value: Double) {
        encoder.encodeDouble(value)
    }

    override fun deserialize(decoder: Decoder): Double {
        val jsonDecoder = decoder as? JsonDecoder ?: error("DoubleOrStringSerializer can be used only with JSON")

        val element = jsonDecoder.decodeJsonElement().jsonPrimitive
        return element.doubleOrNull ?: element.content.toDoubleOrNull()
            ?: error("Cannot parse '${element.content}' as Double")
    }
}

/**
 * Use to test tool with anyOf arguments
 */
object PriceCalculatorTool : Tool<PriceCalculatorTool.Args, Double>() {
    @Serializable
    data class Args(
        val tokens: Int,
        @Serializable(with = DoubleOrStringSerializer::class) @SerialName("price_per_token") val pricePerToken: Double,
        val discount: Double? = null
    )

    override val name: String = "price_calculator"
    override val description: String = "A tool for calculating the price for LLM tokens"
    override val argsSerializer = Args.serializer()
    override val resultSerializer: KSerializer<Double> = Double.serializer()

    override val descriptor = ToolDescriptor(
        name = name,
        description = description,
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "tokens",
                description = "Number of generated tokens",
                type = ToolParameterType.Integer
            ),
            ToolParameterDescriptor(
                name = "price_per_token",
                description = "Price for token, can be String or Double",
                type = ToolParameterType.AnyOf(
                    types = arrayOf(
                        ToolParameterDescriptor(
                            name = "String",
                            description = "String option",
                            type = ToolParameterType.String
                        ),
                        ToolParameterDescriptor(
                            name = "Double",
                            description = "Double option",
                            type = ToolParameterType.Float
                        )
                    )
                )
            ),
        ),
        optionalParameters = listOf(
            ToolParameterDescriptor(
                name = "discount",
                description = "Discount percentage, can be null",
                type = ToolParameterType.Float
            )
        ),
    )

    override suspend fun execute(args: Args): Double {
        return args.tokens * args.pricePerToken * (args.discount ?: 1.0)
    }
}

object DoubleOrNullSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("DoubleOrString", PrimitiveKind.DOUBLE)

    override fun serialize(encoder: Encoder, value: Double?) {
        value?.let { encoder.encodeDouble(it) } ?: encoder.encodeNull()
    }

    override fun deserialize(decoder: Decoder): Double? {
        val jsonDecoder = decoder as? JsonDecoder ?: error("DoubleOrNullSerializer can be used only with JSON")

        val element = jsonDecoder.decodeJsonElement().jsonPrimitive
        return element.doubleOrNull ?: element.content.toDoubleOrNull()
    }
}

/**
 * Use to test tool with nullable arguments
 */
object SimplePriceCalculatorTool : Tool<SimplePriceCalculatorTool.Args, Double>() {
    @Serializable
    data class Args(
        val tokens: Int,
        @Serializable(with = DoubleOrStringSerializer::class) @SerialName("price_per_token") val pricePerToken: Double,
        @Serializable(with = DoubleOrNullSerializer::class) val discount: Double?,
    )

    override val name: String = "price_calculator"
    override val description: String = "A tool for calculating the price for LLM tokens"
    override val argsSerializer = Args.serializer()
    override val resultSerializer: KSerializer<Double> = Double.serializer()

    override val descriptor = ToolDescriptor(
        name = name,
        description = description,
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "tokens",
                description = "Number of generated tokens",
                type = ToolParameterType.Integer
            ),
            ToolParameterDescriptor(
                name = "price_per_token",
                description = "Price for token, Double",
                type = ToolParameterType.Float
            ),
            ToolParameterDescriptor(
                name = "discount",
                description = "Discount for token, can be Double or Null",
                type = ToolParameterType.AnyOf(
                    types = arrayOf(
                        ToolParameterDescriptor(
                            name = "discount",
                            description = "Price for token, can be, Double option",
                            type = ToolParameterType.Float
                        ),
                        ToolParameterDescriptor(
                            name = "discount",
                            description = "Price for token, can be, Null option",
                            type = ToolParameterType.Null
                        )
                    )
                )
            ),
        )
    )

    override suspend fun execute(args: Args): Double {
        return args.tokens * args.pricePerToken * (args.discount ?: 1.0)
    }
}

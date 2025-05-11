package ai.jetbrains.code.prompt.llm

import kotlinx.serialization.Serializable

@Serializable
sealed class LLMCapability(val id: String) {
    @Serializable
    data object Speculation : LLMCapability("speculation")

    @Serializable
    data object Temperature : LLMCapability("temperature")

    @Serializable
    data object Tools : LLMCapability("tools")

    @Serializable
    data object Vision : LLMCapability("vision")

    @Serializable
    data object Embed : LLMCapability("embed")

    @Serializable
    data object Completion : LLMCapability("embed")

    @Serializable
    sealed class Schema(val lang: String) : LLMCapability("$lang-schema") {
        @Serializable
        sealed class JSON(val support: String) : Schema("json-$support") {
            /**
             * Simple support means only standard fields, without definitions, urls and recursive checks
             */
            @Serializable
            data object Simple : JSON("simple")

            @Serializable
            data object Full : JSON("full")
        }
    }
}

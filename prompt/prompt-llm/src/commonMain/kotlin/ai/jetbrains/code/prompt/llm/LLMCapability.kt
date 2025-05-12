package ai.jetbrains.code.prompt.llm

import kotlinx.serialization.Serializable

@Serializable
public sealed class LLMCapability(public val id: String) {
    @Serializable
    public data object Speculation : LLMCapability("speculation")

    @Serializable
    public data object Temperature : LLMCapability("temperature")

    @Serializable
    public data object Tools : LLMCapability("tools")

    @Serializable
    public data object Vision : LLMCapability("vision")

    @Serializable
    public data object Embed : LLMCapability("embed")

    @Serializable
    public data object Completion : LLMCapability("embed")

    @Serializable
    public sealed class Schema(public val lang: String) : LLMCapability("$lang-schema") {
        @Serializable
        public sealed class JSON(public val support: String) : Schema("json-$support") {
            /**
             * Simple support means only standard fields, without definitions, urls and recursive checks
             */
            @Serializable
            public data object Simple : JSON("simple")

            @Serializable
            public data object Full : JSON("full")
        }
    }
}

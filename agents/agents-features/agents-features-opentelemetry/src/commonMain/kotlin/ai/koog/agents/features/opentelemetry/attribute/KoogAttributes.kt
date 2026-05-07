package ai.koog.agents.features.opentelemetry.attribute

import ai.koog.agents.utils.HiddenString
import ai.koog.prompt.dsl.ModerationResult
import kotlinx.serialization.json.Json

/**
 * Koog-specific attribute hierarchy for spans and events emitted by the OpenTelemetry feature.
 */
public object KoogAttributes {

    /**
     * `ModerationResult.categories` uses a class-typed (sealed) [ai.koog.prompt.dsl.ModerationCategory]
     * key, which the default JSON encoder rejects. Allow structured map keys so the encoder serializes
     * the map as `[key, value, key, value, ...]`.
     */
    private val moderationJson = Json { allowStructuredMapKeys = true }

    /**
     * Value set on `gen_ai.provider.name` for `execute_tool` metrics and spans. LLM operations
     * use the actual LLM provider id instead.
     */
    public const val PROVIDER_NAME: String = "koog"

    /**
     * `koog` attribute namespace.
     */
    public sealed interface Koog : Attribute {
        override val key: String
            get() = "koog"

        /**
         * `koog.event` attribute namespace.
         */
        public sealed interface Event : Koog {
            override val key: String
                get() = super.key.concatKey("event")

            /**
             * `koog.event.id` attribute.
             */
            public data class Id(public val id: String) : Event {
                override val key: String = super.key.concatKey("id")
                override val value: String = id
            }
        }

        /**
         * `koog.strategy` attribute namespace.
         */
        public sealed interface Strategy : Koog {
            override val key: String
                get() = super.key.concatKey("strategy")

            /**
             * `koog.strategy.name` attribute.
             */
            public data class Name(public val name: String) : Strategy {
                override val key: String = super.key.concatKey("name")
                override val value: String = name
            }
        }

        /**
         * `koog.node` attribute namespace.
         */
        public sealed interface Node : Koog {
            override val key: String
                get() = super.key.concatKey("node")

            /**
             * `koog.node.id` attribute.
             */
            public data class Id(public val id: String) : Node {
                override val key: String = super.key.concatKey("id")
                override val value: String = id
            }

            /**
             * `koog.node.input` attribute.
             */
            public data class Input(public val input: String) : Node {
                override val key: String = super.key.concatKey("input")
                override val value: HiddenString = HiddenString(input)
            }

            /**
             * `koog.node.output` attribute.
             */
            public data class Output(public val output: String) : Node {
                override val key: String = super.key.concatKey("output")
                override val value: HiddenString = HiddenString(output)
            }
        }

        /**
         * `koog.subgraph` attribute namespace.
         */
        public sealed interface Subgraph : Koog {
            override val key: String
                get() = super.key.concatKey("subgraph")

            /**
             * `koog.subgraph.id` attribute.
             */
            public data class Id(public val id: String) : Subgraph {
                override val key: String = super.key.concatKey("id")
                override val value: String = id
            }

            /**
             * `koog.subgraph.input` attribute.
             */
            public data class Input(public val input: String) : Subgraph {
                override val key: String = super.key.concatKey("input")
                override val value: HiddenString = HiddenString(input)
            }

            /**
             * `koog.subgraph.output` attribute.
             */
            public data class Output(public val output: String) : Subgraph {
                override val key: String = super.key.concatKey("output")
                override val value: HiddenString = HiddenString(output)
            }
        }

        /**
         * `koog.moderation` attribute namespace.
         */
        public sealed interface Moderation : Koog {
            override val key: String
                get() = super.key.concatKey("moderation")

            /**
             * `koog.moderation.result` attribute.
             *
             * The OpenTelemetry GenAI semantic conventions do not define a moderation outcome
             * attribute, so Koog publishes this as a Koog-namespaced attribute on the inference
             * span when moderationResponse is present.
             */
            public data class Result(public val moderationResult: ModerationResult) : Moderation {
                override val key: String = super.key.concatKey("result")
                override val value: HiddenString = HiddenString(
                    moderationJson.encodeToString(ModerationResult.serializer(), moderationResult)
                )
            }
        }

        /**
         * `koog.tool` attribute namespace.
         */
        public sealed interface Tool : Koog {
            override val key: String
                get() = super.key.concatKey("tool")

            /**
             * `koog.tool.call` attribute namespace.
             */
            public sealed interface Call : Tool {
                override val key: String
                    get() = super.key.concatKey("call")

                /**
                 * `koog.tool.call.status` attribute.
                 */
                public data class Status(public val status: StatusType) : Call {
                    override val key: String = super.key.concatKey("status")
                    override val value: String = status.value
                }

                /**
                 * `koog.tool.call.status` allowed values.
                 */
                public enum class StatusType(public val value: String) {
                    SUCCESS("success"),
                    ERROR("error"),
                    VALIDATION_FAILED("validation_failed")
                }
            }
        }
    }
}

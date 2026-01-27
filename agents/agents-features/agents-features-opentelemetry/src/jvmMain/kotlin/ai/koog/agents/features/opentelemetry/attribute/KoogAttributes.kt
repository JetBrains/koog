package ai.koog.agents.features.opentelemetry.attribute

import ai.koog.agents.utils.HiddenString

/**
 * An internal object that contains nested sealed interfaces and data classes for representing structured attributes
 * used within the Koog framework. These attributes facilitate the creation and organization of hierarchical
 * key-value pairs, which can be used for event tracking, strategy configuration, node identification,
 * and subgraph definitions.
 */
internal object KoogAttributes {

    sealed interface Koog : Attribute {
        override val key: String
            get() = "koog"

        sealed interface Event : Koog {
            override val key: String
                get() = super.key.concatKey("event")

            data class Id(private val id: String) : Event {
                override val key: String = super.key.concatKey("id")
                override val value: String = id
            }
        }

        sealed interface Strategy : Koog {
            override val key: String
                get() = super.key.concatKey("strategy")

            data class Name(private val name: String) : Strategy {
                override val key: String = super.key.concatKey("name")
                override val value: String = name
            }
        }

        sealed interface Node : Koog {
            override val key: String
                get() = super.key.concatKey("node")

            data class Id(private val id: String) : Node {
                override val key: String = super.key.concatKey("id")
                override val value: String = id
            }

            data class Input(private val input: String) : Node {
                override val key: String = super.key.concatKey("input")
                override val value: HiddenString = HiddenString(input)
            }

            data class Output(private val output: String) : Node {
                override val key: String = super.key.concatKey("output")
                override val value: HiddenString = HiddenString(output)
            }
        }

        sealed interface Subgraph : Koog {
            override val key: String
                get() = super.key.concatKey("subgraph")

            data class Id(private val id: String) : Subgraph {
                override val key: String = super.key.concatKey("id")
                override val value: String = id
            }

            data class Input(private val input: String) : Subgraph {
                override val key: String = super.key.concatKey("input")
                override val value: HiddenString = HiddenString(input)
            }

            data class Output(private val output: String) : Subgraph {
                override val key: String = super.key.concatKey("output")
                override val value: HiddenString = HiddenString(output)
            }
        }

        sealed interface Tool : Koog {
            override val key: String
                get() = super.key.concatKey("tool")


            sealed interface Call : Tool {
                override val key: String
                    get() = super.key.concatKey("call")

                // koog.tool.call.status
                data class Status(private val status: StatusType) : Call {
                    override val key: String = super.key.concatKey("status")
                    override val value: String = status.str
                }

                enum class StatusType(val str: String) {
                    SUCCESS("success"),
                    ERROR("error"),
                    VALIDATION_FAILED("validation_failed")
                }
            }
        }
    }
}

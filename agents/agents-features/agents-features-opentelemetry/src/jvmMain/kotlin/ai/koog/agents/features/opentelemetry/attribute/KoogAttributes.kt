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

        sealed interface Planner : Koog {
            override val key: String
                get() = super.key.concatKey("planner")

            sealed interface Plan : Planner {
                override val key: String
                    get() = super.key.concatKey("plan")

                data class Input(private val plan: String) : Plan {
                    override val key: String = super.key.concatKey("input")
                    override val value: HiddenString = HiddenString(plan)
                }

                data class Output(private val plan: String) : Plan {
                    override val key: String = super.key.concatKey("output")
                    override val value: HiddenString = HiddenString(plan)
                }
            }

            sealed interface Step : Planner {
                override val key: String
                    get() = super.key.concatKey("step")

                data class Index(private val index: Int) : Step {
                    override val key: String = super.key.concatKey("index")
                    override val value: Long = index.toLong()
                }
            }

            sealed interface State : Planner {
                override val key: String
                    get() = super.key.concatKey("state")

                data class Input(private val state: String) : State {
                    override val key: String = super.key.concatKey("input")
                    override val value: HiddenString = HiddenString(state)
                }

                data class Output(private val state: String) : State {
                    override val key: String = super.key.concatKey("output")
                    override val value: HiddenString = HiddenString(state)
                }
            }

            sealed interface Completion : Planner {
                override val key: String
                    get() = super.key.concatKey("completion")

                data class IsCompleted(private val isCompleted: Boolean) : Completion {
                    override val key: String = super.key.concatKey("is_completed")
                    override val value: Boolean = isCompleted
                }
            }
        }
    }
}

package ai.koog.agents.features.opentelemetry.metric

internal object KoogMetrics {

    sealed interface Client : KoogMetric {

        override val name: String
            get() = super.name.concatKey("client")

        sealed interface Tool : Client {

            override val name: String
                get() = super.name.concatKey("tool")

            sealed interface Call : Tool {

                override val name: String
                    get() = super.name.concatKey("call")

                object Count : Call {

                    override val name: String
                        get() = super.name.concatKey("count")

                    override val description: String = "Number of tool calls performed by the agent"
                    override val unit: String = "{call}"
                }
            }
        }
    }
}

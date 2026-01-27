package ai.koog.agents.features.opentelemetry.metric

internal object KoogMetricNames {
    sealed interface Tool : KoogMetricName {
        override val name: String
            get() = super.name.concatKey("tool")

        object Count : Tool {
            override val name: String
                get() = super.name.concatKey("count")

            override val description: String
                get() = "Tool calls count"

            override val unit: String
                get() = "tool call"
        }
    }
}

package ai.koog.agents.features.opentelemetry.metric

internal object KoogMetrics {

    sealed interface Tool : KoogMetric {

        override val name: String
            get() = super.name.concatKey("tool")

        object Count : Tool {

            override val name: String
                get() = super.name.concatKey("count")

            override val description: String = "Tool calls count"
            override val unit: String = "tool call"
        }
    }
}

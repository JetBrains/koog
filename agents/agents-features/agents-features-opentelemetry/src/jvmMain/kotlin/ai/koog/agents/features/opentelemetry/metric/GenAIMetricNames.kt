package ai.koog.agents.features.opentelemetry.metric

internal object GenAIMetricNames {
    sealed interface Client : GenAIMetricName {
        override val name: String
            get() = super.name.concatKey("client")

        sealed interface Token : Client {
            override val name: String
                get() = super.name.concatKey("token")

            object Usage : Token {
                override val name: String
                    get() = super.name.concatKey("usage")

                override val description: String
                    get() = "Total token count"

                override val unit: String
                    get() = "token"
            }
        }

        sealed interface Operation : Client {
            override val name: String
                get() = super.name.concatKey("operation")

            object Duration : Operation {
                override val name: String
                    get() = super.name.concatKey("duration")

                override val description: String
                    get() = "Operation duration"

                override val unit: String
                    get() = "s"
            }
        }
    }
}

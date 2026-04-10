package ai.koog.agents.features.opentelemetry.metric

internal object GenAIMetrics {

    sealed interface Client : GenAIMetric {

        override val name: String
            get() = super.name.concatKey("client")

        sealed interface Token : Client {

            override val name: String
                get() = super.name.concatKey("token")

            object Usage : Token {

                override val name: String
                    get() = super.name.concatKey("usage")

                override val description: String = "Number of input and output tokens used"
                override val unit: String = "{token}"
            }
        }

        sealed interface Operation : Client {

            override val name: String
                get() = super.name.concatKey("operation")

            object Duration : Operation {

                override val name: String
                    get() = super.name.concatKey("duration")

                override val description: String = "GenAI operation duration"
                override val unit: String = "s"
            }
        }
    }
}

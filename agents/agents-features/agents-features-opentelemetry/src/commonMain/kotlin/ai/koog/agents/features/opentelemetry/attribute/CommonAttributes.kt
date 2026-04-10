package ai.koog.agents.features.opentelemetry.attribute

import ai.koog.prompt.llm.LLMProvider

internal object CommonAttributes {

    // gen_ai.system
    data class System(private val provider: LLMProvider) : GenAIAttribute {
        override val key: String = super.key.concatKey("system")
        override val value: String = provider.id
    }

    // error
    sealed interface Error : Attribute {
        override val key: String
            get() = "error"

        // error.type
        data class Type(private val type: String) : Error {
            override val key: String = super.key.concatKey("type")
            override val value: String = type
        }
    }

    // server
    sealed interface Server : Attribute {
        override val key: String
            get() = "server"

        // server.address
        data class Address(private val address: String) : Server {
            override val key: String = super.key.concatKey("address")
            override val value: String = address
        }

        // server.port
        data class Port(private val port: Int) : Server {
            override val key: String = super.key.concatKey("port")
            override val value: Int = port
        }
    }
}

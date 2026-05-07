package ai.koog.agents.features.opentelemetry.attribute

/**
 * Standard, vendor-neutral OpenTelemetry attributes used across multiple span types.
 * Currently, covers `error.type` and `server.*` attributes per the OTel general semantic conventions.
 */
public object CommonAttributes {

    /**
     * `error` attribute namespace.
     */
    public sealed interface Error : Attribute {
        override val key: String
            get() = "error"

        /**
         * `error.type` attribute.
         */
        public data class Type(public val type: String) : Error {
            override val key: String = super.key.concatKey("type")
            override val value: String = type
        }
    }

    /**
     * `server` attribute namespace.
     */
    public sealed interface Server : Attribute {
        override val key: String
            get() = "server"

        /**
         * `server.address` attribute.
         */
        public data class Address(public val address: String) : Server {
            override val key: String = super.key.concatKey("address")
            override val value: String = address
        }

        /**
         * `server.port` attribute.
         */
        public data class Port(public val port: Int) : Server {
            override val key: String = super.key.concatKey("port")
            override val value: Int = port
        }
    }
}

package ai.koog.agents.features.opentelemetry.attribute

/**
 * MCP (Model Context Protocol) specific attributes following OpenTelemetry semantic conventions.
 * Based on:
 * https://github.com/open-telemetry/semantic-conventions/blob/main/docs/registry/attributes/mcp.md
 * https://github.com/open-telemetry/semantic-conventions/blob/main/model/mcp/common.yaml
 *
 * These attributes are used for instrumenting MCP client operations including:
 * - Tools operations
 * - Session management
 * - Protocol version information
 * - TODO: Resources and prompts operations
 */
internal object McpAttributes {

    sealed interface Mcp : Attribute {
        override val key: String
            get() = "mcp"

        /**
         * The name of the request or notification method.
         * This is a REQUIRED attribute for all MCP operations.
         *
         * @see [ai.koog.agents.features.opentelemetry.integration.mcp.McpMethod]
         */
        sealed interface Method : Mcp {
            override val key: String
                get() = super.key.concatKey("method")

            data class Name(private val name: String) : Method {
                override val key: String = super.key.concatKey("name")
                override val value: String = name
            }
        }

        /**
         * Identifies the MCP session.
         * This is a RECOMMENDED attribute when the request is part of a session.
         *
         * Example: "191c4850af6c49e08843a3f6c80e5046"
         */
        sealed interface Session : Mcp {
            override val key: String
                get() = super.key.concatKey("session")

            data class Id(private val id: String) : Session {
                override val key: String = super.key.concatKey("id")
                override val value: String = id
            }
        }

        /**
         * The version of the Model Context Protocol in use.
         * This is a RECOMMENDED attribute.
         *
         * Example: "2025-06-18"
         */
        sealed interface Protocol : Mcp {
            override val key: String
                get() = super.key.concatKey("protocol")

            data class Version(private val version: String) : Protocol {
                override val key: String = super.key.concatKey("version")
                override val value: String = version
            }
        }
    }

    /**
     * Network transport and protocol attributes for MCP operations.
     */
    sealed interface Network : Attribute {
        override val key: String
            get() = "network"

        /**
         * The transport protocol used for the MCP session.
         * This is RECOMMENDED.
         *
         * https://github.com/open-telemetry/semantic-conventions/blob/main/model/mcp/common.yaml
         *
         * Valid values:
         * - "pipe" for stdio transport
         * - "tcp" for HTTP transport
         * - "quic" for HTTP/3 transport
         */
        data class Transport(private val transport: String) : Network {
            override val key: String = "network.transport"
            override val value: String = transport
        }
    }
}

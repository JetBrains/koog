package ai.koog.agents.features.opentelemetry.attribute

/**
 * MCP (Model Context Protocol) specific attributes following OpenTelemetry semantic conventions.
 * Based on: https://github.com/open-telemetry/semantic-conventions/blob/main/docs/registry/attributes/mcp.md
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
     * JSON-RPC protocol attributes for MCP operations.
     */
    sealed interface JsonRpc : Attribute {
        override val key: String
            get() = "jsonrpc"

        /**
         * The request ID from the JSON-RPC request.
         * This is CONDITIONALLY REQUIRED when the client executes a request (not a notification).
         */
        sealed interface Request : JsonRpc {
            override val key: String
                get() = super.key.concatKey("request")

            data class Id(private val id: String) : Request {
                override val key: String = super.key.concatKey("id")
                override val value: String = id
            }
        }

        /**
         * The version of the JSON-RPC protocol.
         * This is RECOMMENDED if not 2.0.
         *
         * Example: "2.0"
         */
        sealed interface Protocol : JsonRpc {
            override val key: String
                get() = super.key.concatKey("protocol")

            data class Version(private val version: String) : Protocol {
                override val key: String = super.key.concatKey("version")
                override val value: String = version
            }
        }
    }

    /**
     * RPC-specific attributes for MCP operations.
     */
    sealed interface Rpc : Attribute {
        override val key: String
            get() = "rpc"

        /**
         * Response attributes for RPC operations.
         */
        sealed interface Response : Rpc {
            override val key: String
                get() = super.key.concatKey("response")

            /**
             * The error code from JSON-RPC responses.
             * This is CONDITIONALLY REQUIRED if the response contains an error code.
             *
             * Examples: "-32700", "-32600", "-32601", "-32602", "-32603"
             */
            data class StatusCode(private val code: String) : Response {
                override val key: String = super.key.concatKey("status_code")
                override val value: String = code
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

    /**
     * Server address and port attributes for MCP client operations.
     */
    sealed interface Server : Attribute {
        override val key: String
            get() = "server"

        /**
         * Server address for client spans.
         * This is RECOMMENDED.
         */
        data class Address(private val address: String) : Server {
            override val key: String = super.key.concatKey("address")
            override val value: String = address
        }

        /**
         * Server port for client spans.
         * This is RECOMMENDED.
         */
        data class Port(private val port: Int) : Server {
            override val key: String = super.key.concatKey("port")
            override val value: Int = port
        }
    }
}

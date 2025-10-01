package ai.koog.a2a.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.jvm.JvmInline

/**
 * The [AgentCard] is a self-describing manifest for an agent. It provides essential metadata including the agent's
 * identity, capabilities, skills, supported communication methods, and security requirements.
 *
 * @property protocolVersion The version of the A2A protocol this agent supports. Default: "0.3.0".
 *
 * @property name A human-readable name for the agent. Examples: ["Recipe Agent"].
 *
 * @property description A human-readable description of the agent, assisting users and other agents in understanding its purpose.
 *
 *   Examples: ["Agent that helps users with recipes and cooking."].
 *
 * @property url The preferred endpoint URL for interacting with the agent. This URL MUST support the transport specified by 'preferredTransport'.
 *
 *   Examples: ["https://api.example.com/a2a/v1"].
 *
 * @property preferredTransport The transport protocol for the preferred endpoint (the main 'url' field). If not specified, defaults to 'JSONRPC'.
 *
 *   IMPORTANT: The transport specified here MUST be available at the main 'url'. This creates a binding between the main URL and its supported
 *   transport protocol. Clients should prefer this transport and URL combination when both are supported.
 *
 *   Examples: ["JSONRPC", "GRPC", "HTTP+JSON"].
 *
 * @property additionalInterfaces A list of additional supported interfaces (transport and URL combinations). This allows agents to expose multiple
 *   transports, potentially at different URLs.
 *
 *   Best practices:
 *   - SHOULD include all supported transports
 *   - SHOULD include an entry matching the main 'url' and 'preferredTransport'
 *   - MAY reuse URLs if multiple transports are available at the same endpoint
 *   - MUST accurately declare the transport available at each URL.
 *
 *   Clients can select any interface from this list based on their transport capabilities and preferences, enabling transport
 *   negotiation and fallback scenarios.
 *
 * @property iconUrl An optional URL to an icon for the agent.
 *
 * @property provider Information about the agent's service provider.
 *
 * @property version The agent's own version number. The format is defined by the provider.
 *
 *   Examples: ["1.0.0"].
 *
 * @property documentationUrl An optional URL to the agent's documentation.
 *
 * @property capabilities A declaration of optional capabilities supported by the agent.
 *
 * @property securitySchemes A declaration of the security schemes available to authorize requests. The key is the scheme name.
 *   Follows the OpenAPI 3.0 Security Scheme Object.
 *
 * @property security A list of security requirement objects that apply to all agent interactions. Each object lists security schemes that can be used.
 *   Follows the OpenAPI 3.0 Security Requirement Object. This list can be seen as an OR of ANDs. Each object in the list describes one possible set
 *   of security requirements that must be present on a request. This allows specifying, for example, "callers must either use OAuth OR an API Key AND mTLS."
 *
 *   Examples: [{"oauth": ["read"]}, {"api-key": [], "mtls": []}].
 *
 * @property defaultInputModes Default set of supported input MIME types for all skills, which can be overridden on a per-skill basis.
 *
 * @property defaultOutputModes Default set of supported output MIME types for all skills, which can be overridden on a per-skill basis.
 *
 * @property skills The set of skills, or distinct capabilities, that the agent can perform.
 *
 * @property supportsAuthenticatedExtendedCard If true, the agent can provide an extended agent card with additional details to authenticated users. Defaults to false.
 *
 * @property signatures JSON Web Signatures computed for this [AgentCard].
 */
@Serializable
public data class AgentCard(
    @EncodeDefault
    public val protocolVersion: String = "0.3.0",
    public val name: String,
    public val description: String,
    public val url: String,
    @EncodeDefault
    public val preferredTransport: TransportProtocol = TransportProtocol.JSONRPC,
    public val additionalInterfaces: List<AgentInterface>? = null,
    public val iconUrl: String? = null,
    public val provider: AgentProvider? = null,
    public val version: String,
    public val documentationUrl: String? = null,
    public val capabilities: AgentCapabilities,
    public val securitySchemes: SecuritySchemes? = null,
    public val security: Security? = null,
    public val defaultInputModes: List<String>,
    public val defaultOutputModes: List<String>,
    public val skills: List<AgentSkill>,
    public val supportsAuthenticatedExtendedCard: Boolean? = false,
    public val signatures: List<AgentCardSignature>? = null
) {
    init {
        additionalInterfaces?.let { interfaces ->
            requireNotNull(interfaces.find { it.url == url && it.transport == preferredTransport }) {
                "If additionalInterfaces are specified, they must include an entry matching the main 'url' and 'preferredTransport'."
            }
        }
    }
}

/**
 * The transport protocol for an agent.
 */
@JvmInline
@Serializable
public value class TransportProtocol(public val value: String) {
    /**
     * List of known transport protocols.
     */
    public companion object {
        /**
         * JSON-RPC protocol.
         */
        public val JSONRPC: TransportProtocol = TransportProtocol("JSONRPC")

        /**
         * HTTP+JSON/REST protocol.
         */
        public val HTTP_JSON_REST: TransportProtocol = TransportProtocol("HTTP+JSON/REST")

        /**
         * GRPC protocol.
         */
        public val GRPC: TransportProtocol = TransportProtocol("GRPC")
    }
}

/**
 * Declares a combination of a target URL and a transport protocol for interacting with the agent.
 * This allows agents to expose the same functionality over multiple transport mechanisms.
 *
 * @property url The URL where this interface is available. Must be a valid absolute HTTPS URL in production.
 *
 *   Examples: ["https://api.example.com/a2a/v1", "https://grpc.example.com/a2a", "https://rest.example.com/v1"].
 *
 * @property transport The transport protocol supported at this URL.
 *
 *   Examples: ["JSONRPC", "GRPC", "HTTP+JSON"].
 */
@Serializable
public data class AgentInterface(
    public val url: String,
    public val transport: TransportProtocol
)

/**
 * Represents the service provider of an agent.
 *
 * @property organization The name of the agent provider's organization.
 * @property url A URL for the agent provider's website or relevant documentation.
 */
@Serializable
public data class AgentProvider(
    public val organization: String,
    public val url: String
)

/**
 * Defines optional capabilities supported by an agent.
 *
 * @property streaming Indicates if the agent supports Server-Sent Events (SSE) for streaming responses.
 *
 * @property pushNotifications Indicates if the agent supports sending push notifications for asynchronous task updates.
 *
 * @property stateTransitionHistory Indicates if the agent provides a history of state transitions for a task.
 *
 * TODO: it's not clear from the specification and official Python SDK, what does this field control.
 *   It's not [Task.history], since it always should be present.
 *   There are no further mentions or usages of this field in the official sources.
 *   So currently in our implementation it does not control anything.
 *
 * @property extensions A list of protocol extensions supported by the agent.
 */
@Serializable
public data class AgentCapabilities(
    public val streaming: Boolean? = null,
    public val pushNotifications: Boolean? = null,
    public val stateTransitionHistory: Boolean? = null,
    public val extensions: List<AgentExtension>? = null
)

/**
 * A declaration of a protocol extension supported by an Agent.
 *
 * @property uri The unique URI identifying the extension.
 * @property description A human-readable description of how this agent uses the extension.
 * @property required If true, the client must understand and comply with the extension's requirements to interact with the agent.
 * @property params Optional, extension-specific configuration parameters.
 */
@Serializable
public data class AgentExtension(
    public val uri: String,
    public val description: String? = null,
    public val required: Boolean? = null,
    public val params: Map<String, JsonElement>? = null
)

/**
 * A declaration of the security schemes available to authorize requests. The key is the scheme name. The value is the
 * declaration of the security scheme object, which follows the OpenAPI 3.0 Security Scheme Object.
 */
public typealias SecuritySchemes = Map<String, SecurityScheme>

/**
 * A list of alternative security requirements (a logical OR). To authorize a request, a client must satisfy one of the
 * [SecurityRequirement]s in this list.
 *
 * For example, `[{"oauth": ["read"]}, {"apiKey": [], "mtls": []}]` means a client can use either OAuth with the "read" scope
 * or both an API key and mTLS.
 *
 * @see [https://swagger.io/specification/#security-requirement-object]
 */
public typealias Security = List<SecurityRequirement>

/**
 * A set of security schemes that must be satisfied together (a logical AND). The key is a security scheme name, and the
 * value is a list of required scopes.
 *
 * For example, `{"apiKey": [], "mtls": []}` requires both an API key and mTLS.
 *
 * @see [https://swagger.io/specification/#security-requirement-object]
 */
public typealias SecurityRequirement = Map<String, List<String>>

/**
 * Defines a security scheme that can be used to secure an agent's endpoints.
 * This is a discriminated union type based on the OpenAPI 3.0 Security Scheme Object.
 *
 * @see [https://swagger.io/specification/#security-scheme-object]
 */
@Serializable(with = SecuritySchemeSerializer::class)
public sealed interface SecurityScheme {
    /**
     * The type of the security scheme, used as discriminator.
     */
    public val type: String
}

/**
 * Defines a security scheme using an API key.
 *
 * @property description An optional description for the security scheme.
 * @property in The location of the API key.
 * @property name The name of the header, query, or cookie parameter to be used.
 */
@Serializable
public data class APIKeySecurityScheme(
    @SerialName("in")
    public val `in`: In,
    public val name: String,
    public val description: String? = null,
) : SecurityScheme {
    @EncodeDefault
    override val type: String = "apiKey"
}

/**
 * The location of the API key.
 */
@Serializable
public enum class In {
    @SerialName("cookie")
    Cookie,

    @SerialName("header")
    Header,

    @SerialName("query")
    Query
}

/**
 * Defines a security scheme using HTTP authentication.
 *
 * @property scheme The name of the HTTP Authentication scheme to be used in the Authorization header,
 *   as defined in RFC7235 (e.g., "Bearer"). This value should be registered in the IANA Authentication Scheme registry.
 * @property bearerFormat A hint to the client to identify how the bearer token is formatted (e.g., "JWT").
 *   This is primarily for documentation purposes.
 * @property description An optional description for the security scheme.
 */
@Serializable
public data class HTTPAuthSecurityScheme(
    public val scheme: String,
    public val bearerFormat: String? = null,
    public val description: String? = null,
) : SecurityScheme {
    @EncodeDefault
    override val type: String = "http"
}

/**
 * Defines a security scheme using OAuth 2.0.
 *
 * @property flows An object containing configuration information for the supported OAuth 2.0 flows.
 * @property oauth2MetadataUrl URL to the oauth2 authorization server metadata
 *   [RFC8414](https://datatracker.ietf.org/doc/html/rfc8414). TLS is required.
 * @property description An optional description for the security scheme.
 */
@Serializable
public data class OAuth2SecurityScheme(
    public val flows: OAuthFlows,
    public val oauth2MetadataUrl: String? = null,
    public val description: String? = null,
) : SecurityScheme {
    @EncodeDefault
    override val type: String = "oauth2"
}

/**
 * Defines the configuration for the supported OAuth 2.0 flows.
 *
 * @property authorizationCode Configuration for the OAuth Authorization Code flow. Previously called accessCode in OpenAPI 2.0.
 * @property clientCredentials Configuration for the OAuth Client Credentials flow. Previously called application in OpenAPI 2.0.
 * @property implicit Configuration for the OAuth Implicit flow.
 * @property password Configuration for the OAuth Resource Owner Password flow.
 */
@Serializable
public data class OAuthFlows(
    public val authorizationCode: AuthorizationCodeOAuthFlow? = null,
    public val clientCredentials: ClientCredentialsOAuthFlow? = null,
    public val implicit: ImplicitOAuthFlow? = null,
    public val password: PasswordOAuthFlow? = null
)

/**
 * Common interface for OAuth 2.0 flows.
 */
@Serializable
public sealed interface OAuthFlow {
    /**
     * The available scopes for the OAuth2 security scheme. A map between the scope name and a short description for it.
     */
    public val scopes: Map<String, String>

    /**
     * The URL to be used for obtaining refresh tokens. This MUST be a URL and use TLS.
     */
    public val refreshUrl: String?
}

/**
 * Defines configuration details for the OAuth 2.0 Authorization Code flow.
 *
 * @property authorizationUrl The authorization URL to be used for this flow. This MUST be a URL and use TLS.
 * @property tokenUrl The token URL to be used for this flow. This MUST be a URL and use TLS.
 */
@Serializable
public data class AuthorizationCodeOAuthFlow(
    public val authorizationUrl: String,
    public val tokenUrl: String,
    override val scopes: Map<String, String>,
    override val refreshUrl: String? = null
) : OAuthFlow

/**
 * Defines configuration details for the OAuth 2.0 Client Credentials flow.
 *
 * @property tokenUrl The token URL to be used for this flow. This MUST be a URL.
 */
@Serializable
public data class ClientCredentialsOAuthFlow(
    public val tokenUrl: String,
    override val scopes: Map<String, String>,
    override val refreshUrl: String? = null
) : OAuthFlow

/**
 * Defines configuration details for the OAuth 2.0 Implicit flow.
 *
 * @property authorizationUrl The authorization URL to be used for this flow. This MUST be a URL.
 */
@Serializable
public data class ImplicitOAuthFlow(
    public val authorizationUrl: String,
    override val scopes: Map<String, String>,
    override val refreshUrl: String? = null
) : OAuthFlow

/**
 * Defines configuration details for the OAuth 2.0 Resource Owner Password flow.
 *
 * @property tokenUrl The token URL to be used for this flow. This MUST be a URL.
 */
@Serializable
public data class PasswordOAuthFlow(
    public val tokenUrl: String,
    override val scopes: Map<String, String>,
    override val refreshUrl: String? = null
) : OAuthFlow

/**
 * Defines a security scheme using OpenID Connect.
 *
 * @property openIdConnectUrl The OpenID Connect Discovery URL for the OIDC provider's metadata.
 * @property description An optional description for the security scheme.
 */
@Serializable
public data class OpenIdConnectSecurityScheme(
    public val openIdConnectUrl: String,
    public val description: String? = null,
) : SecurityScheme {
    @EncodeDefault
    override val type: String = "openIdConnect"
}

/**
 * Defines a security scheme using mTLS authentication.
 *
 * @property description An optional description for the security scheme.
 */
@Serializable
public data class MutualTLSSecurityScheme(
    public val description: String? = null,
) : SecurityScheme {
    @EncodeDefault
    override val type: String = "mutualTLS"
}

/**
 * Represents a distinct capability or function that an agent can perform.
 *
 * @property id A unique identifier for the agent's skill.
 *
 * @property name A human-readable name for the skill.
 *
 * @property description A detailed description of the skill, intended to help clients or users understand its purpose and functionality.
 *
 * @property tags A set of keywords describing the skill's capabilities.
 *
 *   Examples: ["cooking", "customer support", "billing"].
 *
 * @property examples Example prompts or scenarios that this skill can handle. Provides a hint to the client on how to use the skill.
 *
 *   Examples: ["I need a recipe for bread"].
 *
 * @property inputModes The set of supported input MIME types for this skill, overriding the agent's defaults.
 *
 * @property outputModes The set of supported output MIME types for this skill, overriding the agent's defaults.
 *
 * @property security Security schemes necessary for the agent to leverage this skill. As in the overall AgentCard.security,
 *   this list represents a logical OR of security requirement objects. Each object is a set of security schemes that must be
 *   used together (a logical AND).
 *
 *   Examples: [{"google": ["oidc"]}].
 */
@Serializable
public data class AgentSkill(
    public val id: String,
    public val name: String,
    public val description: String,
    public val tags: List<String>,
    public val examples: List<String>? = null,
    public val inputModes: List<String>? = null,
    public val outputModes: List<String>? = null,
    public val security: Security? = null
)

/**
 * AgentCardSignature represents a JWS signature of an AgentCard.
 * This follows the JSON format of an RFC 7515 JSON Web Signature (JWS).
 *
 * @property protected The protected JWS header for the signature. This is a Base64url-encoded JSON object, as per RFC 7515.
 * @property signature The computed signature, Base64url-encoded.
 * @property header The unprotected JWS header values.
 */
@Serializable
public data class AgentCardSignature(
    @SerialName("protected")
    public val `protected`: String,
    public val signature: String,
    public val header: Map<String, JsonElement>? = null
)

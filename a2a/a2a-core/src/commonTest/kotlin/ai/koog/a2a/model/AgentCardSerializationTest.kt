package ai.koog.a2a.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AgentCardSerializationTest {
    @Suppress("PrivatePropertyName")
    private val TestJson = Json {
        prettyPrint = true
        encodeDefaults = false
    }

    @Test
    fun testMinimalAgentCardSerialization() {
        val agentCard = AgentCard(
            name = "Test Agent",
            description = "A test agent",
            url = "https://api.example.com/a2a",
            version = "1.0.0",
            capabilities = AgentCapabilities(),
            defaultInputModes = listOf("text/plain"),
            defaultOutputModes = listOf("text/plain"),
            skills = listOf(
                AgentSkill(
                    id = "test-skill",
                    name = "Test Skill",
                    description = "A test skill",
                    tags = listOf("test")
                )
            )
        )

        //language=JSON
        val expectedJson = """
            {
                "protocolVersion": "0.3.0",
                "name": "Test Agent",
                "description": "A test agent",
                "url": "https://api.example.com/a2a",
                "preferredTransport": "JSONRPC",
                "version": "1.0.0",
                "capabilities": {
                    "streaming": false,
                    "pushNotifications": false,
                    "stateTransitionHistory": false
                },
                "defaultInputModes": [
                    "text/plain"
                ],
                "defaultOutputModes": [
                    "text/plain"
                ],
                "skills": [
                    {
                        "id": "test-skill",
                        "name": "Test Skill",
                        "description": "A test skill",
                        "tags": [
                            "test"
                        ]
                    }
                ],
                "supportsAuthenticatedExtendedCard": false
            }
        """.trimIndent()

        val actualJson = TestJson.encodeToString(agentCard)
        assertEquals(expectedJson, actualJson)

        // Test deserialization
        val deserializedCard = TestJson.decodeFromString<AgentCard>(actualJson)
        assertEquals(agentCard, deserializedCard)
    }

    @Test
    fun testFullAgentCardSerialization() {
        val agentCard = AgentCard(
            name = "GeoSpatial Route Planner Agent",
            description = "Provides advanced route planning, traffic analysis, and custom map generation services. This agent can calculate optimal routes, estimate travel times considering real-time traffic, and create personalized maps with points of interest.",
            url = "https://georoute-agent.example.com/a2a/v1",
            additionalInterfaces = listOf(
                AgentInterface(
                    url = "https://georoute-agent.example.com/a2a/v1",
                    transport = TransportProtocol.JSONRPC
                ),
                AgentInterface(
                    url = "https://georoute-agent.example.com/a2a/grpc",
                    transport = TransportProtocol.GRPC
                ),
                AgentInterface(
                    url = "https://georoute-agent.example.com/a2a/json",
                    transport = TransportProtocol.HTTP_JSON_REST
                )
            ),
            provider = AgentProvider(
                organization = "Example Geo Services Inc.",
                url = "https://www.examplegeoservices.com"
            ),
            iconUrl = "https://georoute-agent.example.com/icon.png",
            version = "1.2.0",
            documentationUrl = "https://docs.examplegeoservices.com/georoute-agent/api",
            capabilities = AgentCapabilities(
                streaming = true,
                pushNotifications = true,
                stateTransitionHistory = false
            ),
            securitySchemes = mapOf(
                "google" to OpenIdConnectSecurityScheme(
                    openIdConnectUrl = "https://accounts.google.com/.well-known/openid-configuration"
                )
            ),
            security = listOf(
                mapOf("google" to listOf("openid", "profile", "email"))
            ),
            defaultInputModes = listOf("application/json", "text/plain"),
            defaultOutputModes = listOf("application/json", "image/png"),
            skills = listOf(
                AgentSkill(
                    id = "route-optimizer-traffic",
                    name = "Traffic-Aware Route Optimizer",
                    description = "Calculates the optimal driving route between two or more locations, taking into account real-time traffic conditions, road closures, and user preferences (e.g., avoid tolls, prefer highways).",
                    tags = listOf("maps", "routing", "navigation", "directions", "traffic"),
                    examples = listOf(
                        "Plan a route from '1600 Amphitheatre Parkway, Mountain View, CA' to 'San Francisco International Airport' avoiding tolls.",
                        "{\"origin\": {\"lat\": 37.422, \"lng\": -122.084}, \"destination\": {\"lat\": 37.7749, \"lng\": -122.4194}, \"preferences\": [\"avoid_ferries\"]}"
                    ),
                    inputModes = listOf("application/json", "text/plain"),
                    outputModes = listOf(
                        "application/json",
                        "application/vnd.geo+json",
                        "text/html"
                    )
                ),
                AgentSkill(
                    id = "custom-map-generator",
                    name = "Personalized Map Generator",
                    description = "Creates custom map images or interactive map views based on user-defined points of interest, routes, and style preferences. Can overlay data layers.",
                    tags = listOf("maps", "customization", "visualization", "cartography"),
                    examples = listOf(
                        "Generate a map of my upcoming road trip with all planned stops highlighted.",
                        "Show me a map visualizing all coffee shops within a 1-mile radius of my current location."
                    ),
                    inputModes = listOf("application/json"),
                    outputModes = listOf(
                        "image/png",
                        "image/jpeg",
                        "application/json",
                        "text/html"
                    )
                )
            ),
            supportsAuthenticatedExtendedCard = true,
            signatures = listOf(
                AgentCardSignature(
                    `protected` = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpPU0UiLCJraWQiOiJrZXktMSIsImprdSI6Imh0dHBzOi8vZXhhbXBsZS5jb20vYWdlbnQvandrcy5qc29uIn0",
                    signature = "QFdkNLNszlGj3z3u0YQGt_T9LixY3qtdQpZmsTdDHDe3fXV9y9-B3m2-XgCpzuhiLt8E0tV6HXoZKHv4GtHgKQ"
                )
            )
        )

        //language=JSON
        val expectedJson = """
        {
            "protocolVersion": "0.3.0",
            "name": "GeoSpatial Route Planner Agent",
            "description": "Provides advanced route planning, traffic analysis, and custom map generation services. This agent can calculate optimal routes, estimate travel times considering real-time traffic, and create personalized maps with points of interest.",
            "url": "https://georoute-agent.example.com/a2a/v1",
            "preferredTransport": "JSONRPC",
            "additionalInterfaces": [
                {
                    "url": "https://georoute-agent.example.com/a2a/v1",
                    "transport": "JSONRPC"
                },
                {
                    "url": "https://georoute-agent.example.com/a2a/grpc",
                    "transport": "GRPC"
                },
                {
                    "url": "https://georoute-agent.example.com/a2a/json",
                    "transport": "HTTP+JSON/REST"
                }
            ],
            "iconUrl": "https://georoute-agent.example.com/icon.png",
            "provider": {
                "organization": "Example Geo Services Inc.",
                "url": "https://www.examplegeoservices.com"
            },
            "version": "1.2.0",
            "documentationUrl": "https://docs.examplegeoservices.com/georoute-agent/api",
            "capabilities": {
                "streaming": true,
                "pushNotifications": true,
                "stateTransitionHistory": false
            },
            "securitySchemes": {
                "google": {
                    "openIdConnectUrl": "https://accounts.google.com/.well-known/openid-configuration",
                    "type": "openIdConnect"
                }
            },
            "security": [
                {
                    "google": [
                        "openid",
                        "profile",
                        "email"
                    ]
                }
            ],
            "defaultInputModes": [
                "application/json",
                "text/plain"
            ],
            "defaultOutputModes": [
                "application/json",
                "image/png"
            ],
            "skills": [
                {
                    "id": "route-optimizer-traffic",
                    "name": "Traffic-Aware Route Optimizer",
                    "description": "Calculates the optimal driving route between two or more locations, taking into account real-time traffic conditions, road closures, and user preferences (e.g., avoid tolls, prefer highways).",
                    "tags": [
                        "maps",
                        "routing",
                        "navigation",
                        "directions",
                        "traffic"
                    ],
                    "examples": [
                        "Plan a route from '1600 Amphitheatre Parkway, Mountain View, CA' to 'San Francisco International Airport' avoiding tolls.",
                        "{\"origin\": {\"lat\": 37.422, \"lng\": -122.084}, \"destination\": {\"lat\": 37.7749, \"lng\": -122.4194}, \"preferences\": [\"avoid_ferries\"]}"
                    ],
                    "inputModes": [
                        "application/json",
                        "text/plain"
                    ],
                    "outputModes": [
                        "application/json",
                        "application/vnd.geo+json",
                        "text/html"
                    ]
                },
                {
                    "id": "custom-map-generator",
                    "name": "Personalized Map Generator",
                    "description": "Creates custom map images or interactive map views based on user-defined points of interest, routes, and style preferences. Can overlay data layers.",
                    "tags": [
                        "maps",
                        "customization",
                        "visualization",
                        "cartography"
                    ],
                    "examples": [
                        "Generate a map of my upcoming road trip with all planned stops highlighted.",
                        "Show me a map visualizing all coffee shops within a 1-mile radius of my current location."
                    ],
                    "inputModes": [
                        "application/json"
                    ],
                    "outputModes": [
                        "image/png",
                        "image/jpeg",
                        "application/json",
                        "text/html"
                    ]
                }
            ],
            "supportsAuthenticatedExtendedCard": true,
            "signatures": [
                {
                    "protected": "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpPU0UiLCJraWQiOiJrZXktMSIsImprdSI6Imh0dHBzOi8vZXhhbXBsZS5jb20vYWdlbnQvandrcy5qc29uIn0",
                    "signature": "QFdkNLNszlGj3z3u0YQGt_T9LixY3qtdQpZmsTdDHDe3fXV9y9-B3m2-XgCpzuhiLt8E0tV6HXoZKHv4GtHgKQ"
                }
            ]
        }
        """.trimIndent()

        val actualJson = TestJson.encodeToString(agentCard)

        assertEquals(expectedJson, actualJson)
    }

    @Test
    fun testTransportProtocolSerialization() {
        val jsonRpc = TransportProtocol.JSONRPC
        val httpJson = TransportProtocol.HTTP_JSON_REST
        val grpc = TransportProtocol.GRPC
        val custom = TransportProtocol("CUSTOM")

        assertEquals("\"JSONRPC\"", TestJson.encodeToString(jsonRpc))
        assertEquals("\"HTTP+JSON/REST\"", TestJson.encodeToString(httpJson))
        assertEquals("\"GRPC\"", TestJson.encodeToString(grpc))
        assertEquals("\"CUSTOM\"", TestJson.encodeToString(custom))

        // Test deserialization
        assertEquals(jsonRpc, TestJson.decodeFromString("\"JSONRPC\""))
        assertEquals(httpJson, TestJson.decodeFromString("\"HTTP+JSON/REST\""))
        assertEquals(grpc, TestJson.decodeFromString("\"GRPC\""))
        assertEquals(custom, TestJson.decodeFromString("\"CUSTOM\""))
    }

    @Test
    fun testSecuritySchemeSerialization() {
        // API Key Security Scheme
        val apiKeyScheme = APIKeySecurityScheme(
            `in` = In.Header,
            name = "Authorization",
            description = "Bearer token"
        )
        //language=JSON
        val apiKeyJson = """
            {
                "in": "header",
                "name": "Authorization",
                "description": "Bearer token",
                "type": "apiKey"
            }
        """.trimIndent()
        assertEquals(apiKeyJson, TestJson.encodeToString<SecurityScheme>(apiKeyScheme))

        // HTTP Auth Security Scheme
        val httpScheme = HTTPAuthSecurityScheme(
            scheme = "Bearer",
            bearerFormat = "JWT",
            description = "JWT Bearer token"
        )
        //language=JSON
        val httpJson = """
            {
                "scheme": "Bearer",
                "bearerFormat": "JWT",
                "description": "JWT Bearer token",
                "type": "http"
            }
        """.trimIndent()
        assertEquals(httpJson, TestJson.encodeToString<SecurityScheme>(httpScheme))

        // OAuth2 Security Scheme
        val oauth2Scheme = OAuth2SecurityScheme(
            flows = OAuthFlows(
                authorizationCode = AuthorizationCodeOAuthFlow(
                    authorizationUrl = "https://auth.example.com/oauth/authorize",
                    tokenUrl = "https://auth.example.com/oauth/token",
                    scopes = mapOf("read" to "Read access", "write" to "Write access")
                )
            ),
            description = "OAuth2 with authorization code flow"
        )
        //language=JSON
        val expectedOAuth2Json = """
            {
                "flows": {
                    "authorizationCode": {
                        "authorizationUrl": "https://auth.example.com/oauth/authorize",
                        "tokenUrl": "https://auth.example.com/oauth/token",
                        "scopes": {
                            "read": "Read access",
                            "write": "Write access"
                        }
                    }
                },
                "description": "OAuth2 with authorization code flow",
                "type": "oauth2"
            }
        """.trimIndent()

        val oauth2Json = TestJson.encodeToString<SecurityScheme>(oauth2Scheme)
        assertEquals(expectedOAuth2Json, oauth2Json)

        // OpenID Connect Security Scheme
        val oidcScheme = OpenIdConnectSecurityScheme(
            openIdConnectUrl = "https://auth.example.com/.well-known/openid_configuration"
        )
        //language=JSON
        val oidcJson = """
            {
                "openIdConnectUrl": "https://auth.example.com/.well-known/openid_configuration",
                "type": "openIdConnect"
            }
        """.trimIndent()
        assertEquals(oidcJson, TestJson.encodeToString<SecurityScheme>(oidcScheme))

        // Mutual TLS Security Scheme
        val mtlsScheme = MutualTLSSecurityScheme(
            description = "Client certificate authentication"
        )
        //language=JSON
        val mtlsJson = """
            {
                "description": "Client certificate authentication",
                "type": "mutualTLS"
            }
        """.trimIndent()
        assertEquals(mtlsJson, TestJson.encodeToString<SecurityScheme>(mtlsScheme))

        // Test deserialization
        assertEquals(apiKeyScheme, TestJson.decodeFromString<SecurityScheme>(apiKeyJson))
        assertEquals(httpScheme, TestJson.decodeFromString<SecurityScheme>(httpJson))
        assertEquals(oauth2Scheme, TestJson.decodeFromString<SecurityScheme>(expectedOAuth2Json))
        assertEquals(oidcScheme, TestJson.decodeFromString<SecurityScheme>(oidcJson))
        assertEquals(mtlsScheme, TestJson.decodeFromString<SecurityScheme>(mtlsJson))
    }

    @Test
    fun testOAuthFlowsSerialization() {
        val flows = OAuthFlows(
            authorizationCode = AuthorizationCodeOAuthFlow(
                authorizationUrl = "https://auth.example.com/oauth/authorize",
                tokenUrl = "https://auth.example.com/oauth/token",
                scopes = mapOf("read" to "Read access"),
                refreshUrl = "https://auth.example.com/oauth/refresh"
            ),
            clientCredentials = ClientCredentialsOAuthFlow(
                tokenUrl = "https://auth.example.com/oauth/token",
                scopes = mapOf("admin" to "Admin access")
            ),
            implicit = ImplicitOAuthFlow(
                authorizationUrl = "https://auth.example.com/oauth/authorize",
                scopes = mapOf("read" to "Read access")
            ),
            password = PasswordOAuthFlow(
                tokenUrl = "https://auth.example.com/oauth/token",
                scopes = mapOf("user" to "User access")
            )
        )

        val json = TestJson.encodeToString(flows)
        val deserialized = TestJson.decodeFromString<OAuthFlows>(json)
        assertEquals(flows, deserialized)
    }

    @Test
    fun testAgentCapabilitiesSerialization() {
        // Test default capabilities
        val defaultCapabilities = AgentCapabilities()
        //language=JSON
        val defaultJson = """
            {
                "streaming": false,
                "pushNotifications": false,
                "stateTransitionHistory": false
            }
        """.trimIndent()
        assertEquals(defaultJson, TestJson.encodeToString(defaultCapabilities))

        // Test full capabilities
        val fullCapabilities = AgentCapabilities(
            streaming = true,
            pushNotifications = true,
            stateTransitionHistory = true,
            extensions = listOf(
                AgentExtension(
                    uri = "https://example.com/ext/v1",
                    description = "Test extension",
                    required = true
                )
            )
        )
        //language=JSON
        val expectedFullJson = """
            {
                "streaming": true,
                "pushNotifications": true,
                "stateTransitionHistory": true,
                "extensions": [
                    {
                        "uri": "https://example.com/ext/v1",
                        "description": "Test extension",
                        "required": true
                    }
                ]
            }
        """.trimIndent()

        val fullJson = TestJson.encodeToString(fullCapabilities)
        assertEquals(expectedFullJson, fullJson)

        // Test deserialization
        assertEquals(fullCapabilities, TestJson.decodeFromString<AgentCapabilities>(expectedFullJson))
    }

    @Test
    fun testAgentSkillSerialization() {
        val skill = AgentSkill(
            id = "test-skill",
            name = "Test Skill",
            description = "A comprehensive test skill",
            tags = listOf("test", "demo"),
            examples = listOf("How to test?", "Show me a demo"),
            inputModes = listOf("text/plain", "application/json"),
            outputModes = listOf("text/plain"),
            security = listOf(
                mapOf("oauth" to listOf("read", "write")),
                mapOf("api-key" to emptyList())
            )
        )

        //language=JSON
        val expectedJson = """
            {
                "id": "test-skill",
                "name": "Test Skill",
                "description": "A comprehensive test skill",
                "tags": [
                    "test",
                    "demo"
                ],
                "examples": [
                    "How to test?",
                    "Show me a demo"
                ],
                "inputModes": [
                    "text/plain",
                    "application/json"
                ],
                "outputModes": [
                    "text/plain"
                ],
                "security": [
                    {
                        "oauth": [
                            "read",
                            "write"
                        ]
                    },
                    {
                        "api-key": []
                    }
                ]
            }
        """.trimIndent()

        val json = TestJson.encodeToString(skill)
        assertEquals(expectedJson, json)

        val deserialized = TestJson.decodeFromString<AgentSkill>(json)
        assertEquals(skill, deserialized)
    }

    @Test
    fun testEnumSerialization() {
        assertEquals("\"cookie\"", TestJson.encodeToString(In.Cookie))
        assertEquals("\"header\"", TestJson.encodeToString(In.Header))
        assertEquals("\"query\"", TestJson.encodeToString(In.Query))

        assertEquals(In.Cookie, TestJson.decodeFromString("\"cookie\""))
        assertEquals(In.Header, TestJson.decodeFromString("\"header\""))
        assertEquals(In.Query, TestJson.decodeFromString("\"query\""))
    }

    @Test
    fun testAgentCardSignatureSerialization() {
        val signature = AgentCardSignature(
            `protected` = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9",
            signature = "signature-data-here",
            header = mapOf("kid" to kotlinx.serialization.json.JsonPrimitive("key-id-123"))
        )

        //language=JSON
        val expectedJson = """
            {
                "protected": "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9",
                "signature": "signature-data-here",
                "header": {
                    "kid": "key-id-123"
                }
            }
        """.trimIndent()

        val json = TestJson.encodeToString(signature)
        assertEquals(expectedJson, json)

        val deserialized = TestJson.decodeFromString<AgentCardSignature>(json)
        assertEquals(signature, deserialized)
    }
}

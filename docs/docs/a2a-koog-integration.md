# A2A and Koog Integration

Koog provides seamless integration with the A2A protocol, allowing you to expose Koog agents as A2A servers and connect Koog agents to other A2A-compliant agents.

## Overview

The integration enables two main patterns:

1. **Expose Koog agents as A2A servers** - Make your Koog agents discoverable and accessible via the A2A protocol
2. **Connect Koog agents to A2A agents** - Let your Koog agents communicate with other A2A-compliant agents

## Exposing Koog Agents as A2A Servers

### Example 1: Simple Chat Assistant with AgentExecutor

Define a Koog agent first. The logic of the agent can vary, but here's a basic chat assistant:

```kotlin
class KoogChatExecutor : AgentExecutor {

    private fun createAgent(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor
    ) = AIAgent(
        promptExecutor = MultiLLMPromptExecutor(
            LLMProvider.Anthropic to AnthropicLLMClient(ApiKeyService.anthropicApiKey)
        ),
        strategy = strategy("chat-strategy") {
            // Strategy configuration for the agent
        },
        agentConfig = AIAgentConfig(
            prompt = prompt("chat") {
                system { +"You are a helpful AI assistant. Be concise and clear." }
            },
            model = AnthropicModels.Sonnet_4,
            maxAgentIterations = 1
        )
    ) {
        install(A2AAgentServer) {
            this.context = context
            this.eventProcessor = eventProcessor
        }

        graph {
            val userMessage = input<Message.User>()

            // Use Koog's LLM prompt processing
            val response = nodeLLMPrompt(AnthropicModels.Sonnet_4) {
                system { +"You are a helpful AI assistant. Be concise and clear." }
                user { +userMessage.content }
            }

            // Send the response through A2A
            val a2aResponse = nodeA2ARespondMessage(saveToStorage = true)
            a2aResponse(response.map { message ->
                message.toA2AMessage(MessageA2AMetadata(
                    messageId = UUID.randomUUID().toString(),
                    contextId = context.contextId
                ))
            })

            output(response)
        }
    }

    override suspend fun execute(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor
    ) {
        val userMessage = context.params.message

        // Convert A2A message to Koog format
        val koogMessage = userMessage.toKoogMessage()

        val agent = createAgent(context, eventProcessor)

        // Execute the agent - the A2A response is sent automatically via nodeA2ARespondMessage
        agent.execute(koogMessage as Message.User)
    }
}

// Server setup
suspend fun startChatServer() {
    val agentCard = AgentCard(
        name = "Koog Chat Assistant",
        description = "Simple chat assistant powered by Koog",
        version = "1.0.0",
        protocolVersion = "0.3.0",
        preferredTransport = TransportProtocol.JSONRPC,
        capabilities = AgentCapabilities(streaming = false, pushNotifications = false)
    )

    val server = A2AServer(agentExecutor = KoogChatExecutor(), agentCard = agentCard)
    val transport = HttpJSONRPCServerTransport(server)
    transport.start(engineFactory = CIO, port = 8080, path = "/chat", wait = true)
}
```

### Example 2: Document Analysis Agent with AIAgent Graph

Define a document analysis agent with more complex processing workflow:

```kotlin
class DocumentAnalysisExecutor : AgentExecutor {

    private fun createAnalysisAgent(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor
    ) = AIAgent(
        promptExecutor = MultiLLMPromptExecutor(
            LLMProvider.Anthropic to AnthropicLLMClient(ApiKeyService.anthropicApiKey)
        ),
        strategy = strategy("document-analysis") {
            // Multi-step analysis strategy
        },
        agentConfig = AIAgentConfig(
            prompt = prompt("analysis") {
                system { +"You are a document analysis expert. Provide structured analysis." }
            },
            model = AnthropicModels.Sonnet_4,
            maxAgentIterations = 3
        )
    ) {
        install(A2AAgentServer) {
            this.context = context
            this.eventProcessor = eventProcessor
        }

        graph {
            val userMessage = input<Message.User>()

            // Extract document content from message parts
            val documentContent = node { message ->
                message.content // Assuming content contains the document text
            }

            // Analyze document structure
            val structureAnalysis = nodeLLMPrompt(AnthropicModels.Sonnet_4) {
                system { +"Analyze the document structure and identify key sections." }
                user { +"Document to analyze:\n${documentContent.get()}" }
            }

            // Extract key insights
            val insights = nodeLLMPrompt(AnthropicModels.Sonnet_4) {
                system { +"Extract key insights and summarize main points." }
                user { +"Structure: ${structureAnalysis.get().content}\n\nDocument: ${documentContent.get()}" }
            }

            // Create comprehensive response
            val finalResponse = node { (structure, keyInsights) ->
                Message.Assistant(
                    content = "## Document Analysis\n\n**Structure:**\n${structure.content}\n\n**Key Insights:**\n${keyInsights.content}",
                    metaInfo = mapOf("analysis_type" to "document_analysis")
                )
            }

            // Send the analysis result through A2A
            val a2aResponse = nodeA2ARespondMessage(saveToStorage = true)
            a2aResponse(finalResponse.map { message ->
                message.toA2AMessage(MessageA2AMetadata(
                    messageId = UUID.randomUUID().toString(),
                    contextId = context.contextId
                ))
            })

            output(finalResponse)
        }
    }

    override suspend fun execute(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor
    ) {
        val userMessage = context.params.message

        // Convert A2A message to Koog format
        val koogMessage = userMessage.toKoogMessage()

        val agent = createAnalysisAgent(context, eventProcessor)

        // Execute the document analysis agent - response sent automatically
        agent.execute(koogMessage as Message.User)
    }
}
```

## Connecting Koog Agents to A2A Agents

### Example 1: Language Translation Service with Direct A2AClient

Connect to a specialized translation A2A agent from within your Koog application:

```kotlin
class KoogTranslationService {
    private val translationClient = A2AClient(
        transport = HttpJSONRPCClientTransport("https://translate-agent.com/a2a"),
        agentCardResolver = UrlAgentCardResolver(
            baseUrl = "https://translate-agent.com",
            path = "/.well-known/agent-card.json"
        )
    )

    suspend fun translateText(text: String, targetLanguage: String): String {
        // Connect to remote translation agent
        translationClient.connect()

        // Create translation request message
        val translationMessage = Message(
            messageId = UUID.randomUUID().toString(),
            role = Role.User,
            parts = listOf(TextPart("Translate to $targetLanguage: $text")),
            contextId = "translation-${UUID.randomUUID()}"
        )

        val request = Request(data = MessageSendParams(translationMessage))
        val response = translationClient.sendMessage(request)

        // Extract translated text
        return when (val event = response.data) {
            is Message -> event.parts
                .filterIsInstance<TextPart>()
                .joinToString(" ") { it.text }
            else -> "Translation failed - no response received"
        }
    }
}

// Usage in Koog executor - define agent first, then wrap in A2A
class MultilingualChatExecutor : AgentExecutor {
    private val translationService = KoogTranslationService()

    // Define the multilingual agent first with normal graph API
    private val multilingualAgent = AIAgent(
        promptExecutor = MultiLLMPromptExecutor(
            LLMProvider.Anthropic to AnthropicLLMClient(ApiKeyService.anthropicApiKey)
        ),
        strategy = singleRunStrategy(),
        agentConfig = agentConfig
    ) {
        graph {
            val userMessage = input<Message.User>()

            // Detect if translation is needed
            val needsTranslation = node { message ->
                message.content.contains("translate to")
            }

            // Extract translation parameters
            val translationParams = node { message ->
                if (needsTranslation.get()) {
                    val targetLang = extractTargetLanguage(message.content)
                    val textToTranslate = extractTextToTranslate(message.content)
                    Pair(targetLang, textToTranslate)
                } else null
            }

            // Handle translation or regular chat
            val response = node { (needsTranslation, params) ->
                if (needsTranslation && params != null) {
                    val translation = translationService.translateText(params.second, params.first)
                    Message.Assistant("Translation: $translation")
                } else {
                    // Regular chat processing would go here
                    Message.Assistant("I can help with translations. Try asking me to 'translate to [language]: [text]'")
                }
            }

            output(response)
        }
    }

    override suspend fun execute(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor
    ) {
        val userMessage = context.params.message

        // Convert A2A message to Koog format
        val koogMessage = userMessage.toKoogMessage()

        // Execute the multilingual agent
        val response = multilingualAgent.execute(koogMessage as Message.User)

        // Convert back to A2A format and send
        val a2aResponse = response.toA2AMessage(
            a2aMetadata = MessageA2AMetadata(
                messageId = UUID.randomUUID().toString(),
                contextId = context.contextId
            )
        )

        eventProcessor.sendMessage(a2aResponse)
    }
}
```

### Example 2: Research Assistant with AIAgent Client Feature

Use AIAgent's A2A client feature for coordinated research workflows:

```kotlin
suspend fun createResearchAssistant() = aiAgent {
    install(A2AAgentClient) {
        a2aClients = mapOf(
            "fact-checker" to A2AClient(
                transport = HttpJSONRPCClientTransport("https://factcheck.com/a2a"),
                agentCardResolver = UrlAgentCardResolver("https://factcheck.com", "/.well-known/agent-card.json")
            ),
            "web-search" to A2AClient(
                transport = HttpJSONRPCClientTransport("https://websearch.com/a2a"),
                agentCardResolver = UrlAgentCardResolver("https://websearch.com", "/.well-known/agent-card.json")
            )
        )
    }

    graph {
        val userQuery = input<Message.User>()

        // Analyze query to determine research strategy
        val researchPlan = nodeLLMPrompt(AnthropicModels.Sonnet_4) {
            system { +"Analyze the user query and create a research plan. Determine if fact-checking or web search is needed." }
            user { +userQuery.content }
        }

        // Perform web search if needed
        val searchResults = node { plan ->
            if (plan.content.contains("search")) {
                withA2AAgentClient {
                    val searchClient = a2aClientOrThrow("web-search")
                    val searchMessage = Message(
                        messageId = UUID.randomUUID().toString(),
                        role = Role.User,
                        parts = listOf(TextPart("Search for: ${userQuery.content}")),
                        contextId = "search-${UUID.randomUUID()}"
                    )
                    val request = Request(data = MessageSendParams(searchMessage))
                    searchClient.sendMessage(request).data
                }
            } else null
        }

        // Fact-check findings if needed
        val factCheckResults = node { (plan, searchData) ->
            if (plan.content.contains("fact-check") && searchData != null) {
                withA2AAgentClient {
                    val factChecker = a2aClientOrThrow("fact-checker")
                    val factCheckMessage = Message(
                        messageId = UUID.randomUUID().toString(),
                        role = Role.User,
                        parts = listOf(TextPart("Verify these facts: $searchData")),
                        contextId = "fact-check-${UUID.randomUUID()}"
                    )
                    val request = Request(data = MessageSendParams(factCheckMessage))
                    factChecker.sendMessage(request).data
                }
            } else null
        }

        // Synthesize final research report
        val finalReport = nodeLLMPrompt(AnthropicModels.Sonnet_4) {
            system { +"Synthesize the research findings into a comprehensive report." }
            user {
                +"Original query: ${userQuery.content}\n" +
                "Search results: ${searchResults.get()}\n" +
                "Fact-check results: ${factCheckResults.get()}"
            }
        }

        output(finalReport)
    }
}

// Usage
class ResearchAgentExecutor : AgentExecutor {
    private val researchAgent by lazy { runBlocking { createResearchAssistant() } }

    override suspend fun execute(
        context: RequestContext<MessageSendParams>,
        eventProcessor: SessionEventProcessor
    ) {
        val userMessage = context.params.message.toKoogMessage()
        val report = researchAgent.execute(userMessage as Message.User)

        val a2aResponse = report.toA2AMessage(
            a2aMetadata = MessageA2AMetadata(
                messageId = UUID.randomUUID().toString(),
                contextId = context.contextId
            )
        )

        eventProcessor.sendMessage(a2aResponse)
    }
}
```

## Message Conversion

Koog provides automatic conversion between A2A and Koog message formats:

### A2A to Koog

```kotlin
// Convert A2A message to Koog message
val koogMessage: Message = a2aMessage.toKoogMessage()

// Handles role conversion
when (a2aMessage.role) {
    Role.User -> Message.User(content, metaInfo, attachments)
    Role.Agent -> Message.Assistant(content, metaInfo, attachments)
}
```

### Koog to A2A

```kotlin
// Convert Koog message to A2A message
val a2aMessage: A2AMessage = koogMessage.toA2AMessage(
    a2aMetadata = MessageA2AMetadata(
        messageId = UUID.randomUUID().toString(),
        contextId = "context-id"
    )
)
```

## Advanced Integration Patterns

### Multi-Agent Collaboration

```kotlin
val collaborativeAgent = aiAgent {
    install(A2AAgentClient) {
        client("researcher") { /* researcher agent config */ }
        client("writer") { /* writer agent config */ }
        client("reviewer") { /* reviewer agent config */ }
    }

    graph {
        val userQuery = input<Message.User>()

        // Step 1: Research
        val researchRequest = node { query ->
            A2AClientRequest(
                agentId = "researcher",
                params = MessageSendParams(query.toA2AMessage())
            )
        }
        val researchResults = nodeA2AClientSendMessage()(researchRequest)

        // Step 2: Write draft
        val writeRequest = node { research ->
            val prompt = "Based on this research: $research, write a comprehensive answer"
            A2AClientRequest(
                agentId = "writer",
                params = MessageSendParams(Message(
                    messageId = UUID.randomUUID().toString(),
                    role = Role.User,
                    parts = listOf(TextPart(prompt)),
                    contextId = "writing-task"
                ))
            )
        }
        val draft = nodeA2AClientSendMessage()(writeRequest)

        // Step 3: Review and finalize
        val reviewRequest = node { draft ->
            A2AClientRequest(
                agentId = "reviewer",
                params = MessageSendParams(/* review request */)
            )
        }
        val finalResponse = nodeA2AClientSendMessage()(reviewRequest)

        output(finalResponse)
    }
}
```

### Hybrid Local-Remote Processing

```kotlin
val hybridAgent = aiAgent {
    install(A2AAgentClient) {
        client("specialist") { /* specialist agent */ }
    }

    graph {
        val userMessage = input<Message.User>()

        // Decide whether to handle locally or remotely
        val shouldUseSpecialist = node { message ->
            message.content.contains("complex") ||
            message.content.contains("specialized")
        }

        // Branch: local processing
        val localResponse = nodeLLMPrompt(AnthropicModels.Sonnet_4) {
            system { +"You are a general assistant" }
            user { +userMessage.content }
        }

        // Branch: remote specialist
        val remoteRequest = node { message ->
            A2AClientRequest(
                agentId = "specialist",
                params = MessageSendParams(message.toA2AMessage())
            )
        }
        val remoteResponse = nodeA2AClientSendMessage()(remoteRequest)

        // Select response based on condition
        val finalResponse = node { (useSpecialist, local, remote) ->
            if (useSpecialist) {
                (remote as Message).toKoogMessage()
            } else {
                local
            }
        }

        output(finalResponse)
    }
}
```

## Best Practices

### 1. Error Handling

```kotlin
override suspend fun execute(
    context: RequestContext<MessageSendParams>,
    eventProcessor: SessionEventProcessor
) {
    try {
        // Your Koog logic here
    } catch (e: Exception) {
        // Send error message through A2A
        val errorMessage = Message(
            messageId = UUID.randomUUID().toString(),
            role = Role.Agent,
            parts = listOf(TextPart("Error processing request: ${e.message}")),
            contextId = context.contextId,
            taskId = context.taskId
        )
        eventProcessor.sendMessage(errorMessage)
    }
}
```

### 2. Resource Management

```kotlin
class ResourceManagedKoogExecutor : AgentExecutor, Closeable {
    private val promptExecutor = MultiLLMPromptExecutor(/*...*/)

    override suspend fun execute(/*...*/) {
        // Use resources
    }

    override fun close() {
        promptExecutor.close()
    }
}
```

### 3. Configuration

```kotlin
// Use environment-specific configuration
val agentCard = AgentCard(
    name = System.getenv("AGENT_NAME") ?: "Koog Agent",
    url = System.getenv("AGENT_URL") ?: "http://localhost:8080/agent",
    // ... other configuration
)
```

The A2A-Koog integration provides a powerful foundation for building interoperable AI agent systems that can leverage both Koog's rich agent framework and the broader A2A ecosystem.

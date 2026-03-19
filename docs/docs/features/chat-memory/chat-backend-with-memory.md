# Chat backend with memory

A common pattern for the `ChatMemory` feature is a backend service
that manages agent interactions on behalf of clients.
Each HTTP request carries a session ID, the agent loads the matching conversation history,
generates and returns a response, and stores the updated chat history ready for the next interaction.

```kotlin
// --- Controller ---

@RestController
class ChatController(private val agentService: ChatAgentService) {
    @PostMapping("/chat")
    suspend fun chat(@RequestBody request: ChatRequest): ChatResponse {
        val reply = agentService.chat(request.sessionId, request.message)
        return ChatResponse(reply)
    }
}

// --- Service ---

@Service
class ChatAgentService(private val executor: SingleLLMPromptExecutor) {
    private val toolRegistry = ToolRegistry {
        // register your tools here
    }

    private val agent = AIAgent(
        promptExecutor = executor,
        llmModel = OpenAIModels.Chat.GPT4oMini,
        systemPrompt = "You are a helpful assistant.",
        toolRegistry = toolRegistry,
    ) {
        install(ChatMemory) {
            chatHistoryProvider = MyDatabaseProvider() // persistent storage
            windowSize(50)
        }
    }

    suspend fun chat(sessionId: String, message: String): String {
        return agent.run(message, sessionId)
    }
}
```

For a full guide on setting up Koog with Spring Boot, see the
[Spring Boot integration guide](../../spring-boot.md).

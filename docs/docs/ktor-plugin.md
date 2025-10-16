# Ktor Integration: Koog plugin

Koog fits naturally into your Ktor server allowing you to write server-side AI applications using ideomatic Kotlin API from both sides.

Install the Koog plugin once, configure your LLM providers in application.conf/YAML or in code, and then call agents right from your routes. No more wiring LLM clients across modules – your routes just request an agent and are ready to go.

## Overview

The `koog-ktor` module provides idiomatic Kotlin/Ktor integration for server‑side agentic development:

- Drop‑in Ktor plugin: `install(Koog)` in your Application
- First‑class support for OpenAI, Anthropic, Google, OpenRouter, DeepSeek, and Ollama
- Centralized configuration via YAML/CONF and/or code
- Agent setup with prompt, tools, features; simple extension functions for routes
- Direct LLM usage (execute, executeStreaming, moderate)
- JVM‑only Model Context Protocol (MCP) tools integration

## Add dependency

```kotlin
dependencies {
    implementation("ai.koog:koog-ktor:$koogVersion")
}
```

## Quick start

1) Configure providers (in `application.yaml` or `application.conf`)

Use nested keys under `koog.<provider>`. The plugin automatically picks them up.

```yaml
# application.yaml (Ktor config)
koog:
  openai:
    apikey: ${OPENAI_API_KEY}
    baseUrl: https://api.openai.com
  anthropic:
    apikey: ${ANTHROPIC_API_KEY}
    baseUrl: https://api.anthropic.com
  google:
    apikey: ${GOOGLE_API_KEY}
    baseUrl: https://generativelanguage.googleapis.com
  openrouter:
    apikey: ${OPENROUTER_API_KEY}
    baseUrl: https://openrouter.ai
  deepseek:
    apikey: ${DEEPSEEK_API_KEY}
    baseUrl: https://api.deepseek.com
  # Ollama is enabled when any koog.ollama.* key exists
  ollama:
    enable: true
    baseUrl: http://localhost:11434
```

Optional: configure fallback used by direct LLM calls when a requested provider is not configured.

```yaml
koog:
  llm:
    fallback:
      provider: openai
      # see Model identifiers section below
      model: openai.chat.gpt4_1
```

2) Install the plugin and define routes

```kotlin
fun Application.module() {
    install(Koog) {
        // You can also configure providers programmatically (see below)
    }

    routing {
        route("/ai") {
            post("/chat") {
                val userInput = call.receiveText()
                // Create and run a default single‑run agent using a specific model
                val output = aiAgent(
                    strategy = reActStrategy(),
                    model = OpenAIModels.Chat.GPT4_1,
                    input = userInput
                )
                call.respond(HttpStatusCode.OK, output)
            }
        }
    }
}
```

Notes

- aiAgent requires a concrete model (LLModel) – choose per‑route, per‑use.
- For lower‑level LLM access, use llm() (PromptExecutor) directly.

## Direct LLM usage from routes

```kotlin
post("/llm-chat") {
    val userInput = call.receiveText()

    val messages = llm().execute(
        prompt("chat") {
            system("You are a helpful assistant that clarifies questions")
            user(userInput)
        },
        GoogleModels.Gemini2_5Pro
    )

    // Join all assistant messages into a single string
    val text = messages.joinToString(separator = "") { it.content }
    call.respond(HttpStatusCode.OK, text)
}
```

Streaming

```kotlin
get("/stream") {
    val flow = llm().executeStreaming(
        prompt("streaming") { user("Stream this response, please") },
        OpenRouterModels.GPT4o
    )

    // Example: buffer and send as one chunk
    val sb = StringBuilder()
    flow.collect { chunk -> sb.append(chunk) }
    call.respondText(sb.toString())
}
```

Moderation

```kotlin
post("/moderated-chat") {
    val userInput = call.receiveText()

    val moderation = llm().moderate(
        prompt("moderation") { user(userInput) },
        OpenAIModels.Moderation.Omni
    )

    if (moderation.isHarmful) {
        call.respond(HttpStatusCode.BadRequest, "Harmful content detected")
        return@post
    }

    val output = aiAgent(
        strategy = reActStrategy(),
        model = OpenAIModels.Chat.GPT4_1,
        input = userInput
    )
    call.respond(HttpStatusCode.OK, output)
}
```

## Programmatic configuration (in code)

All providers and agent behavior can be configured via install(Koog) {}.

```kotlin
install(Koog) {
    llm {
        openAI(apiKey = System.getenv("OPENAI_API_KEY") ?: "") {
            baseUrl = "https://api.openai.com"
            timeouts { // Default values shown below
                requestTimeout = 15.minutes
                connectTimeout = 60.seconds
                socketTimeout = 15.minutes
            }
        }
        anthropic(apiKey = System.getenv("ANTHROPIC_API_KEY") ?: "")
        google(apiKey = System.getenv("GOOGLE_API_KEY") ?: "")
        openRouter(apiKey = System.getenv("OPENROUTER_API_KEY") ?: "")
        deepSeek(apiKey = System.getenv("DEEPSEEK_API_KEY") ?: "")
        ollama { baseUrl = "http://localhost:11434" }

        // Optional fallback used by PromptExecutor when a provider isn’t configured
        fallback {
            provider = LLMProvider.OpenAI
            model = OpenAIModels.Chat.GPT4_1
        }
    }

    agentConfig {
        // Provide a reusable base prompt for your agents
        prompt(name = "agent") {
            system("You are a helpful server‑side agent")
        }

        // Limit runaway tools/loops
        maxAgentIterations = 10

        // Register tools available to agents by default
        registerTools {
            // tool(::yourTool) // see Tools Overview for details
        }

        // Install agent features (tracing, etc.)
        // install(OpenTelemetry) { /* ... */ }
    }
}
```

## Model identifiers in config (fallback)

When configuring llm.fallback in YAML/CONF, use these identifier formats:

- OpenAI: openai.chat.gpt4_1, openai.reasoning.o3, openai.costoptimized.gpt4_1mini, openai.audio.gpt4oaudio, openai.moderation.omni
- Anthropic: anthropic.sonnet_3_7, anthropic.opus_4, anthropic.haiku_3_5
- Google: google.gemini2_5pro, google.gemini2_0flash001
- OpenRouter: openrouter.gpt4o, openrouter.gpt4, openrouter.claude3sonnet
- DeepSeek: deepseek.deepseek-chat, deepseek.deepseek-reasoner
- Ollama: ollama.meta.llama3.2, ollama.alibaba.qwq:32b, ollama.groq.llama3-grok-tool-use:8b

Note

- For OpenAI you must include the category (chat, reasoning, costoptimized, audio, embeddings, moderation).
- For Ollama, both ollama.model and ollama.<maker>.<model> are supported.

## MCP tools (JVM‑only)

On JVM you can add tools from an MCP server to your agent tool registry:

```kotlin
install(Koog) {
    agentConfig {
        mcp {
            // Register via SSE
            sse("https://your-mcp-server.com/sse")

            // Or register via spawned process (stdio transport)
            // process(Runtime.getRuntime().exec("your-mcp-binary ..."))

            // Or from an existing MCP client instance
            // client(existingMcpClient)
        }
    }
}
```
## Why Koog + Ktor?

- Kotlin‑first, type‑safe development of agents in your server
- Centralized config with clean, testable route code
- Use the right model per‑route, or fall back automatically for direct LLM calls
- Production‑ready features: tools, moderation, streaming, and tracing

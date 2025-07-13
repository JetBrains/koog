# Module koog-ktor-plugin

Ktor server integration for the Koog AI agents framework.

## Overview

The `koog-ktor-plugin` module provides seamless integration between the Koog AI agents framework and Ktor server applications. It includes:

- A Ktor plugin for easy installation and configuration
- Support for multiple LLM providers (OpenAI, Anthropic, Google, OpenRouter, Ollama)
- Agent configuration with tools, features, and prompt customization
- Extension functions for routes to interact with LLMs and agents
- Content moderation capabilities
- JVM-specific support for Model Context Protocol (MCP) integration

## Using in your project

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("ai.koog:koog-ktor-plugin:$koogVersion")
}
```

## Basic Usage

Install and configure the plugin in your Ktor application:

```kotlin
fun Application.module() {
    install(Koog) {
        // Configure LLM providers
        llm {
            openAI(apiKey = "your-openai-api-key") {
                baseUrl = "https://api.openai.com"
            }
            
            // Optional: Configure other providers
            anthropic(apiKey = "your-anthropic-api-key")
            ollama { baseUrl = "http://localhost:11434" }
            google(apiKey = "your-google-api-key")
            openRouter(apiKey = "your-openrouter-api-key")
        }
        
        // Configure agent
        agent {
            // Register tools
            registerTools {
                // Add tools using reflection
                tool(::yourToolFunction)
            }
            
            // Configure prompt
            prompt {
                system("You are a helpful assistant")
            }
            
            // JVM-specific: Configure MCP integration
            mcp {
                sse("your-mcp-server-url")
            }
        }
    }
    
    // Use in routes
    routing {
        route("/ai") {
            post("/chat") {
                val userInput = call.receive<String>()
                
                // Use agent to respond
                call.agentRespond(userInput)
            }
        }
    }
}
```

## Advanced Usage

### Content Moderation

```kotlin
post("/moderated-chat") {
    val userInput = call.receive<String>()
    
    // Moderate content
    val isHarmful = moderateWithLLM(OpenAIModels.Moderation.Omni) {
        user(userInput)
    }.isHarmful
    
    if (isHarmful) {
        call.respond(HttpStatusCode.BadRequest, "Harmful content detected")
        return@post
    }
    
    // Process with agent
    call.agentRespond(userInput)
}
```

### Direct LLM Interaction

```kotlin
post("/llm-chat") {
    val userInput = call.receive<String>()
    
    // Ask LLM directly
    val response = askLLM(OllamaModels.Meta.LLAMA_3_2) {
        system("You are a helpful assistant")
        user(userInput)
    }.single() as Message.Assistant
    
    call.respond(response.content)
}
```

### Custom Agent Strategies

```kotlin
post("/custom-agent") {
    val userInput = call.receive<String>()
    
    // Use custom strategy
    call.agentRespond(userInput, strategy = reActStrategy())
}
```

## Configuration Options

### LLM Configuration

#### Programmatic Configuration

Configure multiple LLM providers with custom settings in code:

```kotlin
llm {
    openAI(apiKey = "your-openai-api-key") {
        baseUrl = "https://api.openai.com"
        timeouts {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 30000
        }
    }
    
    // Set fallback LLM
    fallback {
        provider = LLMProvider.Ollama
        model = OllamaModels.Meta.LLAMA_3_2
    }
}
```

#### YAML/CONF Configuration

You can also configure LLM providers using YAML or CONF files. The plugin will automatically read the configuration from the application's configuration file:

```yaml
# application.yaml or application.conf
koog:
  openai:
    apikey: "your-openai-api-key"
    baseUrl: "https://api.openai.com"
    timeout:
      requestTimeoutMillis: 30000
      connectTimeoutMillis: 10000
      socketTimeoutMillis: 30000
  
  anthropic:
    apikey: "your-anthropic-api-key"
    baseUrl: "https://api.anthropic.com"
    timeout:
      requestTimeoutMillis: 30000
  
  google:
    apikey: "your-google-api-key"
    baseUrl: "https://generativelanguage.googleapis.com"
  
  openrouter:
    apikey: "your-openrouter-api-key"
    baseUrl: "https://openrouter.ai"
  
  ollama:
    baseUrl: "http://localhost:11434"
    timeout:
      requestTimeoutMillis: 60000
```

When using configuration files, you can still provide programmatic configuration that will override the settings from the file:

```kotlin
install(Koog) {
    // Optional: Override or add to configuration from YAML/CONF
    llm {
        // This will override the API key from the configuration file
        openAI(apiKey = System.getenv("OPENAI_API_KEY") ?: "override-from-code")
    }
    
    // Rest of your configuration...
}
```

### Agent Configuration

Configure agent behavior, tools, and features:

```kotlin
agent {
    // Set model
    model = OpenAIModels.GPT4.Turbo
    
    // Set max iterations
    maxAgentIterations = 10
    
    // Register tools
    registerTools {
        tool(::searchTool)
        tool(::calculatorTool)
    }
    
    // Configure prompt
    prompt {
        system("You are a helpful assistant specialized in...")
    }
    
    // Install features
    install(OpenTelemetry) {
        // Configure feature
    }
}
```

### JVM-specific MCP Configuration

Configure Model Context Protocol integration (JVM only):

```kotlin
agent {
    mcp {
        // Use Server-Sent Events
        sse("https://your-mcp-server.com/sse")
        
        // Or use process
        process(yourMcpProcess)
        
        // Or use existing client
        client(yourMcpClient)
    }
}
```
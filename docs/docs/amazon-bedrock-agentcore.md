---
status: beta
---

# Amazon Bedrock AgentCore

--8<-- "versioning-snippets.md:beta"

Koog provides integrations for running agents with Amazon Bedrock AgentCore services.

## Amazon Bedrock AgentCore Runtime

The `koog-bedrock-agentcore-runtime` module provides a Ktor route installer that exposes a Koog agent through the
[Amazon Bedrock AgentCore Runtime](https://docs.aws.amazon.com/bedrock-agentcore/latest/devguide/runtime.html) HTTP
contract. It installs the following endpoints relative to the Ktor route on which it is configured:

- `POST /invocations` processes agent requests.
- `GET /ping` reports agent health and background activity.

The module supports typed JSON handlers as well as text, binary, multipart, and streaming payloads. Invocation handlers
run in a Ktor `RoutingContext`, so they can use Koog routing extensions such as `aiAgent()` when the `koog-ktor` plugin
is installed.

### Add the dependency

Add the AgentCore Runtime module to your Gradle build:

```kotlin
dependencies {
    implementation("ai.koog:koog-bedrock-agentcore-runtime:$koogVersion")
}
```

The module requires JVM 17 or later, Kotlin 2.x, and Ktor 3.x.

### Install the Runtime routes

The following example installs Koog and Ktor content negotiation, then exposes a typed JSON invocation handler:

```kotlin
import ai.koog.agentcore.runtime.agentCoreRuntime
import ai.koog.agentcore.runtime.handle
import ai.koog.ktor.Koog
import ai.koog.ktor.aiAgent
import ai.koog.ktor.llm
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
data class InvocationRequest(val prompt: String)

@Serializable
data class InvocationResponse(val answer: String)

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }

    install(Koog) {
        llm {
            bedrock()
        }
    }

    routing {
        agentCoreRuntime {
            handle<InvocationRequest, InvocationResponse> { request, context ->
                val sessionId = context.getHeader("X-Amzn-Bedrock-AgentCore-Runtime-Session-Id")
                val answer = aiAgent(
                    input = request.prompt,
                    model = BedrockModels.AmazonNovaMicro,
                )
                InvocationResponse(answer)
            }
        }
    }
}
```

Typed handlers delegate request deserialization and response serialization to Ktor's `ContentNegotiation` plugin. The
host application must install a converter for the media types it accepts, such as `json()` for JSON requests and
responses. The server engine, port, and other application plugins also remain under the host application's control.

### Handle different payload types

For non-JSON payloads or multimodal responses, configure the unified `handler`. It receives an `InvocationInput` and an
`AgentCoreContext`, and returns an `InvocationResult`:

```kotlin
routing {
    agentCoreRuntime {
        handler = { input, context ->
            when (input) {
                is InvocationInput.Text -> InvocationResult.Text(
                    aiAgent(input.body, model = BedrockModels.AmazonNovaMicro)
                )
                is InvocationInput.Binary -> InvocationResult.Binary(input.bytes, input.contentType)
                is InvocationInput.Stream -> InvocationResult.Text("Received a streamed request")
                is InvocationInput.Multipart -> InvocationResult.Text("Received multipart data")
            }
        }
    }
}
```

The unified handler supports:

- `InvocationResult.Text` for one-shot text output with `Accept`-based content negotiation.
- `InvocationResult.Binary` for raw image, audio, video, or document bytes with an explicit content type.
- `InvocationResult.TextStream` for a `Flow<String>` emitted as immediately flushed `text/event-stream` events.
- `InvocationResult.BinaryStream` for raw streamed chunks with a caller-selected content type.

Streaming responses are written directly and do not require Ktor's `SSE` plugin.

### Configure request handling

`AgentCoreRuntimeConfig` provides the following options:

| Option | Description | Default |
|---|---|---|
| `handler` | Unified handler used unless a typed `handle<I, O>` handler is registered. | Not set |
| `binaryStreamThresholdBytes` | Binary bodies above this size, or without `Content-Length`, are exposed as `InvocationInput.Stream`. | 1 MiB |
| `maxRequestBytes` | Rejects requests with a declared `Content-Length` above the limit with HTTP 413. | 100 MiB |
| `handlerTimeoutMillis` | Returns HTTP 504 when the handler exceeds this timeout. A non-positive value disables the timeout. | `0` |
| `pingService` | Custom health service for the `/ping` endpoint. | Task-aware default service |
| `taskTracker` | Tracker exposed through `AgentCoreContext` and used by the default health service. | New `AgentCoreTaskTracker` |

Requests without a `Content-Length` header are not pre-checked against `maxRequestBytes`; the underlying server engine's
limits still apply.

### Monitor health and background tasks

The `/ping` endpoint returns:

- `Healthy` with HTTP 200 when the agent has no active background tasks.
- `HealthyBusy` with HTTP 200 while the `AgentCoreTaskTracker` reports active work.
- `Unhealthy` with HTTP 503 when the health check detects a problem.

Use the tracker available from `AgentCoreContext` when starting long-running background work. This keeps the Runtime
informed that the agent is still active. You can replace the default behavior by assigning a custom
`AgentCorePingService` to `pingService`.

Rate limiting is also controlled by the host application. Install Ktor's `RateLimit` plugin globally or wrap the
`agentCoreRuntime` route in a named `rateLimit` block to apply the desired policy.

## Amazon Bedrock AgentCore Memory

Koog integrates with [Amazon Bedrock AgentCore Memory](https://docs.aws.amazon.com/bedrock-agentcore/latest/devguide/memory.html)
in two ways:

- The `agents-features-chat-history-aws` module persists conversational history as AgentCore events.
- The `agents-features-longterm-memory-aws` module retrieves records produced by AgentCore memory strategies and adds
  them to the agent prompt.

Both integrations require JVM 17 or later and an AgentCore memory resource. Configure AWS credentials and a region
through the standard AWS SDK credential and region provider chains.

### Add the dependencies

Add one or both Memory integration modules to your Gradle build:

```kotlin
dependencies {
    implementation("ai.koog:agents-features-chat-history-aws:$koogVersion")
    implementation("ai.koog:agents-features-longterm-memory-aws:$koogVersion")
}
```

Both modules expose the AWS SDK for Kotlin `BedrockAgentCoreClient` used by their public APIs. Long-term memory also
exposes `BedrockAgentCoreControlClient` for memory strategy discovery.

### Persist conversational history

`AgentcoreChatHistoryProvider` implements Koog's `ChatHistoryProvider` with the AgentCore `createEvent` and
`listEvents` APIs. Install it through the `ChatMemory` feature:

```kotlin
import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.features.chathistory.aws.AgentcoreChatHistoryProvider
import aws.sdk.kotlin.services.bedrockagentcore.BedrockAgentCoreClient

val agentCoreClient = BedrockAgentCoreClient { region = "us-west-2" }
val chatHistoryProvider = AgentcoreChatHistoryProvider(
    client = agentCoreClient,
    memoryId = "memory-id",
)

val agent = AIAgent(/* ... */) {
    install(ChatMemory) {
        chatHistoryProvider = chatHistoryProvider
    }
}

val result = agent.run(
    agentInput = "Remember that I prefer window seats.",
    conversationId = "user-123:trip-456",
)
```

A conversation ID can be either `actorId:sessionId` or just `actorId`. When the session part is omitted, the provider
uses `default-session` unless you set `defaultSession` in its constructor.

The provider stores plain-text `Message.User` and `Message.Assistant` messages. Messages loaded from AgentCore carry
their event ID in metadata, allowing the provider to store only new messages when the full history is saved again.
System, tool, reasoning, and non-text content is skipped by default; set `ignoreUnsupportedValues = false` to reject
it instead. Use `pageSize` to control `listEvents` pagination and `totalEventsLimit` to cap the number of loaded events.

### Retrieve long-term memory

`LongTermMemory` can query one or more AgentCore memory strategies before each LLM request. The `agentcore` DSL creates
a composite retrieval, so a single block can combine multiple strategy types and namespace scopes:

```kotlin
import ai.koog.agents.features.longtermmemory.aws.dsl.agentcore
import ai.koog.agents.longtermmemory.feature.LongTermMemory

val agent = AIAgent(/* ... */) {
    install(LongTermMemory) {
        retrieval {
            agentcore(agentCoreClient, memoryId = "memory-id") {
                semantic(
                    strategyId = "semantic-strategy-id",
                    actorId = "user-123",
                    topK = 5,
                )
                userPreferences(
                    strategyId = "preference-strategy-id",
                    actorId = "user-123",
                    limit = 20,
                )
                summary(
                    strategyId = "summary-strategy-id",
                    actorId = "user-123",
                    sessionId = "trip-456",
                    topK = 3,
                )
            }
        }
    }
}
```

The DSL provides the following helpers:

| Helper | AgentCore strategy | Namespace scope | Retrieval |
|---|---|---|---|
| `semantic` | Semantic memory | Actor | Similarity search |
| `userPreferences` | User preferences | Actor | Record listing |
| `summary` | Summarization | Actor and session | Similarity search |
| `episodes` | Episodic episodes | Actor and session | Similarity search |
| `reflections` | Episodic reflections | Actor | Similarity search |
| `episodic` | Episodes and reflections | Both scopes | Composite similarity search |

By default, namespaces follow AWS's documented layout:
`/strategies/{strategyId}/actors/{actorId}/` for actor-scoped memory and
`/strategies/{strategyId}/actors/{actorId}/sessions/{sessionId}/` for session-scoped memory. If the memory resource
uses custom namespace templates, assign an `AgentcoreNamespaceResolver` in the `agentcore` block.

The default `AgentcorePromptAugmenter` places semantic, preference, episode, and reflection records in the system
message. Summary records are appended to the latest user message. Set `augmenter` in the block to use another Koog
`PromptAugmenter`.

### Discover configured memory strategies

When strategy IDs or namespace templates should not be hard-coded, use `AgentcoreStrategyDiscovery` with an AWS
`BedrockAgentCoreControlClient`, then pass its result to `agentcoreDiscovered`. The discovery DSL configures all
supported strategies returned for the memory resource and lets you override retrieval limits, scores, filters, and
namespace patterns or exclude individual strategies. A `sessionId` is required when the discovered set includes a
summary or episodic strategy.

AgentCore creates long-term records asynchronously from stored events. An event written by `ChatMemory` might therefore
not be available to `LongTermMemory` immediately.

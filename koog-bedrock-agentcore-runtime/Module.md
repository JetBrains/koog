# Module koog-bedrock-agentcore-runtime

A Ktor route installer that implements the [Amazon Bedrock AgentCore Runtime](https://docs.aws.amazon.com/bedrock-agentcore/latest/devguide/runtime.html)
HTTP contract, so a Koog agent can be deployed and invoked as an AgentCore Runtime workload.

The route installer exposes the two endpoints required by the runtime:

- `POST /invocations` — the invocation endpoint, backed by a user-supplied handler. Supports JSON-in/JSON-out
  (typed `handle<I, O>`) as well as raw text, binary, multipart, and streaming payloads (unified `handler`).
- `GET /ping` — a health check that reports `Healthy`, `HealthyBusy` (while background work tracked by
  `AgentCoreTaskTracker` is in progress), or `Unhealthy`.

Additional capabilities:

- Optional per-request handler timeout (`handlerTimeoutMillis`, returns **504**).
- Request body size limit (`maxRequestBytes`, returns **413**).
- Streaming threshold to expose large/length-less bodies as `InvocationInput.Stream`.
- Compatibility with application- or route-level Ktor rate limiting.
- Custom health checks through `AgentCorePingService`.

## Usage

The handler runs with a Ktor `RoutingContext` receiver, so it composes directly with the
[`koog-ktor`](../koog-ktor) plugin: install the `Koog` plugin to configure the LLM (here, Amazon Bedrock),
then forward the AgentCore invocation request into `aiAgent`.

```kotlin
import ai.koog.agentcore.runtime.agentCoreRuntime
import ai.koog.agentcore.runtime.handle
import ai.koog.ktor.Koog
import ai.koog.ktor.aiAgent
import ai.koog.ktor.bedrock
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable

@Serializable
data class InvocationRequest(val prompt: String)

@Serializable
data class InvocationResponse(val answer: String)

fun Application.module() {
    // koog-ktor: configure the Amazon Bedrock LLM client (see BedrockConfig.kt)
    install(Koog) {
        bedrock(apiKey = System.getenv("AWS_BEARER_TOKEN_BEDROCK")) {
            region = "us-east-1"
        }
    }

    // koog-bedrock-agentcore-runtime: expose /invocations and /ping
    routing {
        agentCoreRuntime {
            handle<InvocationRequest, InvocationResponse> { request, ctx ->
                // `this` is RoutingContext, so aiAgent(...) from koog-ktor is available.
                // ctx exposes AgentCore headers (session id, user id, etc.).
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

For non-JSON payloads, set the unified `handler` instead of the typed `handle<I, O>` and return an
`InvocationResult` (`Text`, `Binary`, `TextStream`, or `BinaryStream`).

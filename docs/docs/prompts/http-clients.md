# HTTP clients

Every LLM client in Koog expects a [`KoogHttpClient`](api:http-client-core::ai.koog.http.client.KoogHttpClient) — the abstract HTTP contract the framework uses to talk to providers. You hand one in at construction.

You can build that `KoogHttpClient` yourself, but it's real work: each provider has its own base URL, auth header shape, content-type, and SSE conventions. Getting all of that right per provider is exactly what [`KoogHttpClient.Factory`](api:http-client-core::ai.koog.http.client.KoogHttpClient.Factory) exists to spare you. You pass in a `Factory` and the provider client calls `Factory.create(...)` with the parameters that fit its API.

Four backend factories ship out of the box — Ktor, the JDK `HttpClient`, OkHttp, and Spring's `WebClient` — and you can implement your own.

## How it works

One factory works for any provider: pick a backend once and use it across clients.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.http.client.ktor.KtorKoogHttpClient
    import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
    import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
    import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
    import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
    -->
    ```kotlin
    fun main() {
        val factory = KtorKoogHttpClient.Factory()

        val openai = OpenAILLMClient(
            apiKey = System.getenv("OPENAI_API_KEY"),
            settings = OpenAIClientSettings(),
            httpClientFactory = factory,
        )

        val anthropic = AnthropicLLMClient(
            apiKey = System.getenv("ANTHROPIC_API_KEY"),
            settings = AnthropicClientSettings(),
            httpClientFactory = factory,
        )
    }
    ```
    <!--- KNIT example-http-clients-01.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    import ai.koog.http.client.ktor.KtorKoogHttpClient;
    import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings;
    import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient;
    import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings;
    import ai.koog.prompt.executor.clients.openai.OpenAILLMClient;

    KtorKoogHttpClient.Factory factory = new KtorKoogHttpClient.Factory();

    OpenAILLMClient openai = new OpenAILLMClient(
        System.getenv("OPENAI_API_KEY"),
        new OpenAIClientSettings(),
        factory
    );

    AnthropicLLMClient anthropic = new AnthropicLLMClient(
        System.getenv("ANTHROPIC_API_KEY"),
        new AnthropicClientSettings(),
        factory
    );
    ```
    <!--- KNIT example-http-clients-java-01.java -->

## Supported HTTP client flavors

| Module                                                                  | Notes                                              |
|-------------------------------------------------------------------------|----------------------------------------------------|
| [`http-client-ktor`](api:http-client-ktor::)                            | The only backend usable from non-JVM targets.      |
| [`http-client-java`](api:http-client-java::)                            | Wraps the JDK 11+ `java.net.http.HttpClient`.      |
| [`http-client-okhttp`](api:http-client-okhttp::)                        | Backed by OkHttp. Android-friendly.                |
| [`http-client-spring-webclient`](api:http-client-spring-webclient::)    | Backed by Spring `WebClient`.                      |

## Convenience APIs and factory auto-discovery

On JVM and Android, you can construct each LLM client without passing a factory explicitly.

Behind the scenes, [`HttpClientFactoryResolver`](api:http-client-core::ai.koog.http.client.HttpClientFactoryResolver) uses `java.util.ServiceLoader` to resolve `KoogHttpClient.Factory` from the runtime classpath:

- Every backend module provides a `ServiceLoader` registration.
- Resolution succeeds only when exactly one factory is visible on the runtime classpath.
- `prompt-executor-llms-all` declares `http-client-ktor` as a `runtimeOnly` dependency, so you get Ktor by default without compile-time exposure to that module.
- `simple<Provider>Executor(apiKey)` and `PromptExecutorBuilder.<provider>(apiKey)` use the same resolution path.

=== "Kotlin"

    <!--- INCLUDE
    import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
    import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
    -->
    ```kotlin
    fun main() {
        val apiKey = System.getenv("OPENAI_API_KEY")

        val client = OpenAILLMClient(apiKey)
        val executor = simpleOpenAIExecutor(apiKey)
    }
    ```
    <!--- KNIT example-http-clients-02.kt -->

=== "Java"

    <!--- INCLUDE
    /**
    -->
    <!--- SUFFIX
    **/
    -->
    ```java
    import static ai.koog.prompt.executor.clients.openai.OpenAIClientFactory.openAIClient;
    import static ai.koog.prompt.executor.llms.all.SimplePromptExecutors.simpleOpenAIExecutor;

    String apiKey = System.getenv("OPENAI_API_KEY");

    OpenAILLMClient client = openAIClient(apiKey);
    PromptExecutor executor = simpleOpenAIExecutor(apiKey);
    ```
    <!--- KNIT example-http-clients-java-02.java -->

Auto-discovery is not supported on KMP at the moment, so the convenience methods are not available outside the JVM either. From `commonMain`, pass a `Factory` explicitly.

### Auto-discovery gotchas

- **Zero backends on the runtime classpath** → `IllegalStateException` on first resolution. Add a backend module to the runtime classpath, or pass a `Factory` explicitly.
- **Two or more backends** → same exception; the message names the providers it found. Exclude all but one with Gradle (`exclude(module = "http-client-ktor")` on the offending dependency) or pass a `Factory` explicitly at the call site.

## Custom backends

Any class implementing `KoogHttpClient.Factory` works. To make it auto-discoverable on the JVM, register it as a `ServiceLoader` provider:

```
src/main/resources/META-INF/services/ai.koog.http.client.KoogHttpClient$Factory
```

The file contains a single line: the fully qualified name of your factory class. The literal `$` (separator for the nested `Factory` class) is correct — the file is `KoogHttpClient$Factory`, not `KoogHttpClient.Factory`.

If you don't want auto-discovery, skip the registration and pass your factory explicitly everywhere.

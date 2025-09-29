rootProject.name = "koog"

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

include(":agents:agents-core")
include(":agents:agents-ext")

include(":agents:agents-features:agents-features-debugger")
include(":agents:agents-features:agents-features-event-handler")
include(":agents:agents-features:agents-features-memory")
include(":agents:agents-features:agents-features-opentelemetry")
include(":agents:agents-features:agents-features-sql")
include(":agents:agents-features:agents-features-trace")
include(":agents:agents-features:agents-features-tokenizer")
include(":agents:agents-features:agents-features-snapshot")

include(":agents:agents-mcp")
include(":agents:agents-mcp-server")
include(":agents:agents-test")
include(":agents:agents-tools")
include(":agents:agents-utils")

include(":integration-tests")

include(":koog-agents")

include(":prompt:prompt-cache:prompt-cache-files")
include(":prompt:prompt-cache:prompt-cache-model")
include(":prompt:prompt-cache:prompt-cache-redis")

include(":prompt:prompt-executor:prompt-executor-cached")

include(":prompt:prompt-executor:prompt-executor-clients")
include(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-anthropic-client")
include(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-bedrock-client")
include(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-deepseek-client")
include(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-google-client")
include(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-ollama-client")
include(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client")
include(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openai-client-base")
include(":prompt:prompt-executor:prompt-executor-clients:prompt-executor-openrouter-client")

include(":prompt:prompt-executor:prompt-executor-llms")
include(":prompt:prompt-executor:prompt-executor-llms-all")
include(":prompt:prompt-executor:prompt-executor-model")

include(":prompt:prompt-llm")
include(":prompt:prompt-markdown")
include(":prompt:prompt-model")
include(":prompt:prompt-structure")
include(":prompt:prompt-tokenizer")
include(":prompt:prompt-xml")

include(":embeddings:embeddings-base")
include(":embeddings:embeddings-llm")

include(":rag:rag-base")
include(":rag:vector-storage")

include(":a2a:a2a-core")
include(":a2a:a2a-server")
include(":a2a:a2a-client")
include(":a2a:a2a-transport:a2a-transport-core-jsonrpc")
include(":a2a:a2a-transport:a2a-transport-server-jsonrpc-http")
include(":a2a:a2a-transport:a2a-transport-client-jsonrpc-http")
include(":a2a:a2a-transport:a2a-transport-core-rest")
include(":a2a:a2a-transport:a2a-transport-server-rest")
include(":a2a:a2a-transport:a2a-transport-client-rest")

include(":koog-spring-boot-starter")

include(":koog-ktor")
include(":docs")

include(":test-utils")
include(":utils")

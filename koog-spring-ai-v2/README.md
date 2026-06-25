# koog-spring-ai-v2

Spring AI 2 adapter layer for the Koog AI Agent Framework.

## How it differs from `koog-spring-boot-starter`

| | `koog-spring-boot-starter` | `koog-spring-ai` |
|---|---|---|
| **LLM transport** | Koog's own HTTP clients (one per provider: OpenAI, Anthropic, Google, etc.) | Delegates to Spring AI's `ChatModel` / `EmbeddingModel` — any provider that Spring AI supports works automatically |
| **Configuration** | `ai.koog.*` properties per provider | Standard `spring.ai.*` properties managed by Spring AI starters |
| **When to use** | You want Koog to manage LLM connections directly | You already use Spring AI for model access and want to plug Koog's agent orchestration on top |

Both starters are independent — pick one based on how you prefer to manage LLM connectivity.

## Submodules

| Module | Spring AI interfaces | Koog interfaces | Docs |
|---|---|---|---|
| `koog-spring-ai-v2-starter-model-chat` | `ChatModel`, `ModerationModel` | `LLMClient`, `PromptExecutor` | [Module.md](koog-spring-ai-v2-starter-model-chat/Module.md) |
| `koog-spring-ai-v2-starter-model-embedding` | `EmbeddingModel` | `LLMEmbeddingProvider` | [Module.md](koog-spring-ai-v2-starter-model-embedding/Module.md) |
| `koog-spring-ai-v2-starter-chat-memory` | `ChatMemoryRepository` | `ChatHistoryProvider` | [Module.md](koog-spring-ai-v2-starter-chat-memory/Module.md) |
| `koog-spring-ai-v2-starter-vector-store` | `VectorStore` | `WriteStorage`, `SearchStorage`, `FilteringDeletionStorage` | [Module.md](koog-spring-ai-v2-starter-vector-store/Module.md) |

Each submodule is a fully independent Spring Boot starter with its own auto-configuration, configuration properties, and dispatcher management. See the linked `Module.md` for usage details, configuration reference, and examples.

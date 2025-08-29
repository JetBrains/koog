# 0.4.1

> Published 28 Aug 2025

## Bug Fixes

Fixed iOS target publication

# 0.4.0

> Published 27 Aug 2025

## Major Features

- **Integration with Observability Tools**:
  - **Langfuse Integration**: Span adapters for Langfuse client, including open telemetry and graph visualisation ([KG-217](https://youtrack.jetbrains.com/issue/KG-217), [KG-223](https://youtrack.jetbrains.com/issue/KG-223))
  - **W&B Weave Integration**: Span adapters for W&B Weave open telemetry and observability ([KG-217](https://youtrack.jetbrains.com/issue/KG-217), [KG-218](https://youtrack.jetbrains.com/issue/KG-218))
- **Ktor Integration**: First-class Ktor support via the "Koog" Ktor plugin to register and run agents in Ktor applications (#422).
- **iOS Target Support**: Multiplatform expanded with native iOS targets, enabling agents to run on Apple platforms (#512).
- **Upgraded Structured Output**: Refactored structured output API to be more flexible and add built-in/native provider support for OpenAI and Google, reducing prompt boilerplate and improving validation (#443).
- **GPT5 and Custom LLM Parameters Support**: Now GPT5 is available together with custom additional LLM parameters for OpenAI-compatible clients (#631, #517)
- **Resilience and Retries**:
  - **Retryable LLM Clients**: Introduce retry logic for LLM clients with sensible defaults to reduce transient failures (#592)
  - **Retry Anything with LLM Feedback**: Add a feedback mechanism to the retry component (`subgraphWithRetry`) to observe and tune behavior (#459).

## Improvements

- **OpenTelemetry and Observability**:
  - Finish reason and unified attributes for inference/tool/message spans and events; extract event body fields to attributes for better querying ([KG-218](https://youtrack.jetbrains.com/issue/KG-218)).
  - Mask sensitive data in events/attributes and introduce a “hidden-by-default” string type to keep secrets safe in logs ([KG-259](https://youtrack.jetbrains.com/issue/KG-259)).
  - Include all messages into the inference span and add an index for ChoiceEvent to simplify analysis ([KG-172](https://youtrack.jetbrains.com/issue/KG-172)).
  - Add tool arguments to `gen_ai.choice` and `gen_ai.assistant.message` events (#462).
  - Allow setting a custom OpenTelemetry SDK instance in Koog ([KG-169](https://youtrack.jetbrains.com/issue/KG-169)).
- **LLM and Providers**:
  - Support Google’s “thinking” mode in generation config to improve reasoning quality (#414).
  - Add responses API support for OpenAI (#645)
  - AWS Bedrock: support Inference Profiles for simpler, consistent configuration (#506) and accept `AWS_SESSION_TOKEN` (#456).
  - Add `maxTokens` as prompt parameters for finer control over generation length (#579).
  - Add `contextLength` and `maxOutputTokens` to `LLModel` (#438, [KG-134](https://youtrack.jetbrains.com/issue/KG-134))
- **Agent Engine**:
  - Add AIAgentPipeline interceptors to uniformly handle node errors; propagate `NodeExecutionError` across features ([KG-170](https://youtrack.jetbrains.com/issue/KG-170)).
  - Include finish node processing in the pipeline to ensure finalizers run reliably (#598).
- **File Tools and RAG**:
    - Reworked FileSystemProvider with API cleanups and better ergonomics; moved blocking/suspendable operations to `Dispatchers.IO` for improved performance and responsiveness (#557, “Move suspendable operations to Dispatchers.IO”).
    - Introduce `filterByRoot` helpers and allow custom path filters in `FilteredFileSystemProvider` for safer agent sandboxes (#494, #508).
    - Rename `PathFilter` to `TraversalFilter` and make its methods suspendable to support async checks.
    - Rename `fromAbsoluteString` to `fromAbsolutePathString` for clarity (#567).
    - Add `ReadFileTool` for reading local file contents where appropriate (#628).
- Update kotlin-mcp dependency to v0.6.0 (#523)


## Bug Fixes

- Make `parts` field nullable in Google responses to handle missing content from Gemini models (#652).
- Fix enum parsing in MCP when type is not mentioned (#601, [KG-49](https://youtrack.jetbrains.com/issue/KG-49))
- Fix function calling for `gemini-2.5-flash` models to correctly route tool invocations (#586).
- Restore OpenAI `responseFormat` option support in requests (#643).
- Correct `o4-mini` vs `gpt-4o-mini` model mix-up in configuration (#573).
- Ensure event body for function calls is valid JSON for telemetry ingestion ([KG-268](https://youtrack.jetbrains.com/issue/KG-268)).
- Fix duplicated tool names resolution in `AIAgentSubgraphExt` to prevent conflicts (#493).
- Fix Azure OpenAI client settings to generate valid endpoint URLs (#478).
- Restore `llama3.2:latest` as the default for LLAMA_3_2 to match the provider expectations (#522).
- Update missing `Document` capabilities for LLModel (#543)
- Fix Anthropic json schema validation error (#457)

## Removals / Breaking Changes

- Remove Google Gemini 1.5 Flash/Pro variants from the catalog ([KG-216](https://youtrack.jetbrains.com/issue/KG-216), #574).
- Drop `execute` extensions for `PromptExecutor` in favor of the unified API (#591).
- File system API cleanup: removed deprecated FSProvider interfaces and methods; `PathFilter` renamed to `TraversalFilter` with suspendable operations; `fromAbsoluteString` renamed to `fromAbsolutePathString`.

## Examples

- Add a web search agent (from Koog live stream 1) showcasing retrieval + summarization (#575).
- Add a trip planning agent example (from Koog live stream 2) demonstrating tools + planning + composite strategy (#595).
- Improve BestJokeAgent sample and fix NumberGuessingAgent example (#503, #445).

# 0.3.0

> Published 15 Jul 2025

## Major Features

- **Agent Persistency and Checkpoints**: Save and restore agent state to local disk, memory, or easily integrate with
  any cloud storages or databases. Agents can now roll back to any prior state on demand or automatically restore from
  the latest checkpoint (#305)
- **Vector Document Storage**: Store embeddings and documents in persistent storage for retrieval-augmented generation (
  RAG), with in-memory and local file implementations (#272)
- **OpenTelemetry Support**: Native integration with OpenTelemetry for unified tracing logs across AI agents (#369, #401,
  #423, #426)
- **Content Moderation**: Built-in support for moderating models, enabling AI agents to automatically review and filter
  outputs for safety and compliance (#395)
- **Parallel Node Execution**: Parallelize different branches of your agent graph with a MapReduce-style API to speed up
  agent execution or to choose the best of the parallel attempts (#220, #404)
- **Spring Integration**: Ready-to-use Spring Boot starter with auto-configured LLM clients and beans (#334)
- **AWS Bedrock Support**: Native support for Amazon Bedrock provider covering several crucial models and services (
  #285, #419)
- **WebAssembly Support**: Full support for compiling AI agents to WebAssembly (WASM) for browser deployment (#349)

## Improvements

- **Multimodal Data Support**: Seamlessly integrate and reason over diverse data types such as text, images, and audio (
  #277)
- **Arbitrary Input/Output Types**: More flexibility over how agents receive data and produce responses (#326)
- **Improved History Compression**: Enhanced fact-retrieval history compression for better context management (#394,
  #261)
- **ReAct Strategy**: Built-in support for ReAct (Reasoning and Acting) agent strategy, enabling step-by-step reasoning
  and dynamic action taking (#370)
- **Retry Component**: Robust retry mechanism to enhance agent resilience (#371)
- **Multiple Choice LLM Requests**: Generate or evaluate responses using structured multiple-choice formats (#260)
- **Azure OpenAI Integration**: Support for Azure OpenAI services (#352)
- **Ollama Enhancements**: Native image input support for agents running with Ollama-backed models (#250)
- **Customizable LLM in fact search**: Support providing custom LLM for fact retrieval in the history (#289)
- **Tool Execution Improvements**: Better support for complex parameters in tool execution (#299, #310)
- **Agent Pipeline enhancements**: More handlers and context available in `AIAgentPipeline` (#263)
- **Default support of tools and messages mixture**: Simple single run strategies variants for multiple message and
  parallel tool calls (#344)
- **ResponseMetaInfo Enhancement**: Add `additionalInfo` field to `ResponseMetaInfo` (#367)
- **Subgraph Customization**: Support custom `LLModel` and `LLMParams` in subgraphs, make `nodeUpdatePrompt` a
  pass-through node (#354)
- **Attachments API simplification**: Remove additional `content` builder from `MessageContentBuilder`, introduce
  `TextContentBuilderBase` (#331)
- **Nullable MCP parameters**: Added support for nullable MCP tool parameters (#252)
- **ToolSet API enhancement**: Add missing `tools(ToolSet)` convenience method for `ToolRegistry` builder (#294)
- **Thinking support in Ollama**: Add THINKING capability and it's serialization for Ollama API 0.9 (#248)
- **kotlinx.serialization version update**: Update kotlinx-serialization version to 1.8.1
- **Host settings in FeatureMessageRemoteServer**: Allow configuring custom host in `FeatureMessageRemoteServer` (#256)

## Bug Fixes

- Make `CachedPromptExecutor` and `PromptCache` timestamp-insensitive to enable correct caching (#402)
- Fix `requestLLMWithoutTools` generating tool calls (#325)
- Fix Ollama function schema generation from `ToolDescriptor` (#313)
- Fix OpenAI and OpenRouter clients to produce simple text user message when no attachments are present (#392)
- Fix intput/output token counts for OpenAILLMClient (#370)
- Using correct `Ollama` LLM provider for ollama llama4 model (#314)
- Fixed an issue where structured data examples were prompted incorrectly (#325)
- Correct mistaken model IDs in DEFAULT_ANTHROPIC_MODEL_VERSIONS_MAP (#327)
- Remove possibility of calling tools in structured LLM request (#304)
- Fix prompt update in `subgraphWithTask` (#304)
- Removed suspend modifier from LLMClient.executeStreaming (#240)
- Fix `requestLLMWithoutTools` to work properly across all providers (#268)

## Examples

- W&B Weave Tracing example
- Langfuse Tracing example
- Moderation example: Moderating iterative joke-generation conversation
- Parallel Nodes Execution example: Generating jokes using 3 different LLMs in parallel, and choosing the funniest one
- Snapshot and Persistency example: Taking agent snapshots and restoring its state example

# 0.2.1

> Published 6 Jun 2025

## Bug Fixes

- Support MCP enum arg types and object additionalParameters (#214)
- Allow appending handlers for the EventHandler feature (#234)
- Migrating of simple agents to AIAgent constructor, simpleSingleRunAgent deprecation (#222)
- Fix LLM clients after #195, make LLM request construction again more explicit in LLM clients (#229)

# 0.2.0

> Published 5 Jun 2025

## Features

- Add media types (image/audio/document) support to prompt API and models (#195)
- Add token count and timestamp support to Message.Response, add Tokenizer and MessageTokenizer feature (#184)
- Add LLM capability for caching, supported in anthropic mode (#208)
- Add new LLM configurations for Groq, Meta, and Alibaba (#155)
- Extend OpenAIClientSettings with chat completions API path and embeddings API path to make it configurable (#182)

## Improvements

- Mark prompt builders with PromptDSL (#200)
- Make LLM provider not sealed to allow it's extension (#204)
- Ollama reworked model management API (#161)
- Unify PromptExecutor and AIAgentPipeline API for LLMCall events (#186)
- Update Gemini 2.5 Pro capabilities for tool support
- Add dynamic model discovery and fix tool call IDs for Ollama client (#144)
- Enhance the Ollama model definitions (#149)
- Enhance event handlers with more available information (#212)

## Bug Fixes

- Fix LLM requests with disabled tools, fix prompt messages update (#192)
- Fix structured output JSON descriptions missing after serialization (#191)
- Fix Ollama not calling tools (#151)
- Pass format and options parameters in Ollama request DTO (#153)
- Support for Long, Double, List, and data classes as tool arguments for tools from callable functions (#210)

## Examples

- Add demo Android app to examples (#132)
- Add example with media types - generating Instagram post description by images (#195)

## Removals

- Remove simpleChatAgent (#127)

# 0.1.0 (Initial Release)

> Published 21 May 2025

The first public release of Koog, a Kotlin-based framework designed to build and run AI agents entirely in idiomatic
Kotlin.

## Key Features

- **Pure Kotlin implementation**: Build AI agents entirely in natural and idiomatic Kotlin
- **MCP integration**: Connect to Model Context Protocol for enhanced model management
- **Embedding capabilities**: Use vector embeddings for semantic search and knowledge retrieval
- **Custom tool creation**: Extend your agents with tools that access external systems and APIs
- **Ready-to-use components**: Speed up development with pre-built solutions for common AI engineering challenges
- **Intelligent history compression**: Optimize token usage while maintaining conversation context
- **Powerful Streaming API**: Process responses in real-time with streaming support and parallel tool calls
- **Persistent agent memory**: Enable knowledge retention across sessions and different agents
- **Comprehensive tracing**: Debug and monitor agent execution with detailed tracing
- **Flexible graph workflows**: Design complex agent behaviors using intuitive graph-based workflows
- **Modular feature system**: Customize agent capabilities through a composable architecture
- **Scalable architecture**: Handle workloads from simple chatbots to enterprise applications
- **Multiplatform**: Run agents on both JVM and JS targets with Kotlin Multiplatform

## Supported LLM Providers

- Google
- OpenAI
- Anthropic
- OpenRouter
- Ollama

## Supported Targets

- JVM (requires JDK 17 or higher)
- JavaScript

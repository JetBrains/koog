# 0.6.0
> Published 22 December 2025

## Major Features

- **ACP Integration**: Introduce initial ACP (Agent Communication Protocol) integration to create ACP-compatible agents in Koog (#1253)
- **Planner Agent Type**: Introduce new "planner" agent type with iterative planning capabilities. Provide two out-of-the box strategies: simple LLM planner and GOAP (Goal-Oriented Action Planning) (#1232)
- **Response Processor**: Introduce `ResponseProcessor` to fix tool call messages from weak models that fail to properly generate tool calls ([KG-212](https://youtrack.jetbrains.com/issue/KG-212), #871)

## Improvements

- **Event ID Propagation**: Integrate event ID and execution info propagation across all pipeline events, agent execution flow, and features including Debugger and Tracing ([KG-178](https://youtrack.jetbrains.com/issue/KG-178))
- **Bedrock Enhancements**:
  - Add fallback model support and warning mechanism for unsupported Bedrock models with custom families ([KG-595](https://youtrack.jetbrains.com/issue/KG-595), #1224)
  - Add global inference profile prefix support to Bedrock models for improved availability and latency (#1139)
  - Add Bedrock support in Ktor integration for configuring and initializing Bedrock LLM clients (#1141)
  - Improve Bedrock moderation implementation with conditional guardrails API calls (#1105)
- **Ollama**: Add support for file attachments in Ollama client (#1221)
- **Tool Schema**: Add extension point for custom tool schemas to allow clients to provide custom schemas or modify existing ones (#1158)
- **Google Client**:
  - Add support for `/models` request in Google LLM Client (#1181)
  - Add text embedding support for Google's **Gemini models** via `gemini-embedding-001` ([KG-314](https://youtrack.jetbrains.com/issue/KG-314), #1235)
- **HTTP Client**: Make `KoogHttpClient` auto-closable and add `clientName` parameter (#1184)
- Update MCP SDK version to 0.7.7 (#1154)
- Use SEQUENTIAL mode as default for `singleRunStrategy` (#1195)

## Bug Fixes

- **Streaming**: Fix streaming + tool call issues for Google and OpenRouter clients - Google client now passes tools parameter, OpenRouter uses CIO engine for SSE, improved SSE error handling ([KG-616](https://youtrack.jetbrains.com/issue/KG-616), #1262)
- **Tool Calling**: Fix `requestLLMOnlyCallingTools` ignoring tool calls after reasoning messages from models with Chain of Thought ([KG-545](https://youtrack.jetbrains.com/issue/KG-545), #1198)
- **File Tools**:
  - Handle empty filters in `ListDirectoryTool` ([KG-628](https://youtrack.jetbrains.com/issue/KG-628), #1285)
  - Fix directory collapse in `ListDirectoryUtil` ([KG-583](https://youtrack.jetbrains.com/issue/KG-583), #1260)
  - Clamp `endLine` to file length and add warnings for overflow in `ReadFileTool` ([KG-534](https://youtrack.jetbrains.com/issue/KG-534), #1182)
- **Model-Specific Fixes**:
  - Pass `jsonObject` as `responseFormat` for DeepSeek to fix JSON mode ([KG-537](https://youtrack.jetbrains.com/issue/KG-537), #1258)
  - Remove `LLMCapability.Temperature` from GPT-5 model capabilities (#1277)
  - Fix OpenAI streaming with tools in Responses API ([KG-584](https://youtrack.jetbrains.com/issue/KG-584), #1255)
  - Fix Bedrock timeout setting propagation to `BedrockRuntimeClient.HttpClient` (#1190)
  - Add handler for `GooglePart.InlineData` to support binary content responses ([KG-487](https://youtrack.jetbrains.com/issue/KG-487), #1094)
- **Other Fixes**:
  - Fix reasoning message handling in provided simple strategies (#1166)
  - Fix empty list condition check in `onMultipleToolResults` and `onMultipleAssistantMessages` (#1192)
  - Fix timeout not respected in executor because `join()` was called before timeout check (#1005)
  - Fix `ContentPartsBuilder` to flush whenever `textBuilder` is not empty ([KG-504](https://youtrack.jetbrains.com/issue/KG-504), #1123)
  - Fix and simplify `McpTool` to properly support updated Tool serialization (#1128)
  - Fix `OpenAIConfig.moderationsPath` to be mutable (`var` instead of `val`) (#1097)
  - Finalize pipeline feature processors after agent run for `StatefulSingleUseAIAgent` ([KG-576](https://youtrack.jetbrains.com/issue/KG-576))

## Breaking Changes

- **Persistence**: Remove requirement for unique graph node names in Persistence feature, migrate to node path usage (#1288)
- **Tool API**: Update Tool API to fix name and descriptor discrepancy - moved configurable tool properties to constructors, removed `doExecute` in favor of `execute` ([KG-508](https://youtrack.jetbrains.com/issue/KG-508), #1226)
- **OpenAI Models**: GPT-5-Codex and GPT-5.1 reasoning models moved from Chat section to Reasoning section ([KG-562](https://youtrack.jetbrains.com/issue/KG-562), #1146)
- **Structured Output**: Rename structured output classes - `StructuredOutput` → `StructuredRequest`, `StructuredData` → `Structure`, `JsonStructuredData` → `JsonStructure` (#1107)
- **Module Organization**: Move `LLMChoice` from `prompt-llm` to `prompt-executor-model` module (#1109)

# 0.5.4
> Published 03 December 2025

## Improvements
- LLM clients: better error reporting (#1149). Potential **breaking change**: LLM clients now throw `LLMClientException` instead of `IllegalStateException` ([KG-552](https://youtrack.jetbrains.com/issue/KG-552))
- Add support for **OpenAI** **GPT-5.1** and **GPT-5 pro** (#1121) and (#1113) and **Anthropic** **Claude Opus 4.5** (#1199)
- Add Bedrock support in Ktor for configuring and initializing Bedrock LLM clients. (#1141)
- Improve Bedrock moderation implementation (#1105)
- Add handler for `GooglePart.InlineData` in `GoogleLLMClient` (#1094) ([KG-487](https://youtrack.jetbrains.com/issue/KG-487))
- Improvements in `ReadFileTool` (#1182) and (#1213)

## Bug Fixes
- Fix and simplify `McpTool` to properly support updated Tool serialization (#1128)
- Fix file tools to properly use newer API to provide textual tool result representation (#1201)
- Fix empty list condition check in `onMultipleToolResults` and `onMultipleAssistantMessages` (#1192)
- Fix reasoning message handling in strategy (#1166)
- Fix timeout in `JvmShellCommandExecutor` (#1005)

# 0.5.3
> Published 12 November 2025

## New Features
- Reasoning messages support (#943)
- Add get models list request to `OpenAI`-based `LLMClient`s (#1074)

## Improvements
- Support subgraph execution events in an agent pipeline and features, including `OpenTelemetry` (#1052)
- Make `systemPrompt` and `temperature` optional, set default temperature to null in `AIAgent` factory functions (#1078)
- Improve compatibility with kotlinx-coroutines 1.8 in runtime by removing `featurePrepareDispatcher` from `AIAgentPipeline` (#1083)

## Bug Fixes
- Fix persistence feature by making `ReceivedToolResult` serializable (#1049)
- Make clients properly rethrow cancellations and remove exception wrapping (#1057)
- Fix `StructureFixingParser` to do the right number of retires (#1084)


# 0.5.2
> Published 29 Oct 2025

## New Features
- Add `subtask` extension for non-graph agents similar to `subgraphWithTask` (#982)
- Add MistralAI LLM Client (#622)

## Improvements
- Replace string content and attachments list in messages with a unified content parts list to make the API more flexible and preserve text/attachment parts order (#1004)
- Add input and output attributes to the NodeExecuteSpan span in OpenTelemetry to improve observability [(KG-501)](https://youtrack.jetbrains.com/issue/KG-501)
- Set the JVM target to 11 to support older JVM versions and explicitly specify the JVM target. (#1015)
- Support multi-responses from LLM in the subgraphWithTask API [(KG-507)](https://youtrack.jetbrains.com/issue/KG-507)
- Add error handling for missing tools in GenericAgentEnvironment by passing the error message to the agent instead of failing with exception [(KG-509)](https://youtrack.jetbrains.com/issue/KG-509)

# 0.5.1
> Published 15 Oct 2025

## Improvements
- **Add error handling in LocalFileMemoryProvider** (#905)
- **Add GPT-5 Codex** model support (#888)
- **Added support for filters** in PersistenceProvider (#936)
- **Added** **DashScope (Qwen)** LLM client support (#687)
- Excluded **Ktor** engine dependencies ([KG-315](https://youtrack.jetbrains.com/issue/KG-315))
- Support additional **Bedrock auth options** (#923)
- `requestLLMStreaming` now respect `AgentConfig.missingToolsConversionStrategy` (#944)

## Bug Fixes
- Make subgraphWithTask work with models without ToolChoice support ([KG-440](https://youtrack.jetbrains.com/issue/KG-440))
- Fix for [KTOR-8881](https://youtrack.jetbrains.com/issue/KTOR-8881) - Ktor/Koog configuration in `application.yaml` gives error
- Fixed the ordering issue for **Persistence** checkpoints (#964)
- Fixed issue with the tool name in `@Tool` annotation - now we take it into account (#930)

## Examples
- Supported Multi-LLM Prompt Executor Spring Bean by adding llmProvider method to LLM clients (#842)

# 0.5.0

> Published 2 Oct 2025

## Major Features

- **Full Agent-to-Agent (A2A) Protocol Support**:
    - **Multiplatform Kotlin A2A SDK**: Including server and client with JSON-RPC HTTP support.
    - **A2A Agent Feature**: seamlessly integrate A2A in your Koog agents
- **Non-Graph API for Strategies**: Introduced non-graph API for creating AI Agent strategies as Kotlin extension
  functions with most of Koog's features supported (#560)
- **Agent Persistence and Checkpointing**:
    - **Roll back Tool Side-Effects**: Add `RollbackToolRegistry` in the `Persistence` feature in order to roll back
      tool calls with side effects when checkpointing.
    - **State-Machine Persistence / Message History Switch**: Support switching between full state-machine persistence
      and message history persistence (#856)
- **Tool API Improvements**:
    - Make `ToolDescriptor` auto-generated for class-based tools (#791)
    - Get rid of `ToolArgs` and `ToolResult` limitations for `Tool<*, *>` class (#791)
- **`subgraphWithTask` Simplification**: Get rid of required `finishTool` and support tools as functions in
  `subgraphWithTask`, deduce final step automatically by data class (#791)
- **`AIAgentService` Introduced**: Make `AIAgent` state-manageable and single-run explicitly, introduce `AIAgentService`
  to manage multiple uniform running agents.
- **New components**:
    - Add LLM as a Judge component (#866)
    - Tool Calling loop with Structured Output strategy (#829)

## Improvements

- Make Koog-based tools exportable via MCP server (KG-388)
- Add `additionalProperties` to LLM clients in order to support custom LLM configurations (#836)
- Allow adjusting context window sizes for Ollama dynamically (#883)
- Refactor streaming api to support tool calls (#747)
- Provide an ability to collect and send a list of nodes and edges out of `AIAgentStrategy` to the client when running
  an agent (KG-160)
- Add `excludedProperties` to inline `createJsonStructure` too, update KDocs (#826)
- Refactor binary attachment handling and introduce Base64 serializer (#838)
- In `JsonStructuredData.defaultJson` instance rename class discriminator
  from `#type` to `kind` to align with common practices (#772, KG-384)
- Make standard json generator default when creating `JsonStructuredData`
  (it was basic before) (#772, KG-384)
- Add default audio configuration and modalities (#817)
- Add `GptAudio` model in OpenAI client (#818)
- Allow re-running of finished agents that have `Persistence` feature installed (#828, KG-193)
- Allow ideomatic node transformations with `.transform { ...}` lambda function (#684)
- Add ability to filter messages for every agent feature (KG-376)
- Add support for trace-level attributes in Langfuse integration (#860, KG-427)
- Keep all system messages when compressing message history of the agent(#857)
- Add support for Anthropic's Sonnet 4.5 model in Anthropic/Bedrock providers (#885)
- Refactored LLM client auto-configuration in Spring Boot integration, to modular provider-specific classes with
  improved validation and security (#886)
- Add LLM Streaming agent events (KG-148)

## Bug Fixes

- Fix broken Anthropic models support via Amazon Bedrock (#789)
- Make `AIAgentStorageKey` in agent storage actually unique by removing `data` modifier (#825)
- Fix rerun for agents with Persistence (#828, KG-193)
- Update mcp version to `0.7.2` with fix for Android target (#835)
- Do not include an empty system message in Anthropic request (#887, KG-317)
- Use `maxTokens` from params in Google models (#734)
- Fix finishReason nullability (#771)

## Deprecations

- Rename agent interceptors in `EventHandler` and related feature events (KG-376)
- Deprecate concurrent unsafe `AIAgent.asTool` in favor of `AIAgentService.createAgentTool` (#873)
- Rename `Persistency` to `Persistence` everywhere (#896)
- Add `agentId` argument to all `Persistence` methods instead of `persistencyId` class field (#904)

## Examples

- Add a basic code-agent example (#808, KG-227)
- Add iOS and Web targets for demo-compose-app (#779, #780)

# 0.4.2

> Published 15 Sep 2025

## Improvements

- Make agents‑mcp support KMP targets to run across more platforms (#756).
- Add LLM client retry support to Spring Boot auto‑configuration to improve resilience on transient failures (#748).
- Add Claude Opus 4.1 model support to Anthropic client to unlock latest reasoning capabilities (#730).
- Add Gemini 2.5 Flash Lite model support to Google client to enable lower‑latency, cost‑efficient generations (#769).
- Add Java‑compatible non‑streaming Prompt Executor so Java apps can call Koog without
  coroutines ([KG-312](https://youtrack.jetbrains.com/issue/KG-312), #715).
- Support excluding properties in JSON Schema generation to fine‑tune structured outputs (#638).
- Update AWS SDK to latest compatible version for Bedrock integrations.
- Introduce Postgres persistence provider to store agent state and artifacts (#705).
- Update Kotlin to 2.2.10 in dependency configuration for improved performance and language features (#764).
- Refactor executeStreaming to remove suspend for simpler interop and better call sites (#720).
- Add Java‑compatible prompt executor (non‑streaming) wiring and polish across
  modules ([KG-312](https://youtrack.jetbrains.com/issue/KG-312), #715).
- Decouple FileSystemEntry from FileSystemProvider to simplify testing and enable alternative providers (#664).

## Bug Fixes

- Add missing tool calling support for Bedrock Nova models so agents can invoke functions when using
  Nova ([KG-239](https://youtrack.jetbrains.com/issue/KG-239)).
- Add Android target support and migrate Android app to Kotlin Multiplatform to widen KMP
  coverage ([KG-315](https://youtrack.jetbrains.com/issue/KG-315), #728, #767).
- Add Spring Boot Java example to jump‑start integration (#739).
- Add Java Spring auto‑config fixes: correct property binding and make Koog starter work out of the box (#698).
- Fix split package issues in OpenAI LLM clients to avoid classpath/load
  errors ([KG-305](https://youtrack.jetbrains.com/issue/KG-305), #694).
- Ensure Anthropic tool schemas include the required "type" field in serialized request bodies to prevent validation
  errors during tool calling (#582).
- Fix AbstractOpenAILLMClient to correctly handle plain‑text responses in capabilities flow; add integration tests to
  prevent regressions (#564).
- Fix GraalVM native image build failure so projects can compile native binaries again (#774).
- Fix usages in OpenAI‑based data model to align with recent API changes (#688).
- Fix SpringBootStarters initialization and improve `RetryingClient` (#894)

## CI and Build

- Nightly build configuration and dependency submission workflow added (#695, #737).

# 0.4.1

> Published 28 Aug 2025

## Bug Fixes

Fixed iOS target publication

# 0.4.0

> Published 27 Aug 2025

## Major Features

- **Integration with Observability Tools**:
    - **Langfuse Integration**: Span adapters for Langfuse client, including open telemetry and graph
      visualisation ([KG-217](https://youtrack.jetbrains.com/issue/KG-217), [KG-223](https://youtrack.jetbrains.com/issue/KG-223))
    - **W&B Weave Integration**: Span adapters for W&B Weave open telemetry and
      observability ([KG-217](https://youtrack.jetbrains.com/issue/KG-217), [KG-218](https://youtrack.jetbrains.com/issue/KG-218))
- **Ktor Integration**: First-class Ktor support via the "Koog" Ktor plugin to register and run agents in Ktor
  applications (#422).
- **iOS Target Support**: Multiplatform expanded with native iOS targets, enabling agents to run on Apple platforms (
  #512).
- **Upgraded Structured Output**: Refactored structured output API to be more flexible and add built-in/native provider
  support for OpenAI and Google, reducing prompt boilerplate and improving validation (#443).
- **GPT5 and Custom LLM Parameters Support**: Now GPT5 is available together with custom additional LLM parameters for
  OpenAI-compatible clients (#631, #517)
- **Resilience and Retries**:
    - **Retryable LLM Clients**: Introduce retry logic for LLM clients with sensible defaults to reduce transient
      failures (#592)
    - **Retry Anything with LLM Feedback**: Add a feedback mechanism to the retry component (`subgraphWithRetry`) to
      observe and tune behavior (#459).

## Improvements

- **OpenTelemetry and Observability**:
    - Finish reason and unified attributes for inference/tool/message spans and events; extract event body fields to
      attributes for better querying ([KG-218](https://youtrack.jetbrains.com/issue/KG-218)).
    - Mask sensitive data in events/attributes and introduce a “hidden-by-default” string type to keep secrets safe in
      logs ([KG-259](https://youtrack.jetbrains.com/issue/KG-259)).
    - Include all messages into the inference span and add an index for ChoiceEvent to simplify
      analysis ([KG-172](https://youtrack.jetbrains.com/issue/KG-172)).
    - Add tool arguments to `gen_ai.choice` and `gen_ai.assistant.message` events (#462).
    - Allow setting a custom OpenTelemetry SDK instance in Koog ([KG-169](https://youtrack.jetbrains.com/issue/KG-169)).
- **LLM and Providers**:
    - Support Google’s “thinking” mode in generation config to improve reasoning quality (#414).
    - Add responses API support for OpenAI (#645)
    - AWS Bedrock: support Inference Profiles for simpler, consistent configuration (#506) and accept
      `AWS_SESSION_TOKEN` (#456).
    - Add `maxTokens` as prompt parameters for finer control over generation length (#579).
    - Add `contextLength` and `maxOutputTokens` to `LLModel` (
      #438, [KG-134](https://youtrack.jetbrains.com/issue/KG-134))
- **Agent Engine**:
    - Add AIAgentPipeline interceptors to uniformly handle node errors; propagate `NodeExecutionError` across
      features ([KG-170](https://youtrack.jetbrains.com/issue/KG-170)).
    - Include finish node processing in the pipeline to ensure finalizers run reliably (#598).
- **File Tools and RAG**:
    - Reworked FileSystemProvider with API cleanups and better ergonomics; moved blocking/suspendable operations to
      `Dispatchers.IO` for improved performance and responsiveness (#557, “Move suspendable operations to
      Dispatchers.IO”).
    - Introduce `filterByRoot` helpers and allow custom path filters in `FilteredFileSystemProvider` for safer agent
      sandboxes (#494, #508).
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
- Ensure event body for function calls is valid JSON for telemetry
  ingestion ([KG-268](https://youtrack.jetbrains.com/issue/KG-268)).
- Fix duplicated tool names resolution in `AIAgentSubgraphExt` to prevent conflicts (#493).
- Fix Azure OpenAI client settings to generate valid endpoint URLs (#478).
- Restore `llama3.2:latest` as the default for LLAMA_3_2 to match the provider expectations (#522).
- Update missing `Document` capabilities for LLModel (#543)
- Fix Anthropic json schema validation error (#457)

## Removals / Breaking Changes

- Remove Google Gemini 1.5 Flash/Pro variants from the catalog ([KG-216](https://youtrack.jetbrains.com/issue/KG-216),
  #574).
- Drop `execute` extensions for `PromptExecutor` in favor of the unified API (#591).
- File system API cleanup: removed deprecated FSProvider interfaces and methods; `PathFilter` renamed to
  `TraversalFilter` with suspendable operations; `fromAbsoluteString` renamed to `fromAbsolutePathString`.

## Examples

- Add a web search agent (from Koog live stream 1) showcasing retrieval + summarization (#575).
- Add a trip planning agent example (from Koog live stream 2) demonstrating tools + planning + composite strategy (
  #595).
- Improve BestJokeAgent sample and fix NumberGuessingAgent example (#503, #445).

# 0.3.0

> Published 15 Jul 2025

## Major Features

- **Agent Persistence and Checkpoints**: Save and restore agent state to local disk, memory, or easily integrate with
  any cloud storages or databases. Agents can now roll back to any prior state on demand or automatically restore from
  the latest checkpoint (#305)
- **Vector Document Storage**: Store embeddings and documents in persistent storage for retrieval-augmented generation (
  RAG), with in-memory and local file implementations (#272)
- **OpenTelemetry Support**: Native integration with OpenTelemetry for unified tracing logs across AI agents (#369,
  #401,
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
- Snapshot and Persistence example: Taking agent snapshots and restoring its state example

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

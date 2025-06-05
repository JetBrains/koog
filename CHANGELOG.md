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

The first public release of Koog, a Kotlin-based framework designed to build and run AI agents entirely in idiomatic Kotlin.

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
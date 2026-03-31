# Module features-chat-history-aws

Provides a chat history provider backed by Amazon Bedrock AgentCore Memory, enabling persistent conversation storage and retrieval via the AgentCore `createEvent` and `listEvents` APIs.

## Features

- **AWS Bedrock AgentCore Integration**: Stores and loads conversational history using the Bedrock AgentCore Memory service
- **Delta Tracking**: Only persists new messages by detecting already-stored events via `eventId` metadata, avoiding duplicate writes
- **Paginated Loading**: Fetches events in configurable pages with an optional total events limit
- **Configurable Unsupported Value Handling**: Silently skip or reject non-conversational message types (System, Tool, Reasoning) and non-text content via `ignoreUnsupportedValues`

## Dependencies

- `aws-sdk-kotlin` — Bedrock AgentCore client (`BedrockAgentCoreClient`)
- `agents-features-memory` — Provides the `ChatHistoryProvider` interface

## Key Components

### AgentcoreChatHistoryProvider
Main `ChatHistoryProvider` implementation that stores and loads plain-text `User` and `Assistant` messages through AgentCore events. Supports conversation ID parsing into actor/session pairs.

### AgentcoreMessageConverter
Converts between Koog `Message` types and AgentCore event payloads, handling role mapping and metadata (event ID, timestamps).

### AgentcoreConversationIdParser
Parses conversation ID strings into actor and session components used by the AgentCore API.

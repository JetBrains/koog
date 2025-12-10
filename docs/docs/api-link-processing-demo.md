# Custom API reference link processing demo

Koog documentation also supports custom API reference link processing that allows you to link to specific elements of the Dokka-generated API reference hosted at [api.koog.ai](https://api.koog.ai). This lets you use the element's FQDN to link to its API reference page, instead of having to manually provide the full link.

To add an API reference link, use the following link format:

```markdown
[Link text](api:module-name::package-name.declaration)
```

Here is an example of an actual link to the `Tokenizer` interface:

```markdown
[Tokenizer](api:prompt-tokenizer::ai.koog.prompt.tokenizer.Tokenizer)
```

Once processed by mkdocs, the link renders as follows:

[Tokenizer](api:prompt-tokenizer::ai.koog.prompt.tokenizer.Tokenizer)

!!! note
    The processor will only render links that already exist in the API reference. Links to locally generated Dokka API reference pages that are not yet available on [api.koog.ai](https://api.koog.ai) will not be processed.

## Examples

The following list contains links to the API reference pages for different types of elements:

- Classes: 
    - [Tracing](api:agents-features-trace::ai.koog.agents.features.tracing.feature.Tracing)
    - [TraceFeatureMessageLogWriter](api:agents-features-trace::ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter)
    - [AIAgentFunctionalStrategy](api:agents-core::ai.koog.agents.core.agent.AIAgentFunctionalStrategy)
- Objects:
    - [CrossProduct](api:agents-features-memory::ai.koog.agents.memory.model.MemoryScope.CrossProduct)
    - [MermaidDiagramGenerator](api:agents-core::ai.koog.agents.core.agent.MermaidDiagramGenerator)
    - [ModelInfo.Companion](api:agents-utils::ai.koog.agents.utils.ModelInfo.Companion)
- Interfaces:
    - [traceString](api:agents-features-trace::ai.koog.agents.features.tracing.traceString)
    - [PromptCache](api:prompt-cache-model::ai.koog.prompt.cache.model.PromptCache)
- Functions:
    - [tokenizer()](api:agents-features-tokenizer::ai.koog.agents.features.tokenizer.feature.tokenizer)
    - [mostRelevantDocuments()](api:rag-base::ai.koog.rag.base.mostRelevantDocuments)
- Enum values:
    - [FactType.SINGLE](api:agents-features-memory::ai.koog.agents.memory.model.FactType.SINGLE)
    - [ToolCall.SEQUENTIAL](api:agents-core::ai.koog.agents.core.agent.ToolCalls.SEQUENTIAL)
  - Random:
    - [defaultStdioTransport()](api:agents-mcp::ai.koog.agents.mcp.defaultStdioTransport)
    - [AgentCheckpointPredicateFilter](api:agents-features-snapshot::ai.koog.agents.snapshot.providers.filters.AgentCheckpointPredicateFilter)
    - [ResponseProcessor.Chain](api:prompt-processor::ai.koog.prompt.processor.ResponseProcessor.Chain)
    - [MarkdownStreamingParser](api:prompt-structure::ai.koog.prompt.structure.markdown.MarkdownParserBuilder.MarkdownStreamingParser)

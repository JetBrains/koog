# Koog Framework Simple Examples

> **⚡ Composite Build Notice**: This project uses Gradle's composite build feature to depend on the local root Koog project,
> ensuring you always have the latest development version. This provides convenience and keeps everything in sync during development.
> For production use, you can replace these composite build dependencies with exact published versions from public repositories.
> Check [Composite Build Information](#-composite-build-information) below for more details.

Welcome to the **Koog Framework Simple Examples** collection! This project showcases various AI agent implementations and patterns using the Koog framework for Kotlin, ranging from basic concepts to advanced features.

## 🚀 Quick Start

### Prerequisites
- **Java 17+**
- **API Keys** for your chosen AI providers:
  - OpenAI API key
  - Anthropic API key (optional)
  - Other provider keys as needed

### Setup
1. **Set up environment variables:**
   ```bash
   # macOS/Linux
   export OPENAI_API_KEY=your_openai_key
   export ANTHROPIC_API_KEY=your_anthropic_key

   # Windows
   set OPENAI_API_KEY=your_openai_key
   set ANTHROPIC_API_KEY=your_anthropic_key
   ```

2. **Or create an `env.properties` file** in the project root:
   ```properties
   OPENAI_API_KEY=your_openai_key
   ANTHROPIC_API_KEY=your_anthropic_key
   # ... other API keys as needed
   ```

### Run
```bash
# Run a specific example (see available tasks below)
./gradlew runExampleCalculator
```

## 📚 Examples

### Core Examples

| Example                     | Description                                                       | Gradle Task                         | Notebook                                             |
|-----------------------------|-------------------------------------------------------------------|-------------------------------------|------------------------------------------------------|
| **Calculator**              | Basic calculator agent with parallel tool calls and event logging | `runExampleCalculator`              | [📓 Calculator.ipynb](../notebooks/Calculator.ipynb) |
| **Calculator V2**           | Enhanced calculator with improved functionality                   | `runExampleCalculatorV2`            | -                                                    |
| **Calculator Local**        | Calculator using local LLM models                                 | `runExampleCalculatorLocal`         | -                                                    |
| **Streaming with Tools**    | Agent demonstrating streaming responses while using tools         | `runExampleStreamingWithTools`      | -                                                    |
| **Banking Routing**         | Comprehensive AI banking assistant with routing capabilities      | `runExampleRoutingViaGraph`         | [📓 Banking.ipynb](../notebooks/Banking.ipynb)       |
| **Banking Agents as Tools** | Banking routing using agents as tools pattern                     | `runExampleRoutingViaAgentsAsTools` | -                                                    |
| **Chess**                   | Intelligent chess-playing agent with interactive choice selection | -                                   | [📓 Chess.ipynb](../notebooks/Chess.ipynb)           |
| **Guesser**                 | Number-guessing agent implementing binary search strategy         | `runExampleGuesser`                 | [📓 Guesser.ipynb](../notebooks/Guesser.ipynb)       |

### Advanced Features

| Feature                    | Description                                    | Gradle Task                                | Notebook |
|----------------------------|------------------------------------------------|--------------------------------------------|----------|
| **Error Fixing**           | Agent that can identify and fix errors in code | `runExampleErrorFixing`                    | -        |
| **Error Fixing Local**     | Error fixing with local LLM models             | `runExampleErrorFixingLocal`               | -        |
| **Essay Writer**           | Essay writing agent with structured output     | `runExampleEssay`                          | -        |
| **Template Generation**    | Fleet project template generation agent        | `runExampleFleetProjectTemplateGeneration` | -        |
| **Rider Project Template** | Rider project template generation              | `runExampleRiderProjectTemplate`           | -        |
| **Project Analyzer**       | Agent for analyzing project structure and code | `runProjectAnalyzer`                       | -        |

### Structured Output Examples

| Example                           | Description                                        | Gradle Task                                            | Notebook |
|-----------------------------------|----------------------------------------------------|--------------------------------------------------------|----------|
| **Simple Structured Output**      | Basic structured data output patterns              | `runExampleStructuredOutputSimple`                     | -        |
| **Advanced with Basic Schema**    | Advanced structured output using basic schema      | `runExampleStructuredOutputAdvancedWithBasicSchema`    | -        |
| **Advanced with Standard Schema** | Advanced structured output with standard schema    | `runExampleStructuredOutputAdvancedWithStandardSchema` | -        |
| **Markdown Streaming**            | Streaming structured data in Markdown format       | `runExampleMarkdownStreaming`                          | -        |
| **Markdown Streaming with Tools** | Streaming Markdown output combined with tool usage | `runExampleMarkdownStreamingWithTool`                  | -        |

### External API Integration

| Example           | Description                                          | Gradle Task                        | Notebook                                                 |
|-------------------|------------------------------------------------------|------------------------------------|----------------------------------------------------------|
| **Attachments**   | Using structured Markdown and attachments in prompts | `runExampleInstagramPostDescriber` | [📓 Attachments.ipynb](../notebooks/Attachments.ipynb)   |
| **Bedrock Agent** | AI agents using AWS Bedrock integration              | `runExampleBedrockAgent`           | [📓 BedrockAgent.ipynb](../notebooks/BedrockAgent.ipynb) |
| **Web Search**    | Agent with web search capabilities                   | `runExampleWebSearchAgent`         | -                                                        |

### Advanced Patterns

| Feature               | Description                                   | Gradle Task                     | Notebook |
|-----------------------|-----------------------------------------------|---------------------------------|----------|
| **Memory**            | Customer support agent with persistent memory | -                               | -        |
| **Tone Analysis**     | Text tone analysis capabilities               | -                               | -        |
| **Moderation**        | Content moderation with jokes example         | `runExampleJokesWithModeration` | -        |
| **Execution Sandbox** | Safe code execution in sandboxed environment  | `runExampleExecSandbox`         | -        |
| **Loop Components**   | Project generation with loop-based components | `runExampleLoopComponent`       | -        |

### Persistence Examples

| Example                   | Description                             | Gradle Task                     | Notebook |
|---------------------------|-----------------------------------------|---------------------------------|----------|
| **File Persistent Agent** | Agent with file-based state persistence | `runExampleFilePersistentAgent` | -        |
| **SQL Persistent Agent**  | Agent with SQL database persistence     | `runExampleSQLPersistentAgent`  | -        |

### Observability and Monitoring

| Feature           | Description                                         | Gradle Task                      | Notebook                                                   |
|-------------------|-----------------------------------------------------|----------------------------------|------------------------------------------------------------|
| **OpenTelemetry** | Adding OpenTelemetry-based tracing to agents        | `runExampleFeatureOpenTelemetry` | [📓 OpenTelemetry.ipynb](../notebooks/OpenTelemetry.ipynb) |
| **Langfuse**      | Export agent traces to Langfuse using OpenTelemetry | -                                | [📓 Langfuse.ipynb](../notebooks/Langfuse.ipynb)           |
| **Weave**         | Trace agents to W&B Weave using OpenTelemetry       | -                                | [📓 Weave.ipynb](../notebooks/Weave.ipynb)                 |

## 🔧 Available Gradle Tasks

Run any example using:
```bash
./gradlew [task-name]
```

**Core Examples:**
- `runExampleCalculator` - Basic calculator agent
- `runExampleCalculatorV2` - Enhanced calculator
- `runExampleCalculatorLocal` - Calculator with local LLM
- `runExampleStreamingWithTools` - Streaming responses with tool usage
- `runExampleGuesser` - Number guessing game agent
- `runExampleEssay` - Essay writing agent

**Banking Examples:**
- `runExampleRoutingViaGraph` - Banking agent with graph routing
- `runExampleRoutingViaAgentsAsTools` - Banking with agents as tools

**Error Handling:**
- `runExampleErrorFixing` - Code error fixing agent
- `runExampleErrorFixingLocal` - Error fixing with local LLM

**Template Generation:**
- `runExampleFleetProjectTemplateGeneration` - Fleet project templates
- `runExampleRiderProjectTemplate` - Rider project templates
- `runExampleTemplate` - Generic template generation

**Structured Output:**
- `runExampleStructuredOutputSimple` - Basic structured output
- `runExampleStructuredOutputAdvancedWithBasicSchema` - Advanced basic schema
- `runExampleStructuredOutputAdvancedWithStandardSchema` - Advanced standard schema
- `runExampleMarkdownStreaming` - Streaming Markdown data
- `runExampleMarkdownStreamingWithTool` - Streaming with tools

**External Integration:**
- `runExampleBedrockAgent` - AWS Bedrock integration
- `runExampleWebSearchAgent` - Web search capabilities
- `runExampleInstagramPostDescriber` - Attachment handling

**Advanced Features:**
- `runExampleJokesWithModeration` - Content moderation
- `runExampleExecSandbox` - Code execution sandbox
- `runExampleLoopComponent` - Loop-based project generation
- `runExampleFeatureOpenTelemetry` - OpenTelemetry tracing

**Persistence:**
- `runExampleFilePersistentAgent` - File-based persistence
- `runExampleSQLPersistentAgent` - SQL database persistence

**Analysis:**
- `runProjectAnalyzer` - Project structure analysis

## 📖 Related Resources

- **[Interactive Notebooks](../notebooks/)** - Jupyter notebooks for hands-on learning
- **[Full Documentation](https://docs.koog.ai/)** - Complete framework documentation
- **[API Reference](https://api.koog.ai/)** - Detailed API documentation
- **[Getting Started Guide](https://docs.koog.ai/single-run-agents/)** - Framework introduction

## 🔄 Composite Build Information

This project uses Gradle's composite build feature to include the root Koog project:

```kotlin
// settings.gradle.kts
includeBuild("../../.") {
    name = "koog"
}
```

This means:
- ✅ **Development**: Always uses the latest local Koog framework code
- ✅ **Convenience**: No need to publish and update versions during development
- ✅ **Sync**: Changes in the main framework are immediately available

**For production use**, replace composite build dependencies in `build.gradle.kts` with published versions:

```kotlin
// Replace this composite build approach:
implementation("ai.koog:koog-agents")

// With specific published versions:
implementation("ai.koog:koog-agents:VERSION")
```

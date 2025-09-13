# Koog Framework Examples

<p align="center">
  <a href="https://docs.koog.ai/examples/">
    <svg width="220" height="50" xmlns="http://www.w3.org/2000/svg">
      <rect x="0" y="0" width="220" height="50" rx="20" ry="20" style="fill:#4f46e5;" />
      <text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle" fill="white" font-family="sans-serif" font-size="16" font-weight="bold">Koog Examples</text>
    </svg>
  </a>
</p>

Welcome to the **Koog Framework Examples** repository! This collection showcases various AI agent implementations and
patterns using the Koog framework for Kotlin.

---

## Contents

- 🎯 [Examples](#examples)
- 📱 [Android Demo App](#android-demo-app)
- 📖 [How to Run](#how-to-run)
- ⚙️ [Getting Started](#getting-started)

---

## Examples

Each example includes both **interactive Jupyter notebooks** and **complete Kotlin project implementations**.

### Core Examples

| Example           | Description                                                                   | Notebook                                     | Project                                                          |
|-------------------|-------------------------------------------------------------------------------|----------------------------------------------|------------------------------------------------------------------|
| **Attachments**   | Learn how to use structured Markdown and attachments in prompts               | [📓 Notebook](notebooks/Attachments.ipynb)   | [🚀 Project](src/main/kotlin/ai/koog/agents/example/attachments) |
| **Banking**       | Build a comprehensive AI banking assistant with routing capabilities          | [📓 Notebook](notebooks/Banking.ipynb)       | [🚀 Project](src/main/kotlin/ai/koog/agents/example/banking)     |
| **BedrockAgent**  | Create intelligent AI agents using AWS Bedrock integration                    | [📓 Notebook](notebooks/BedrockAgent.ipynb)  | [🚀 Project](src/main/kotlin/ai/koog/agents/example/client)      |
| **Calculator**    | Build a calculator agent with parallel tool calls and event logging           | [📓 Notebook](notebooks/Calculator.ipynb)    | [🚀 Project](src/main/kotlin/ai/koog/agents/example/calculator)  |
| **Chess**         | Build an intelligent chess-playing agent with interactive choice selection    | [📓 Notebook](notebooks/Chess.ipynb)         | [🚀 Project](src/main/kotlin/ai/koog/agents/example/chess)       |
| **GoogleMapsMcp** | Connect to Google Maps MCP server and perform geocoding and elevation queries | [📓 Notebook](notebooks/GoogleMapsMcp.ipynb) | [🚀 Project](src/main/kotlin/ai/koog/agents/example/mcp)         |
| **Guesser**       | Build a number-guessing agent implementing binary search strategy             | [📓 Notebook](notebooks/Guesser.ipynb)       | [🚀 Project](src/main/kotlin/ai/koog/agents/example/guesser)     |
| **PlaywrightMcp** | Drive browsers with Playwright MCP for web automation tasks                   | [📓 Notebook](notebooks/PlaywrightMcp.ipynb) | [🚀 Project](src/main/kotlin/ai/koog/agents/example/mcp)         |
| **UnityMcp**      | Control Unity game development through MCP server integration                 | [📓 Notebook](notebooks/UnityMcp.ipynb)      | [🚀 Project](src/main/kotlin/ai/koog/agents/example/mcp)         |
| **VaccumAgent**   | Implementation of a basic reflex agent for cleaning tasks                     | [📓 Notebook](notebooks/VaccumAgent.ipynb)   | [🚀 Project](src/main/kotlin/ai/koog/agents/example/simpleapi)   |

### Advanced Features

| Feature             | Description                                                     | Notebook                                     | Project                                                                     |
|---------------------|-----------------------------------------------------------------|----------------------------------------------|-----------------------------------------------------------------------------|
| **Langfuse**        | Export Koog agent traces to Langfuse using OpenTelemetry        | [📓 Notebook](notebooks/Langfuse.ipynb)      | [🚀 Project](src/main/kotlin/ai/koog/agents/example/features/langfuse)      |
| **OpenTelemetry**   | Add OpenTelemetry-based tracing to Koog AI agents               | [📓 Notebook](notebooks/OpenTelemetry.ipynb) | [🚀 Project](src/main/kotlin/ai/koog/agents/example/features/opentelemetry) |
| **Weave**           | Learn how to trace Koog agents to W&B Weave using OpenTelemetry | [📓 Notebook](notebooks/Weave.ipynb)         | [🚀 Project](src/main/kotlin/ai/koog/agents/example/features/weave)         |
| **Memory**          | Customer support agent with persistent memory                   | -                                            | [🚀 Project](src/main/kotlin/ai/koog/agents/example/memory)                 |
| **MCP Integration** | Model Context Protocol examples                                 | -                                            | [🚀 Project](src/main/kotlin/ai/koog/agents/example/mcp)                    |
| **Planner**         | Task planning with execution trees                              | -                                            | [🚀 Project](src/main/kotlin/ai/koog/agents/example/planner)                |
| **Structured Data** | JSON-based structured output                                    | -                                            | [🚀 Project](src/main/kotlin/ai/koog/agents/example/structuredoutput)       |
| **Tone Analysis**   | Text tone analysis agent                                        | -                                            | [🚀 Project](src/main/kotlin/ai/koog/agents/example/tone)                   |

### Integration Examples

- [**Spring Boot Java with Koog**](spring-boot-java/README.md) - A Spring Boot application that integrates Koog AI
  capabilities, providing a REST API endpoint for chat interactions using OpenAI's GPT models

---

## Android Demo App

A complete Android application showcasing Koog framework integration with:

- **Calculator Agent**: Arithmetic operations with tool calling
- **Weather Agent**: Weather information retrieval
- **Settings Management**: API key configuration
- **Modern UI**: Jetpack Compose interface

### 🚀 How to Run Android App

1. **Open in IntelliJ IDEA or Android Studio:**
    - Navigate to [`demo-android-app`](demo-android-app) directory
    - Open the project in your preferred IDE

2. **Configure API keys** in the app settings
3. **Build and run** on device or emulator

---

## How to Run

### 📓 Running Notebooks

1. **Open in IntelliJ IDEA:**
    - IntelliJ IDEA has built-in Kotlin Notebook support
    - Navigate to [`notebooks`](notebooks) directory
    - Open any `.ipynb` file

2. **Set up environment variables:**
   ```bash
   # macOS/Linux
   export OPENAI_API_KEY=your_openai_key
   export ANTHROPIC_API_KEY=your_anthropic_key
   
   # Windows
   set OPENAI_API_KEY=your_openai_key
   set ANTHROPIC_API_KEY=your_anthropic_key
   ```

### 🚀 Running Projects

1. **Build the project:**
   ```bash
   ./gradlew build
   ```

2. **Run a specific example:**
   ```bash
   ./gradlew run -PmainClass="ai.koog.agents.example.calculator.CalculatorKt"
   ```

3. **Set environment variables:**
    - **Option 1:** Use system environment variables (as shown above)
    - **Option 2:** Create [`env.properties`](env.template.properties) file:
      ```properties
      OPENAI_API_KEY=your_openai_key
      ANTHROPIC_API_KEY=your_anthropic_key
      # ... other API keys as needed
      ```

---

## Getting Started

### Prerequisites

- **Java 17+**
- **Kotlin 1.9+**
- **API Keys** for your chosen AI providers:
    - OpenAI API key
    - Anthropic API key (optional)
    - AWS Bedrock credentials (for Bedrock examples)
    - Other provider keys as needed

### Quick Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/JetBrains/koog.git
   cd koog/examples
   ```

2. **Set up environment:**
   ```bash
   cp env.template.properties env.properties
   # Edit env.properties with your API keys
   ```

3. **Choose your path:**
    - **Notebooks**: Open in IntelliJ IDEA and explore interactive examples
    - **Projects**: Build with Gradle and run specific examples
    - **Android**: Open in IntelliJ IDEA or Android Studio for mobile development

---

## Documentation

- 📖 **[Full Documentation](https://docs.koog.ai/)**
- 🎯 **[Examples Guide](https://docs.koog.ai/examples/)**
- 🚀 **[Getting Started](https://docs.koog.ai/single-run-agents/)**
- 🔧 **[API Reference](https://api.koog.ai/)**

---

## License

This project is licensed under the [Apache License 2.0](../LICENSE.txt).

---

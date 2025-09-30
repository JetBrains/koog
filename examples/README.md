# Koog Framework Examples

<p align="center">
  <a href="https://docs.koog.ai/examples/">
    <svg width="220" height="50" xmlns="http://www.w3.org/2000/svg">
      <rect x="0" y="0" width="220" height="50" rx="20" ry="20" style="fill:#4f46e5;" />
      <text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle" fill="white" font-family="sans-serif" font-size="16" font-weight="bold">Koog Examples</text>
    </svg>
  </a>
</p>

Welcome to the **Koog Framework Examples** collection! This directory contains multiple example projects showcasing various AI agent implementations and patterns using the Koog framework for Kotlin.

---

## 📁 Project Structure

This examples directory hosts several independent projects, each demonstrating different aspects of the Koog framework:

### 🔧 Core Example Projects

#### [**simple-examples**](simple-examples/)

A comprehensive collection of runnable Koog framework examples ranging from basic concepts to advanced features. 

**Key Examples:**
- Calculator agents with tool calling
- Banking assistant with routing capabilities
- Error fixing and code analysis agents
- Structured output and streaming examples
- OpenTelemetry integration and tracing
- Memory and persistence patterns
- … and more

➡️ **[View all examples and run instructions →](simple-examples/README.md)**

#### [**demo-compose-app**](demo-compose-app/)

A complete Kotlin Multiplatform application built with Compose Multiplatform that demonstrates Koog integration in mobile and desktop environments.

**Features:**
- **Calculator Agent**: Arithmetic operations with tool calling
- **Weather Agent**: Weather information retrieval
- **Settings Management**: API key configuration
- **Modern UI**: Jetpack Compose interface
- **Cross-platform**: Android, iOS, and Desktop support

➡️ **[Setup and run instructions →](demo-compose-app/README.md)**

### 🌟 Specialized Example Projects

#### [**trip-planning-example**](trip-planning-example/)
An advanced trip planning agent demonstrating complex multi-API integration:
- Natural language conversation interface
- Google Maps and weather API integration
- Iterative planning with user feedback
- MCP (Model Context Protocol) integration
- Multi-LLM executor support

#### [**spring-boot-java**](spring-boot-java/)
A Spring Boot application showcasing Koog integration in Java environments:
- REST API for chat interactions
- Reactive programming patterns
- OpenAI GPT-4 integration via Koog
- Configurable AI persona

### 📚 Learning Resources

#### [**notebooks**](notebooks/)
Interactive Jupyter notebooks for hands-on learning with the Koog framework. Each notebook provides step-by-step tutorials with executable examples:

| Notebook                                             | Description                                 |
|------------------------------------------------------|---------------------------------------------|
| [Calculator.ipynb](notebooks/Calculator.ipynb)       | Basic calculator agent with tool calling    |
| [Banking.ipynb](notebooks/Banking.ipynb)             | Banking assistant with routing capabilities |
| [Chess.ipynb](notebooks/Chess.ipynb)                 | Chess-playing agent with choice selection   |
| [Attachments.ipynb](notebooks/Attachments.ipynb)     | Using structured Markdown and attachments   |
| [BedrockAgent.ipynb](notebooks/BedrockAgent.ipynb)   | AWS Bedrock integration                     |
| [OpenTelemetry.ipynb](notebooks/OpenTelemetry.ipynb) | Tracing and observability                   |
| [Langfuse.ipynb](notebooks/Langfuse.ipynb)           | Export traces to Langfuse                   |
| [Weave.ipynb](notebooks/Weave.ipynb)                 | W&B Weave integration                       |
| *...and more*                                        | Additional notebooks for various features   |

---

## 🚀 Quick Start Guide

### Choose Your Path

1. **🔍 Explore Examples**: Start with [`simple-examples`](simple-examples/) for comprehensive runnable examples
2. **📱 Mobile Development**: Try [`demo-compose-app`](demo-compose-app/) for multiplatform applications
3. **📓 Interactive Learning**: Open notebooks in IntelliJ IDEA for hands-on tutorials
4. **🌐 Advanced Integration**: Explore specialized projects like trip-planning or Spring Boot

## 🛠️ How to Run Examples

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

### 🚀 Running Other Examples
Each project has a dedicated README with instructions

---

## Prerequisites

- **Java 17+**
- **Kotlin 1.9+**
- **API Keys** for your chosen AI providers

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

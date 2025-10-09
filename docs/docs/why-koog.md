# Why Koog

Koog is designed to solve real-world problems with JetBrains-level quality.
It provides advanced AI algorithms, out-of-the-box proven techniques, a Kotlin DSL, and robust multi-platform
support that goes beyond traditional frameworks.

## Integration with JVM and Kotlin applications

Koog provides a Kotlin Domain-Specific Language (DSL) designed specifically for JVM and Kotlin developers.
This ensures smooth integration into Kotlin and Java-based applications, 
significantly improving productivity and enhancing the overall developer experience.

## Real-world validation with JetBrains products

Koog powers multiple JetBrains products, including internal AI agents. 
This real-world integration ensures that Koog is continuously tested, refined, and validated against practical use cases. 
It focuses on what works in practice, incorporating insights from extensive feedback and real-product scenarios. 
This integration provides Koog with strengths that distinguish it from other frameworks.

## Advanced solutions available out of the box

Koog includes pre-built, composable solutions to simplify and speed up the development of agentic systems, setting it apart from frameworks that only offer basic components:

* **Multiple history compression strategies.** Koog comes with advanced strategies to compress and manage long-running conversations out of the box, eliminating the need for manual experimentation with approaches. With fine-tuned prompts, techniques, and algorithms tested and refined by ML engineers, you can rely on proven methods to improve performance. For more details on compression strategies, refer to [History compression](https://docs.koog.ai/history-compression/). To explore how Koog handles compression and context management in real-world scenarios, check out [this article](https://blog.jetbrains.com/ai/2025/07/when-tool-calling-becomes-an-addiction-debugging-llm-patterns-in-koog/).
* **Seamless LLM switching.** You can switch a conversation to a different large language model (LLM) with a new set of available tools at any point without losing the existing conversation history. Koog automatically rewrites the history and handles unavailable tools, enabling smooth transitions and a natural interaction flow.
* **Advanced persistence.** Koog lets you restore full agent state machines instead of just chat messages. This enables features like checkpoints, failure recovery, and even the ability to revert to any point in the execution of the state machine.
* **Robust retry component.** Koog includes a retry mechanism that lets you wrap any set of operations within your agentic system and retry them until they meet configurable conditions. You can provide feedback and adjust each attempt to ensure reliable results. If LLM calls time out, tools do not work as expected, or there are network issues, Koog ensures that your agent remains resilient and performs effectively, even during temporary failures. For more technical details, see [Retry functionality](https://docs.koog.ai/history-compression/).
* **Structured typed streaming with Markdown DSL.** Koog streams LLM output and parses it into structured, typed events using a Markdown DSL. You can register handlers for specific elements like headers, bullet points, or regex patterns and receive only the relevant parts in real-time. This approach provides human-readable feedback using Markdown and machine-parsable data using structured typing, effectively eliminating the lack of transparency and enhancing the user experience. It ensures predictable output and dynamic user interfaces with progressive content rendering.


## Broad integration, multiplatform support, enhanced observability

Koog supports the development and deployment of agentic applications across a variety of platforms and environments:

*  **Multiplatform support**. You can deploy your agentic applications across JVM, JS, WasmJS, Android, and iOS targets.
*  **Broad AI integration**. Koog integrates with major LLM providers, including OpenAI and Anthropic, as well as enterprise-level AI clouds like Bedrock. It also supports local models such as Ollama. For the full list of available providers, see [LLM providers](https://docs.koog.ai/llm-providers/).
*  **OpenTelemetry support**. Koog provides out-of-the-box integration with popular observability providers like [W&B Weave](https://wandb.ai/site/weave/) and [Langfuse](https://langfuse.com/) for monitoring and debugging AI applications. With native OpenTelemetry support, you can trace, log, and measure your agents using the same tools you already use in your system. To learn more, refer to [OpenTelemetry](https://docs.koog.ai/opentelemetry-support/).
*  **Spring Boot and Ktor integrations**. Koog integrates with widely used enterprise environments.
      * If you have a Ktor server, you can install Koog as a plugin, configure providers using configuration files, and call agents directly from any route without manually connecting LLM clients.
      * For Spring Boot, Koog provides ready-to-use beans and auto-configured LLM clients, making it easy to start building AI-powered workflows.

## Collaboration with ML engineers and product teams

A unique advantage of Koog is its direct collaboration with JetBrains ML engineers and product teams.
This ensures that features built with Koog are not just theoretical but tested and refined based on real-world product requirements.
This means that Koog incorporates:

* **Fine-tuned prompts and strategies** optimized for real-world performance.
* **Proven engineering approaches** discovered and validated through product development, such as its unique history compression strategies. You can learn more in [this detailed article](https://blog.jetbrains.com/ai/2025/07/when-tool-calling-becomes-an-addiction-debugging-llm-patterns-in-koog/).
* **Continuous improvements** that help Koog stay efficient and adaptable to evolving needs.

## Commitment to the developer community

The Koog team is deeply committed to building a strong developer community.
By actively gathering and incorporating feedback, Koog evolves to meet the needs of developers effectively.
We are actively expanding support for diverse AI architectures, comprehensive benchmarks, detailed use-case guides,
and educational resources to empower developers.

## Where to start

* Explore Koog capabilities in [Overview](https://docs.koog.ai/).
* Build your first Koog agent with our [Getting started](https://docs.koog.ai/getting-started/) guide.
* See the latest updates in Koog [release notes](https://github.com/JetBrains/koog/blob/main/CHANGELOG.md).
* Learn from [Examples](https://docs.koog.ai/examples/).

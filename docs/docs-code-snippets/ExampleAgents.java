import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.config.AIAgentConfig;
import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.ollama.client.OllamaModels;
import ai.koog.prompt.params.LLMParams;

import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleOllamaAIExecutor;
import static ai.koog.prompt.executor.llms.all.SimplePromptExecutorsKt.simpleOpenAIExecutor;

public class ExampleAgents {
    public static void main(String[] args) {

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(simpleOllamaAIExecutor("http://localhost:11434"))
            .llmModel(OllamaModels.Meta.LLAMA_3_2)
            .systemPrompt("You are a helpful assistant.")
            .temperature(0.7)
            .maxIterations(10)
            .build();


        Prompt prompt = Prompt.builder("assistant")
            .system("You are a helpful assistant.")
            .build()
            .withParams(new LLMParams(
                0.7,         // temperature
                null,        // maxTokens
                1,           // numberOfChoices
                null,        // speculation
                null,        // schema
                LLMParams.ToolChoice.Auto.INSTANCE, // toolChoice
                null,        // user
                null         // additionalProperties
            ));

        AIAgentConfig agentConfig = AIAgentConfig.builder(OpenAIModels.Chat.GPT4o)
                .prompt(prompt)
                .maxAgentIterations(10)
                .build();

        AIAgent<String, String> agent1 = AIAgent.builder()
                .promptExecutor(simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")))
                .agentConfig(agentConfig)
                .build();
    }
}

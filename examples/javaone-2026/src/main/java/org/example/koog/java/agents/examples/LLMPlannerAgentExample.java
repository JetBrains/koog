package org.example.koog.java.agents.examples;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.config.AIAgentConfig;
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.planner.AIAgentPlannerStrategy;
import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.serialization.jackson.JacksonSerializer;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.koog.java.tools.AccountReadTools;
import org.example.koog.java.tools.AccountWriteTools;
import org.example.koog.java.tools.CommunicationTools;

// Unused example, shows how to build GOAP agents:
public class LLMPlannerAgentExample {

    private void defineAgent(String userId) {

        AIAgent<String, String> plannerAgent = AIAgent.builder()
                .plannerStrategy(
                        AIAgentPlannerStrategy.builder("llm-planner-with-critic")
                                .llmBasedPlanner()
                                .withCritic()
                                .withHistoryCompression(HistoryCompressionStrategy.Chunked(20))
                                .build()
                )
                .toolRegistry(
                        ToolRegistry.builder()
                                .tools(new CommunicationTools())
                                .tools(new AccountReadTools(userId))
                                .tools(new AccountWriteTools(userId))
                                .build()
                )
                .agentConfig(
                        AIAgentConfig.builder()
                                .model(OpenAIModels.Chat.GPT4_1)
                                .prompt(
                                        Prompt.builder("id")
                                                .system("You are a helpful banking assistant.")

                                                // Few-shot examples:
                                                // (prompt engineering technique to provide more context to the LLM with the desired behavior patterns):
                                                .user("Take a loan for me!")
                                                .assistant("Sorry, I can't take a loan, please contact a human operator")

                                                .user("What is my balance?")
                                                .assistant("Checking the balance.... Calling tool `getAccountBalance`... Your balance is : ...")
                                                .user("Why?")
                                                .assistant("Let me check your latest transactions... Calling tool `getLatestTransactions`... Here is why: ...")

                                                .build()
                                )
                                .maxAgentIterations(100)
                                // set custom serializer:
                                .serializer(new JacksonSerializer(
                                        new ObjectMapper()
                                                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                                ))
                                .build()
                )
                .build();

        plannerAgent.run("Cars and motorsport");
    }
}

package org.example.koog.java.agents.examples;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.model.PromptExecutor;
import ai.koog.prompt.message.Message;
import org.example.koog.java.tools.AccountReadTools;
import org.example.koog.java.tools.AccountWriteTools;
import org.example.koog.java.tools.CommunicationTools;

// Unused example, shows how to build functional agents:
public class FunctionalAgentExample {
    private void defineAgent(String userId) {
        var functionalAgent = AIAgent.builder()
                .llmModel(OpenAIModels.Chat.GPT5_2Pro)
                .systemPrompt("You're a helpful banking assistant with access to all banking tools. Help the user")
                .toolRegistry(
                        ToolRegistry.builder()
                                .tools(new CommunicationTools())
                                .tools(new AccountReadTools(userId))
                                .tools(new AccountWriteTools(userId))
                                .build()
                )
                .promptExecutor(PromptExecutor.builder()
                        .openAI("OPENAI_KEY")
                        .anthropic("ANTHROPIC_KEY")
                        .ollama("https://my.custom/url/of/self-hosted-ollama")
                        .build()
                )
                .functionalStrategy("simple", (AIAgentFunctionalContext ctx, String input) -> {
                    var response = ctx.requestLLM(input);

                    while (true) {
                        if (response instanceof Message.Assistant)
                            return response.getContent();
                        else if (response instanceof Message.Tool.Call) {
                            ctx.executeTool((Message.Tool.Call) response);
                            response = ctx.requestLLM(input);
                        }
                    }
                })
                .build();

        functionalAgent.run("What is my current balance?");
    }
}

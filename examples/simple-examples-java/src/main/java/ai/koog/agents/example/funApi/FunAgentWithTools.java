package ai.koog.agents.example.funApi;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.features.eventHandler.feature.EventHandler;
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor;
import ai.koog.prompt.executor.ollama.client.OllamaClient;
import ai.koog.prompt.executor.ollama.client.OllamaModels;
import ai.koog.prompt.message.Message;

import java.util.List;

/**
 * This example demonstrates how to create a functional agent with tools using the Java API.
 * The agent handles tool calls in a loop until the LLM produces a final assistant response.
 */
public class FunAgentWithTools {

    public static void main(String[] args) throws Exception {
        SingleLLMPromptExecutor promptExec = new SingleLLMPromptExecutor(new OllamaClient());
        Switch theSwitch = new Switch();

        AIAgent<String, String> functionalAgent = AIAgent.<String, String>builder()
                .promptExecutor(promptExec)
                .llmModel(OllamaModels.Meta.LLAMA_3_2)
                .systemPrompt("You're responsible for running a Switch device and perform operations on it by request.")
                .toolRegistry(
                        ToolRegistry.builder()
                                .tools(new SwitchTools(theSwitch))
                                .build()
                )
                .functionalStrategy("funStrategy", (AIAgentFunctionalContext context, String input) -> {
                    List<Message.Response> responses = context.requestLLMMultiple(input);

                    while (responses.stream().anyMatch(r -> r instanceof Message.Tool.Call)) {
                        List<Message.Tool.Call> tools = context.extractToolCalls(responses);

                        if (context.latestTokenUsage() > 100500) {
                            context.compressHistory();
                        }

                        var results = context.executeMultipleTools(tools, false);
                        responses = context.sendMultipleToolResults(results);
                    }

                    return responses.get(0).getContent();
                })
                .install(EventHandler.Feature, config -> {
                    config.onToolCallStarting(eventContext -> {
                        System.out.println("Tool called: tool " + eventContext.getToolName()
                                + ", args " + eventContext.getToolArgs());
                    });
                })
                .build();

        functionalAgent.run("Turn switch on");
        System.out.println("Switch is " + (theSwitch.isOn() ? "on" : "off"));
    }
}

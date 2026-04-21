package org.example.koog.java.agents.examples;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.entity.AIAgentEdge;
import ai.koog.agents.core.agent.entity.AIAgentNode;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.model.PromptExecutor;
import ai.koog.prompt.message.Message;
import org.example.koog.java.tools.AccountReadTools;
import org.example.koog.java.tools.AccountWriteTools;
import org.example.koog.java.tools.CommunicationTools;

// Unused example, shows how to build functional agents:
public class SimpleGraphAgentExample {
    private void defineAgent(String userId) {
        var simpleGraphAgent = AIAgent.builder()
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
                .graphStrategy("simple", builder -> {
                    var graph = builder
                            .withInput(String.class)
                            .withOutput(String.class);

                    var askLLM = AIAgentNode.llmRequest(); // AIAgentNode<String, Message.Response>
                    var executeTool = AIAgentNode.executeTool(); // AIAgentNode<Message.Tool.Call, ReceivedToolResult>
                    var decide = AIAgentNode.doNothing(Message.Response.class); // AIAgentNode<Message.Response, Message.Response>
                    var sendToolResult = AIAgentNode.llmSendToolResult(); // AIAgentNode<ReceivedToolResult, Message.Response>

                    // 1. ask LLM what to do:
                    graph.edge(graph.nodeStart, askLLM);
                    // 2. then, decide what to do, depending on the response type
                    graph.edge(askLLM, decide);
                    // 3. if LLM returned Message.Tool.Call -- execute the tool
                    graph.edge(
                            AIAgentEdge.builder()
                                    .from(decide)
                                    .to(executeTool)
                                    .onIsInstance(Message.Tool.Call.class)
                                    .build()
                    );
                    // 4. if LLM returned Message.Assistant (textual response) -- return it's content and finish the agent.
                    graph.edge(
                            AIAgentEdge.builder()
                                    .from(decide)
                                    .to(graph.nodeFinish)
                                    .onIsInstance(Message.Assistant.class)
                                    .transformed(Message.Assistant::getContent)
                                    .build()
                    );
                    // 5. after executing each tool -- send tool result back to llm
                    graph.edge(executeTool, sendToolResult);
                    // 6. and then -- again, decide what to do, depending on the LLM's next answer
                    graph.edge(sendToolResult, decide);

                    return graph.build();
                })
                .build();

        simpleGraphAgent.run("What is my current balance?");
    }
}

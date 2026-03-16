package ai.koog.agents.example.chess;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.entity.AIAgentEdge;
import ai.koog.agents.core.agent.entity.AIAgentNode;
import ai.koog.agents.core.environment.ReceivedToolResult;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.message.Message;
import me.kpavlov.finchly.TestEnvironment;

/**
 * Chess agent that plays chess using the koog AI framework.
 * The agent uses the Move tool to make chess moves.
 *
 * <p>Requires the {@code OPENAI_API_KEY} environment variable to be set.
 */
public class Chess {

    public static void main(String[] args) {
        String apiKey = TestEnvironment.INSTANCE.get("OPENAI_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY environment variable is not set");
        }

        ChessGame game = new ChessGame();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(new MultiLLMPromptExecutor(new OpenAILLMClient(apiKey)))
            .llmModel(OpenAIModels.Chat.O3Mini)
            .systemPrompt(
                "You are an agent who plays chess.\n" +
                    "You should always propose a move in response to the \"Your move!\" message.\n" +
                    "\n" +
                    "DO NOT HALLUCINATE!!!\n" +
                    "DO NOT PLAY ILLEGAL MOVES!!!\n" +
                    "YOU CAN SEND A MESSAGE ONLY IF IT IS A RESIGNATION OR A CHECKMATE!!!"
            )
            .temperature(0.0)
            .toolRegistry(
                ToolRegistry.builder()
                    .tools(new ChessGameTools(game))
                    .build()
            )
            .graphStrategy("minimal", b -> {
                var graph = b
                    .withInput(String.class)
                    .withOutput(String.class);

                var nodeCallLLM = AIAgentNode.llmRequest();
                var nodeExecuteTool = AIAgentNode.executeTool();
                var nodeSendToolResult = AIAgentNode.llmSendToolResult();

                var nodeTrimHistory = AIAgentNode.builder("node")
                    .withInput(ReceivedToolResult.class)
                    .withOutput(ReceivedToolResult.class)
                    .withAction((input, ctx) ->
                        ctx.getLlm().writeSession(session -> {
                            // TODO: implement prompt trimming
                            session.rewritePrompt(prompt -> prompt);
                            return input;
                        }))
                    .build();

                AIAgentEdge.builder()
                    .from(graph.nodeStart)
                    .to(nodeCallLLM)
                    .build();

                AIAgentEdge.builder()
                    .from(nodeCallLLM)
                    .to(nodeExecuteTool)
                    .onIsInstance(Message.Tool.Call.class)
                    .build();

                AIAgentEdge.builder()
                    .from(nodeCallLLM)
                    .to(graph.nodeFinish)
                    .onIsInstance(Message.Assistant.class)
                    .transformed(Message.Assistant::getContent)
                    .build();

                AIAgentEdge.builder()
                    .from(nodeExecuteTool)
                    .to(nodeTrimHistory)
                    .build();

                AIAgentEdge.builder()
                    .from(nodeTrimHistory)
                    .to(nodeSendToolResult)
                    .build();

                AIAgentEdge.builder()
                    .from(nodeSendToolResult)
                    .to(graph.nodeFinish)
                    .onIsInstance(Message.Assistant.class)
                    .transformed(Message.Assistant::getContent)
                    .build();

                return graph.build();
            })
            .maxIterations(200)
            .build();

        System.out.println("Chess Game started!");

        String initialMessage = "Starting position is " + game.getBoard() + ". White to move!";
        agent.run(initialMessage);
    }
}

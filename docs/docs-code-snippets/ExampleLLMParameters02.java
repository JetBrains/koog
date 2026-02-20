import ai.koog.agents.core.agent.ToolCalls;
import ai.koog.agents.core.dsl.builder.GraphBuilder;
import ai.koog.agents.core.dsl.builder.subgraph.SubgraphWithTask;
import ai.koog.agents.ext.tool.SayToUser;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.params.LLMParams;

import java.util.Arrays;

public class ExampleLLMParameters02 {
    public static void main(String[] args) {
        SayToUser searchTool = SayToUser.INSTANCE;
        SayToUser calculatorTool = SayToUser.INSTANCE;
        SayToUser weatherTool = SayToUser.INSTANCE;

        // FAILED: SubgraphWithTask is a Kotlin DSL construct that relies on suspend functions and Kotlin-specific builders.
        // The Java API does not provide a direct equivalent for `subgraphWithTask` with all these parameters.
        // You would need to use the lower-level SubgraphBuilder API or define custom nodes in Java.
        /*
        SubgraphWithTask<String, String> processQuery = SubgraphBuilder.createWithTask(
            Arrays.asList(searchTool, calculatorTool, weatherTool),
            OpenAIModels.Chat.GPT4o,
            new LLMParams(
                0.7,         // temperature
                500,         // maxTokens
                1,           // numberOfChoices
                null,        // speculation
                null,        // schema
                LLMParams.ToolChoice.Auto.INSTANCE, // toolChoice
                null,        // user
                null         // additionalProperties
            ),
            ToolCalls.SEQUENTIAL,
            3,
            userQuery -> String.format("""
                You are a helpful assistant that can answer questions about various topics.
                Please help with the following query:
                %s
                """, userQuery)
        );
        */
    }
}

package ai.koog.agents.example.subgraphs;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy;
import ai.koog.agents.core.agent.entity.AIAgentSubgraph;
import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;
import ai.koog.agents.example.ApiKeyService;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.model.PromptExecutor;

import java.util.Collections;

/**
 * Example demonstrating a multi-subgraph agent strategy using the Java API.
 * <p>
 * The agent uses three subgraphs connected in sequence:
 * <ol>
 *   <li><b>Research subgraph</b>: gathers information about the topic using a web search tool</li>
 *   <li><b>Plan subgraph</b>: creates a structured outline based on the research</li>
 *   <li><b>Write subgraph</b>: produces a final summary based on the plan</li>
 * </ol>
 * <p>
 * Usage:
 * <pre>{@code
 * ./gradlew runExampleCustomSubgraph
 * }</pre>
 */
public class CustomSubgraphExample {

    static class WebSearchToolSet implements ToolSet {

        @Tool
        @LLMDescription("Search the web for information about the given query and return a summary of the results")
        @SuppressWarnings("unused")
        public String webSearch(
            @LLMDescription("The search query") String query
        ) {
            // Simulate a web search result
            return "Search results for '" + query + "': Found relevant articles covering key aspects of the topic.";
        }
    }

    public static void main(String[] args) {
        var promptExecutor = PromptExecutor.builder()
            .openAI(ApiKeyService.getOpenAIApiKey())
            .build();

        // Define the graph structure
        var strategyBuilder = AIAgentGraphStrategy.builder("research-assistant")
            .withInput(String.class)
            .withOutput(String.class);

        // Research the topic using a web search tool
        var researchSubgraph = AIAgentSubgraph.builder("research")
            .limitedTools(new WebSearchToolSet())
            .withInput(String.class)
            .withOutput(String.class)
            .withTask(topic ->
                "You are a research assistant. Search for information about the following topic " +
                    "and provide a concise summary of what you find: " + topic
            )
            .build();

        // Create a structured outline based on the research
        var planSubgraph = AIAgentSubgraph.builder("plan")
            .limitedTools(Collections.emptyList())
            .withInput(String.class)
            .withOutput(String.class)
            .withTask(research ->
                "You are an expert writer. Based on the following research, create a structured " +
                    "outline with 3-5 key points for a short article:\n" + research
            )
            .build();

        // Write the final summary based on the outline
        var writeSubgraph = AIAgentSubgraph.builder("summary")
            .limitedTools(Collections.emptyList())
            .withInput(String.class)
            .withOutput(String.class)
            .withTask(outline ->
                "You are a skilled writer. Using the following outline, write a concise and " +
                    "informative summary (2-3 paragraphs):\n" + outline
            )
            .build();

        // Connect the nodes in a graph to define a strategy
        var strategy = strategyBuilder
            .edge(strategyBuilder.nodeStart, researchSubgraph)
            .edge(researchSubgraph, planSubgraph)
            .edge(planSubgraph, writeSubgraph)
            .edge(writeSubgraph, strategyBuilder.nodeFinish)
            .build();

        var agent = AIAgent.builder()
            .promptExecutor(promptExecutor)
            .llmModel(OpenAIModels.Chat.GPT4o)
            .graphStrategy(strategy)
            .maxIterations(50)
            .build();

        System.out.println("Research assistant started. Enter a topic to research: ");
        var topic = System.console() != null
            ? System.console().readLine()
            : "the history of artificial intelligence";

        System.out.println("Start research on a provided topic: '" + topic + "'");

        var summary = agent.run(topic, "research-assistant-session");
        System.out.println("Summary:\n" + summary);
    }
}

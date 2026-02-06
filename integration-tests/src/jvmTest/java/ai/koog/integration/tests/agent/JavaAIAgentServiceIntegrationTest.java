package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgentService;
import ai.koog.agents.core.agent.GraphAIAgent;
import ai.koog.agents.core.agent.GraphAIAgentService;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.integration.tests.base.KoogJavaTestBase;
import ai.koog.integration.tests.utils.JavaInteropUtils;
import ai.koog.integration.tests.utils.Models;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AIAgentService (multi-agent management).
 */
public class JavaAIAgentServiceIntegrationTest extends KoogJavaTestBase {

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_AIAgentServiceCreateAndListAgents(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        GraphAIAgentService<String, String> service = AIAgentService.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .build();

        GraphAIAgent<String, String> agent1 = service.createAgent("agent-1");
        GraphAIAgent<String, String> agent2 = service.createAgent("agent-2");

        assertNotNull(agent1);
        assertNotNull(agent2);
        assertEquals("agent-1", agent1.getId());
        assertEquals("agent-2", agent2.getId());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_AIAgentServiceAgentById(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        GraphAIAgentService<String, String> service = AIAgentService.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .build();

        GraphAIAgent<String, String> createdAgent = service.createAgent("test-agent");

        GraphAIAgent<String, String> retrievedAgent = service.agentById("test-agent");
        assertNotNull(retrievedAgent);
        assertEquals("test-agent", retrievedAgent.getId());
        assertEquals(createdAgent, retrievedAgent);

        GraphAIAgent<String, String> nonExistent = service.agentById("non-existent");
        assertNull(nonExistent);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_AIAgentServiceRemoveAgent(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        GraphAIAgentService<String, String> service = AIAgentService.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .build();

        // Test removing by agent instance
        GraphAIAgent<String, String> agent = service.createAgent("removable-agent");

        boolean removed = service.removeAgent(agent);
        assertTrue(removed);

        boolean removedAgain = service.removeAgent(agent);
        assertFalse(removedAgain);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_AIAgentServiceCreateAgentAndRun(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        GraphAIAgentService<String, String> service = AIAgentService.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .build();

        ToolRegistry emptyRegistry = ToolRegistry.builder().build();
        String result = service.createAgentAndRun("What is 2+2?", "one-shot-agent",
            emptyRegistry, service.getAgentConfig(), null, kotlinx.datetime.Clock.System.INSTANCE);

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_AIAgentServiceWithCustomToolRegistry(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        JavaInteropUtils.CalculatorTools calculator = new JavaInteropUtils.CalculatorTools();
        ToolRegistry serviceToolRegistry = JavaInteropUtils.createToolRegistry(calculator);

        GraphAIAgentService<String, String> service = AIAgentService.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a calculator assistant. Use tools when needed.")
            .toolRegistry(serviceToolRegistry)
            .build();

        GraphAIAgent<String, String> agent = service.createAgent("calculator-agent");

        String result = runBlocking(continuation -> agent.run("Calculate 10 + 15", null, continuation));
        assertNotNull(result);
        assertFalse(result.isBlank());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_AIAgentServiceBuilderConfiguration(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        GraphAIAgentService<String, String> service = AIAgentService.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .temperature(0.7)
            .maxIterations(5)
            .build();

        GraphAIAgent<String, String> agent = service.createAgent("agent-id");

        assertNotNull(agent);
        assertEquals(0.7, service.getAgentConfig().getPrompt().getParams().getTemperature());
        assertEquals(5, service.getAgentConfig().getMaxAgentIterations());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_BuilderWithCustomId(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        GraphAIAgentService<String, String> service = AIAgentService.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .build();

        GraphAIAgent<String, String> agent = service.createAgent("custom-test-id");

        assertNotNull(agent);
        assertEquals("custom-test-id", agent.getId());

        GraphAIAgent<String, String> retrievedAgent = service.agentById("custom-test-id");
        assertNotNull(retrievedAgent);
        assertEquals(agent, retrievedAgent);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_AIAgentServiceBuilderFunctionalStrategy(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        var service = AIAgentService.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .functionalStrategy((context, input) -> {
                String inputStr = (input instanceof String) ? (String) input : String.valueOf(input);
                Message.Response response = JavaInteropUtils.requestLLM(context, inputStr, true);
                if (response instanceof Message.Assistant) {
                    return JavaInteropUtils.getAssistantContent((Message.Assistant) response);
                }
                return "Unexpected response type";
            })
            .build();

        var agent = service.createAgent("functional-agent");

        String result = runBlocking(continuation -> agent.run("Say hello"));

        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.toLowerCase().contains("hello"));
    }
}

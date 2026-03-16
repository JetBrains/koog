package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.config.AIAgentConfig;
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext;
import ai.koog.agents.core.agent.entity.AIAgentStorageKey;
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy;
import ai.koog.agents.core.environment.ReceivedToolResult;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.features.eventHandler.feature.EventHandler;
import ai.koog.agents.snapshot.feature.Persistence;
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider;
import ai.koog.agents.snapshot.providers.file.JVMFilePersistenceStorageProvider;
import ai.koog.integration.tests.base.KoogJavaTestBase;
import ai.koog.integration.tests.utils.JavaUtils;
import ai.koog.integration.tests.utils.Models;
import ai.koog.integration.tests.utils.NumberTools;
import ai.koog.integration.tests.utils.SubgraphStrategies;
import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient;
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels;
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.llm.LLMCapability;
import ai.koog.prompt.llm.LLMProvider;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.AttachmentContent;
import ai.koog.prompt.message.ContentPart;
import ai.koog.prompt.message.Message;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class JavaAIAgentIntegrationTest extends KoogJavaTestBase {
    @SuppressWarnings("unused")
    public static Stream<Arguments> historyCompressionStrategies() {
        return JavaUtils.historyCompressionStrategiesForJava().stream().map(Arguments::of);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_NoErrorsWithoutSystemPrompt(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AtomicInteger errors = new AtomicInteger(0);
        AtomicBoolean completed = new AtomicBoolean(false);

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .maxIterations(10)
            .install(EventHandler.Feature, config -> {
                config.onAgentExecutionFailed(ctx -> errors.incrementAndGet());
                config.onAgentCompleted(ctx -> completed.set(true));
            }).build();

        String result = agent.run("Reply with one short greeting.");

        assertThat(result).isNotNull();
        assertThat(result.isBlank()).isFalse();
        assertThat(completed.get()).isTrue();
        assertThat(errors.get()).as("Agent without system prompt should not fail").isEqualTo(0);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    @Disabled("KG-734 Parameters from Java builder is not put into the effective prompt params")
    public void integration_ShouldSetPromptParamsViaBuilder(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .temperature(1.0)
            .maxIterations(10)
            .numberOfChoices(1)
            .build();


        assertThat(agent.getAgentConfig().getPrompt().getParams().getTemperature()).isEqualTo(1.0);
        assertThat(agent.getAgentConfig().getPrompt().getParams().getNumberOfChoices()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_ShouldFailOnMaxIterationsExhaustion(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        assumeTrue(model.supports(LLMCapability.Tools.INSTANCE), "Model must support tools");

        NumberTools numberTools = new NumberTools();
        AtomicInteger errors = new AtomicInteger(0);

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt(
                "You are a calculator assistant. You MUST call the multiply tool to answer."
            )
            .toolRegistry(ToolRegistry.builder().tools(numberTools).build())
            .maxIterations(1)
            .install(EventHandler.Feature, config ->
                config.onAgentExecutionFailed(ctx -> errors.incrementAndGet())
            )
            .build();

        Throwable failure = null;
        try {
            agent.run("What is 9 times 9?");
        } catch (Throwable ex) {
            failure = ex;
        }

        assertThat(failure != null || errors.get() > 0)
            .as("Expected max-iterations exhaustion to fail by exception or failure event")
            .isTrue();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_ShouldCallNoArgToolWithoutParams(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        assumeTrue(model.supports(LLMCapability.Tools.INSTANCE), "Model must support tools");

        NumberTools tools = new NumberTools();
        ToolRegistry registry = ToolRegistry.builder().tools(tools).build();
        List<String> calledTools = new CopyOnWriteArrayList<>();
        AtomicInteger errors = new AtomicInteger(0);
        AtomicBoolean completed = new AtomicBoolean(false);

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .toolRegistry(registry)
            .systemPrompt(
                "You are a tool-using assistant. You MUST call a tool."
            )
            .maxIterations(10)
            .install(EventHandler.Feature, config -> {
                config.onToolCallStarting(ctx -> calledTools.add(ctx.getToolName()));
                config.onAgentExecutionFailed(ctx -> errors.incrementAndGet());
                config.onAgentCompleted(ctx -> completed.set(true));
            })
            .build();

        String result = agent.run("Generate a random number.");

        assertThat(result).isNotNull();
        assertThat(result.isBlank()).isFalse();
        assertThat(calledTools.contains("generateRandomNumber")).as("Expected generateRandomNumber tool call").isTrue();
        assertThat(completed.get()).isTrue();
        assertThat(errors.get()).as("Run should complete without execution errors").isEqualTo(0);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_MultiLLMRouting(LLModel ignoredModel) {
        Models.assumeAvailable(LLMProvider.OpenAI);
        Models.assumeAvailable(LLMProvider.Anthropic);

        OpenAILLMClient openAIClient = new OpenAILLMClient(
            ai.koog.integration.tests.utils.TestCredentials.INSTANCE.readTestOpenAIKeyFromEnv()
        );
        AnthropicLLMClient anthropicClient = new AnthropicLLMClient(
            ai.koog.integration.tests.utils.TestCredentials.INSTANCE.readTestAnthropicKeyFromEnv()
        );
        resourcesToClose.add(openAIClient);
        resourcesToClose.add(anthropicClient);

        MultiLLMPromptExecutor executor = new MultiLLMPromptExecutor(openAIClient, anthropicClient);
        List<LLMProvider> providersSeen = new CopyOnWriteArrayList<>();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(executor)
            .llmModel(OpenAIModels.Chat.GPT5_1)
            .systemPrompt("You are a helpful assistant.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                Message.Response first = context.requestLLM("Reply the user", true);
                String second = context.subtask("Verify the answer")
                    .withInput(input)
                    .withOutput(String.class)
                    .useLLM(AnthropicModels.Opus_4_6)
                    .run();
                return first.getContent() + " | " + second;
            })
            .install(EventHandler.Feature, config ->
                config.onLLMCallStarting(ctx -> providersSeen.add(ctx.getModel().getProvider()))
            )
            .build();

        String result = agent.run("Hi. What's the full name of Napoleon?");
        assertThat(result).isNotNull();
        assertThat(result.isBlank()).isFalse();
        assertThat(Set.copyOf(providersSeen)).isEqualTo(Set.of(LLMProvider.OpenAI, LLMProvider.Anthropic));
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_SubgraphToolShouldReuseAgentTools(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        assumeTrue(model.supports(LLMCapability.Tools.INSTANCE), "Model must support tools");

        NumberTools numberTools = new NumberTools();
        ToolRegistry toolRegistry = ToolRegistry.builder().tools(numberTools).build();
        List<String> calledTools = new CopyOnWriteArrayList<>();
        AtomicInteger errors = new AtomicInteger(0);

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a calculator assistant. Use tools from the subgraph to solve the task.")
            .toolRegistry(toolRegistry)
            .graphStrategy(SubgraphStrategies.calculatorWithSubgraphs(model))
            .maxIterations(20)
            .install(EventHandler.Feature, config -> {
                config.onToolCallStarting(ctx -> calledTools.add(ctx.getToolName()));
                config.onAgentExecutionFailed(ctx -> errors.incrementAndGet());
            })
            .build();

        String result = agent.run("What's 15 + 25?");

        assertThat(result).isNotNull();
        assertThat(result.isBlank()).isFalse();
        assertThat(errors.get()).as("Subgraph workflow with registered tools should not fail").isEqualTo(0);
        assertThat(calledTools.contains("add") || calledTools.contains("sum"))
            .as("Expected at least one calculator tool call from subgraph flow")
            .isTrue();
    }

    @Test
    public void integration_SubgraphWithoutAgentToolsFallback() {
        LLModel model = OpenAIModels.Chat.GPT5_2;
        Models.assumeAvailable(model.getProvider());
        assumeTrue(model.supports(LLMCapability.Tools.INSTANCE), "Model must support tools");

        List<String> calledTools = new CopyOnWriteArrayList<>();
        AtomicInteger errors = new AtomicInteger(0);

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a calculator assistant. Use tools from the subgraph to solve the task.")
            .graphStrategy(SubgraphStrategies.calculatorWithSubgraphs(model))
            .maxIterations(20)
            .install(EventHandler.Feature, config -> {
                config.onToolCallStarting(ctx -> calledTools.add(ctx.getToolName()));
                config.onAgentExecutionFailed(ctx -> errors.incrementAndGet());
            })
            .build();

        String result = agent.run("What's 15 + 25?");

        assertThat(result).isNotNull();
        assertThat(result.isBlank()).isFalse();
        assertThat(errors.get())
            .as("Subgraph workflow without registered tools should not crash")
            .isEqualTo(0);
        assertThat(calledTools.size()).isEqualTo(0);
    }

    @ParameterizedTest
    @MethodSource("historyCompressionStrategies")
    public void integration_HistoryCompressionStrategiesWorkOnSingleRun(HistoryCompressionStrategy strategy) {
        LLModel model = OpenAIModels.Chat.GPT5_1;

        AtomicInteger errors = new AtomicInteger(0);
        AtomicInteger compressionsApplied = new AtomicInteger(0);
        AtomicReference<List<Message>> historyAfterCompression = new AtomicReference<>(List.of());

        Prompt prompt = Prompt.builder("java-history-compression")
            .system("You are a helpful assistant. Always remember the user is human.")
            .user("Hello!")
            .assistant("Hello there!")
            .system("Keep your responses concise.")
            .user("Please remember all prior instructions.")
            .build();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .agentConfig(
                AIAgentConfig.builder()
                    .model(model)
                    .prompt(prompt)
                    .maxAgentIterations(10)
                    .build()
            )
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                context.requestLLM("Acknowledge the context in one short sentence.", false);
                context.compressHistory(strategy, true);
                compressionsApplied.incrementAndGet();
                historyAfterCompression.set(new ArrayList<>(context.getHistory()));
                return context.requestLLM("Who am I according to instructions? Reply shortly.", false).getContent();
            })
            .install(EventHandler.Feature, config ->
                config.onAgentExecutionFailed(ctx -> errors.incrementAndGet())
            )
            .build();

        String result = agent.run("Who am I");

        assertThat(result).isNotNull();
        assertThat(result.isBlank()).isFalse();
        assertThat(errors.get()).as("No execution errors expected").isEqualTo(0);
        assertThat(compressionsApplied.get()).as("Strategy compression should be applied exactly once").isEqualTo(1);
        assertThat(historyAfterCompression.get().stream().anyMatch(m -> m instanceof Message.System))
            .as("Compressed history should preserve at least one system message")
            .isTrue();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_HistoryCompressionSupportsBeforeAndAfterToolResult(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        assumeTrue(model.supports(LLMCapability.Tools.INSTANCE), "Model must support tools");

        NumberTools numberTools = new NumberTools();
        AtomicInteger errors = new AtomicInteger(0);
        AtomicInteger toolCalls = new AtomicInteger(0);
        AtomicInteger beforeCompressCalls = new AtomicInteger(0);
        AtomicInteger afterCompressCalls = new AtomicInteger(0);
        AtomicReference<List<Message>> historyAfterCompression = new AtomicReference<>(List.of());

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt(
                "You are a calculator assistant. You MUST call multiply exactly once to answer."
            )
            .toolRegistry(ToolRegistry.builder().tools(numberTools).build())
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                Message.Response response = context.requestLLM("Calculate 7 times 2. Use multiply tool.", true);
                if (!(response instanceof Message.Tool.Call)) {
                    return response.getContent();
                }

                Message.Tool.Call toolCall = (Message.Tool.Call) response;
                ReceivedToolResult toolResult = context.executeTool(toolCall);

                context.compressHistory(HistoryCompressionStrategy.WholeHistory, true);
                beforeCompressCalls.incrementAndGet();

                Message.Response afterToolResult = context.sendToolResult(toolResult);

                context.compressHistory(HistoryCompressionStrategy.FromLastNMessages(2), true);
                afterCompressCalls.incrementAndGet();
                historyAfterCompression.set(new ArrayList<>(context.getHistory()));

                return afterToolResult.getContent();
            })
            .install(EventHandler.Feature, config -> {
                config.onToolCallStarting(ctx -> toolCalls.incrementAndGet());
                config.onAgentExecutionFailed(ctx -> errors.incrementAndGet());
            })
            .build();

        String result = agent.run("start");

        assertThat(result).isNotNull();
        assertThat(result.isBlank()).isFalse();
        assertThat(errors.get()).as("No execution errors expected").isEqualTo(0);
        assertThat(toolCalls.get() >= 1).as("Expected at least one tool call").isTrue();
        assertThat(beforeCompressCalls.get()).as("Compression before tool result should be called once").isEqualTo(1);
        assertThat(afterCompressCalls.get()).as("Compression after tool result should be called once").isEqualTo(1);
        assertThat(historyAfterCompression.get().stream().anyMatch(m -> m instanceof Message.System))
            .as("History should preserve system message after compression around tool result")
            .isTrue();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#modelsWithVisionCapability")
    public void integration_AgentSupportsVisionBase64(LLModel model) throws Exception {
        Models.assumeAvailable(model.getProvider());
        assumeTrue(model.supports(LLMCapability.Vision.Image.INSTANCE), "Model must support vision capability");

        byte[] imageBytes;
        try (InputStream stream = JavaAIAgentIntegrationTest.class.getResourceAsStream("/media/test.png")) {
            assertThat(stream).as("Expected /media/test.png test resource").isNotNull();
            imageBytes = stream.readAllBytes();
        }
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        String promptWithImage = "Analyze this image and tell the format: data:image/png," + base64Image;

        AtomicInteger errors = new AtomicInteger(0);
        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You analyze images. Reply in one short sentence.")
            .maxIterations(10)
            .install(EventHandler.Feature, config ->
                config.onAgentExecutionFailed(ctx -> errors.incrementAndGet())
            )
            .build();

        String result = agent.run(promptWithImage);

        assertThat(result).isNotNull();
        assertThat(result.isBlank()).isFalse();
        assertThat(errors.get()).as("Vision base64 prompt should not fail").isEqualTo(0);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#modelsWithVisionCapability")
    public void integration_AgentSupportsVisionURLImage(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        assumeTrue(model.supports(LLMCapability.Vision.Image.INSTANCE), "Model must support vision capability");

        AtomicInteger errors = new AtomicInteger(0);
        Prompt prompt = Prompt.builder("java-vision-url-image-part")
            .system("You analyze images. Keep answers short.")
            .user(List.of(
                new ContentPart.Text("Please identify the image format."),
                new ContentPart.Image(
                    new AttachmentContent.URL(
                        "https://upload.wikimedia.org/wikipedia/commons/4/47/PNG_transparency_demonstration_1.png"
                    ),
                    "png",
                    "image/png",
                    "PNG_transparency_demonstration_1.png"
                )
            ))
            .build();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .agentConfig(
                AIAgentConfig.builder()
                    .model(model)
                    .prompt(prompt)
                    .maxAgentIterations(10)
                    .build()
            )
            .functionalStrategy((AIAgentFunctionalContext context, String input) ->
                context.requestLLM("Answer the user", false).getContent()
            )
            .install(EventHandler.Feature, config ->
                config.onAgentExecutionFailed(ctx -> errors.incrementAndGet())
            )
            .build();

        String result = agent.run("Identify the image format from the image part in history.");
        assertThat(result).isNotNull();
        assertThat(result.isBlank()).isFalse();
        assertThat(errors.get()).as("Vision URL image-part should not fail").isEqualTo(0);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_PersistenceInMemoryProvider(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        InMemoryPersistenceStorageProvider provider = new InMemoryPersistenceStorageProvider();
        String sessionId = "java-checkpoint-in-memory-" + UUID.randomUUID();

        AIAgent<String, String> firstAgent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are concise.")
            .maxIterations(10)
            .install(Persistence.Feature, config -> {
                config.setStorage(provider);
                config.setEnableAutomaticPersistence(true);
            })
            .build();

        String firstResult = firstAgent.run("Say hello and keep it short.", sessionId);
        List<?> checkpointsAfterFirstRun = JavaUtils.getCheckpointsBlocking(provider, sessionId);

        AIAgent<String, String> restoredAgent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are concise.")
            .maxIterations(10)
            .install(Persistence.Feature, config -> {
                config.setStorage(provider);
                config.setEnableAutomaticPersistence(true);
            })
            .build();

        String secondResult = restoredAgent.run("Continue.", sessionId);
        List<?> checkpointsAfterSecondRun = JavaUtils.getCheckpointsBlocking(provider, sessionId);

        assertThat(firstResult).isNotNull();
        assertThat(firstResult.isBlank()).isFalse();
        assertThat(secondResult).isNotNull();
        assertThat(secondResult.isBlank()).isFalse();
        assertThat(checkpointsAfterFirstRun.isEmpty()).as("Expected checkpoints after first run").isFalse();
        assertThat(checkpointsAfterSecondRun.size() >= checkpointsAfterFirstRun.size())
            .as("Second run should keep or increase checkpoint count for same session")
            .isTrue();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_PersistenceInFileProvider(
        LLModel model,
        @TempDir Path tempDir
    ) {
        Models.assumeAvailable(model.getProvider());

        JVMFilePersistenceStorageProvider fileProvider = new JVMFilePersistenceStorageProvider(tempDir);
        String sessionId = "java-checkpoint-file-" + UUID.randomUUID();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are concise.")
            .maxIterations(10)
            .install(Persistence.Feature, config -> {
                config.setStorage(fileProvider);
                config.setEnableAutomaticPersistence(true);
            })
            .build();

        String result = agent.run("Say hello from file persistence test.", sessionId);
        List<?> checkpoints = JavaUtils.getCheckpointsBlocking(fileProvider, sessionId);

        assertThat(result).isNotNull();
        assertThat(result.isBlank()).isFalse();
        assertThat(checkpoints.isEmpty()).as("Expected checkpoints persisted in file provider").isFalse();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_ShouldStoreAndRetrieveValueWithStorageKeys(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgentStorageKey<String> storageKey = new AIAgentStorageKey<>("test-key");
        String expectedValue = "test-value";
        AtomicReference<String> retrievedValue = new AtomicReference<>();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                JavaUtils.storageSet(context.getStorage(), storageKey, expectedValue);
                retrievedValue.set(JavaUtils.storageGet(context.getStorage(), storageKey));
                return context.requestLLM(input, true).getContent();
            })
            .build();

        runBlocking(continuation -> agent.run("Hello", null, continuation));
        assertThat(retrievedValue.get()).isEqualTo(expectedValue);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_ShouldReturnNullForNotExistentKey(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgentStorageKey<String> nonExistentKey = new AIAgentStorageKey<>("non-existent");
        AtomicReference<String> retrievedValue = new AtomicReference<>("not-null");

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                retrievedValue.set(JavaUtils.storageGet(context.getStorage(), nonExistentKey));
                return context.requestLLM(input, true).getContent();
            })
            .build();

        runBlocking(continuation -> agent.run("Hello", null, continuation));
        assertThat(retrievedValue.get()).isNull();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_ShouldOverwriteValueForSameKey(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgentStorageKey<String> storageKey = new AIAgentStorageKey<>("overwrite-key");
        AtomicReference<String> firstRead = new AtomicReference<>();
        AtomicReference<String> secondRead = new AtomicReference<>();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                JavaUtils.storageSet(context.getStorage(), storageKey, "initial");
                firstRead.set(JavaUtils.storageGet(context.getStorage(), storageKey));
                JavaUtils.storageSet(context.getStorage(), storageKey, "updated");
                secondRead.set(JavaUtils.storageGet(context.getStorage(), storageKey));
                return context.requestLLM(input, true).getContent();
            })
            .build();

        runBlocking(continuation -> agent.run("Hello", null, continuation));
        assertThat(firstRead.get()).isEqualTo("initial");
        assertThat(secondRead.get()).isEqualTo("updated");
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_ShouldSupportKeysWithDifferentTypes(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgentStorageKey<String> stringKey = new AIAgentStorageKey<>("string-key");
        AIAgentStorageKey<Integer> intKey = new AIAgentStorageKey<>("int-key");
        AtomicReference<String> retrievedString = new AtomicReference<>();
        AtomicReference<Integer> retrievedInt = new AtomicReference<>();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                JavaUtils.storageSet(context.getStorage(), stringKey, "test-string");
                JavaUtils.storageSet(context.getStorage(), intKey, 42);
                retrievedString.set(JavaUtils.storageGet(context.getStorage(), stringKey));
                retrievedInt.set(JavaUtils.storageGet(context.getStorage(), intKey));
                return context.requestLLM(input, true).getContent();
            })
            .build();

        runBlocking(continuation -> agent.run("Hello", null, continuation));
        assertThat(retrievedString.get()).isEqualTo("test-string");
        assertThat(retrievedInt.get()).isEqualTo(42);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_ShouldIsolateStorageForMultipleRuns(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgentStorageKey<Integer> runCountKey = new AIAgentStorageKey<>("run-count");
        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                Integer existing = JavaUtils.storageGet(context.getStorage(), runCountKey);
                int newCount = existing == null ? 1 : existing + 1;
                JavaUtils.storageSet(context.getStorage(), runCountKey, newCount);
                context.requestLLM(input, true);
                return String.valueOf(newCount);
            })
            .build();

        String firstResult = runBlocking(continuation -> agent.run("First hello", null, continuation));
        String secondResult = runBlocking(continuation -> agent.run("Second hello", null, continuation));

        assertThat(Integer.parseInt(firstResult)).isEqualTo(1);
        assertThat(Integer.parseInt(secondResult)).isEqualTo(1);
    }

    @Test
    public void integration_ThrowError() {
        var model = OpenAIModels.Chat.GPT5_1;
        Models.assumeAvailable(model.getProvider());

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                if (input != null) {
                    throw new RuntimeException("Intentional error from functional strategy");
                }
                return "Should not reach here";
            })
            .build();

        assertThatThrownBy(() ->
            runBlocking(continuation -> agent.run("Test", null, continuation))
        )
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Intentional error from functional strategy");
    }
}

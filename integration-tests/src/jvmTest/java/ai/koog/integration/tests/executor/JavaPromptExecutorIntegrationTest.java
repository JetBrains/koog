package ai.koog.integration.tests.executor;

import ai.koog.agents.core.tools.ToolDescriptor;
import ai.koog.integration.tests.base.KoogJavaTestBase;
import ai.koog.integration.tests.utils.JavaUtils;
import ai.koog.integration.tests.utils.Models;
import ai.koog.integration.tests.utils.structuredOutput.WeatherReport;
import ai.koog.integration.tests.utils.structuredOutput.WeatherReportKt;
import ai.koog.integration.tests.utils.tools.*;
import ai.koog.prompt.message.MessagePart;
import ai.koog.prompt.params.LLMParams;
import ai.koog.prompt.Prompt;
import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort;
import ai.koog.prompt.executor.clients.openai.models.OpenAIInclude;
import ai.koog.prompt.executor.clients.openai.models.ReasoningConfig;
import ai.koog.prompt.executor.clients.openai.models.ReasoningSummary;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.executor.model.PromptExecutorStructuredKt;
import ai.koog.prompt.llm.LLMCapability;
import ai.koog.prompt.llm.LLMProvider;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message;
import ai.koog.prompt.message.RequestMetaInfo;
import ai.koog.prompt.message.ResponseMetaInfo;
import ai.koog.prompt.streaming.StreamFrame;
import ai.koog.prompt.structure.StructuredRequestConfig;
import ai.koog.prompt.structure.StructuredResponse;
import kotlinx.serialization.json.JsonObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import kotlin.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.assertj.core.api.Assertions.fail;

public class JavaPromptExecutorIntegrationTest extends KoogJavaTestBase {

    private static String textContent(Message m) {
        return m.getParts().stream()
            .filter(p -> p instanceof MessagePart.Text)
            .map(p -> ((MessagePart.Text) p).getText())
            .collect(Collectors.joining("\n"));
    }

    /**
     * Joins all {@link MessagePart.Text} and {@link MessagePart.Reasoning} content from a
     * {@link Message.Assistant} into a single string, mirroring the behaviour of the removed
     * {@code JavaUtils.mergeAssistantAndReasoningContent}.
     */
    private static String mergeAssistantAndReasoningContent(Message.Assistant response) {
        return response.getParts().stream()
            .map(p -> {
                if (p instanceof MessagePart.Text) {
                    return ((MessagePart.Text) p).getText();
                }
                if (p instanceof MessagePart.Reasoning) {
                    return String.join("\n", ((MessagePart.Reasoning) p).getContent());
                }
                return "";
            })
            .filter(s -> !s.isEmpty())
            .collect(Collectors.joining("\n"));
    }

    private static final class ToolSchemaCase {
        private final String id;
        private final ToolDescriptor descriptor;
        private final String userPrompt;
        private final String expectedToolName;
        private final List<String> expectedSnippets;

        private ToolSchemaCase(
            String id,
            ToolDescriptor descriptor,
            String userPrompt,
            String expectedToolName,
            List<String> expectedSnippets
        ) {
            this.id = id;
            this.descriptor = descriptor;
            this.userPrompt = userPrompt;
            this.expectedToolName = expectedToolName;
            this.expectedSnippets = expectedSnippets;
        }

        private String id() {
            return id;
        }

        private ToolDescriptor descriptor() {
            return descriptor;
        }

        private String userPrompt() {
            return userPrompt;
        }

        private String expectedToolName() {
            return expectedToolName;
        }

        private List<String> expectedSnippets() {
            return expectedSnippets;
        }
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
    public void integration_shouldExecutePrompt(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        assertThat(model.getProvider()).isNotNull();

        MultiLLMPromptExecutor executor = createExecutor(model);
        Prompt prompt = Prompt.builder("test-prompt")
            .system("You are a calculator.")
            .user("What is 2+2?")
            .assistant("Shall I answer in a number or in a word?")
            .user("In a word please")
            .build();

        assertThat(prompt.getMessages().get(0)).isInstanceOf(Message.System.class);
        assertThat(prompt.getMessages().get(1)).isInstanceOf(Message.User.class);
        assertThat(prompt.getMessages().get(2)).isInstanceOf(Message.Assistant.class);
        assertThat(prompt.getMessages().get(3)).isInstanceOf(Message.User.class);

        Message.Assistant response = executor.execute(prompt, model);
        assertThat(response).isInstanceOf(Message.Assistant.class);

        String content = textContent(response);
        assertThat(content.toLowerCase()).contains("four");
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.utils.Models#reasoningCapableModels")
    public void integration_ReasoningExecuteShouldSucceed(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        assumeTrue(
            model.getProvider() == LLMProvider.OpenAI || model.getProvider() == LLMProvider.Anthropic,
            "Reasoning Java interop test currently supports only OpenAI/Anthropic providers in KoogJavaTestBase"
        );
        MultiLLMPromptExecutor executor = createExecutor(model);

        LLMParams params = JavaUtils.createParams(
            model.getProvider(),
            null,
            null,
            List.of(OpenAIInclude.REASONING_ENCRYPTED_CONTENT),
            new ReasoningConfig(ReasoningEffort.MEDIUM, ReasoningSummary.AUTO),
            256
        );

        Prompt prompt = Prompt.builder("java-interop-reasoning-exec")
            .system("You are a helpful assistant.")
            .user("Think step by step and compute 15 * 23 + 8.")
            .build()
            .withParams(params);

        Message.Assistant response = executor.execute(prompt, model);
        assertThat(response).isNotNull();
        assertThat(response.getParts().stream().anyMatch(p ->
            p instanceof MessagePart.Text || p instanceof MessagePart.Reasoning
        )).isTrue();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.utils.Models#reasoningCapableModels")
    public void integration_ReasoningWithEncryptionShouldContainEncryptedReasoning(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        MultiLLMPromptExecutor executor = createExecutor(model);

        LLMParams params = JavaUtils.createReasoningStreamingParams(model.getProvider(), 1056);

        Prompt prompt = Prompt.builder("java-interop-reasoning-encryption")
            .system("You are a helpful assistant. Think carefully.")
            .user("How much wood I need to build a house? Think step by step.")
            .build()
            .withParams(params);

        Message.Assistant response = executor.execute(prompt, model);
        assertThat(response).isNotNull();

        List<MessagePart.Reasoning> reasoningParts = response.getParts().stream()
            .filter(MessagePart.Reasoning.class::isInstance)
            .map(MessagePart.Reasoning.class::cast)
            .collect(Collectors.toList());

        assertThat(reasoningParts.stream().anyMatch(r -> r.getEncrypted() != null && !r.getEncrypted().isBlank()))
            .isTrue();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.utils.Models#reasoningCapableModels")
    public void integration_ReasoningStreamingShouldEndWithEndFrame(LLModel model) throws InterruptedException {
        Models.assumeAvailable(model.getProvider());
        MultiLLMPromptExecutor executor = createExecutor(model);

        LLMParams params = JavaUtils.createReasoningStreamingParams(model.getProvider(), 256);

        Prompt prompt = Prompt.builder("java-interop-reasoning-stream")
            .system("You are a helpful assistant.")
            .user("How much wood I need to build a house? Think step by step.")
            .build()
            .withParams(params);

        JavaUtils.StreamCollectionResult result =
            JavaUtils.collectFrames(executor.executeStreaming(prompt, model));

        if (result.getError() != null) {
            fail("Streaming failed with error: " + result.getError().getClass().getSimpleName() + " - " + result.getError().getMessage());
        }

        boolean hasEndFrame = result.getFrames().stream().anyMatch(frame -> frame instanceof StreamFrame.End);
        assertThat(hasEndFrame).isTrue();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.utils.Models#reasoningCapableModels")
    @Disabled("KG-733 [Java API] OpenAILLMClient error: 'reasoning' is provided without its required following item")
    public void integration_ReasoningMultiStepShouldSucceed(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        MultiLLMPromptExecutor executor = createExecutor(model);

        LLMParams params = JavaUtils.createReasoningStreamingParams(model.getProvider(), 1056);

        Prompt firstPrompt = Prompt.builder("java-interop-reasoning-multistep-1")
            .system("You are a helpful assistant.")
            .user("How much wood I need to build a house? Think step by step.")
            .build()
            .withParams(params);

        Message.Assistant firstResponse = executor.execute(firstPrompt, model);
        assertThat(firstResponse).isNotNull();
        assertThat(firstResponse.getParts().stream().anyMatch(MessagePart.Reasoning.class::isInstance)).isTrue();

        List<Message> secondTurnMessages = new ArrayList<>(firstPrompt.getMessages());
        secondTurnMessages.add(firstResponse);
        secondTurnMessages.add(
            new Message.User(
                "Summarize the result in 2 sentences.",
                new RequestMetaInfo(Instant.Companion.getDISTANT_PAST(), null)
            )
        );
        Prompt secondPrompt = new Prompt(secondTurnMessages, "java-interop-reasoning-multistep-2", params);

        Message.Assistant secondResponse = executor.execute(secondPrompt, model);
        assertThat(secondResponse).isNotNull();
        String answer = mergeAssistantAndReasoningContent(secondResponse);
        assertThat(answer).isNotBlank();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.utils.Models#reasoningCapableModels")
    @Disabled("KG-735 GoogleLLMClient.executeStreaming() does not map Google thought signals into reasoning StreamFrames, " +
        "KG-726 Responses from several OpenAI models are completing without receiving an End frame")
    public void integration_ReasoningStreamingShouldContainReasoningFrames(LLModel model) throws InterruptedException {
        Models.assumeAvailable(model.getProvider());
        MultiLLMPromptExecutor executor = createExecutor(model);

        LLMParams params = JavaUtils.createReasoningStreamingParams(model.getProvider(), 1056);

        Prompt prompt = Prompt.builder("java-interop-reasoning-stream-frames")
            .system("You are a helpful assistant.")
            .user("How much wood I need to build a house? Think step by step.")
            .build()
            .withParams(params);

        JavaUtils.StreamCollectionResult result =
            JavaUtils.collectFrames(executor.executeStreaming(prompt, model));
        if (result.getError() != null) {
            fail("Streaming failed with error: " + result.getError().getClass().getSimpleName() + " - " + result.getError().getMessage());
        }

        boolean hasReasoning = result.getFrames().stream()
            .anyMatch(frame -> frame instanceof StreamFrame.ReasoningDelta || frame instanceof StreamFrame.ReasoningComplete);
        assertThat(hasReasoning).isTrue();

        String answer = result.getFrames().stream()
            .filter(StreamFrame.TextDelta.class::isInstance)
            .map(StreamFrame.TextDelta.class::cast)
            .map(StreamFrame.TextDelta::getText)
            .reduce("", (a, b) -> a + b);
        assertThat(answer).isNotBlank();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
    public void integration_ToolChoiceRequiredShouldEmitToolCall(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        MultiLLMPromptExecutor executor = createExecutor(model);

        LLMParams params = JavaUtils.createParams(
            model.getProvider(),
            LLMParams.ToolChoice.Required.INSTANCE,
            null,
            null,
            null,
            256
        );

        Prompt prompt = Prompt.builder("java-interop-tool-choice-required")
            .system("You are a calculator assistant. You MUST call tools.")
            .user("What is 123 + 456?")
            .build()
            .withParams(params);

        var tools = List.of(SimpleCalculatorTool.INSTANCE.getDescriptor());
        Message.Assistant response = executor.execute(prompt, model, tools);
        assertThat(response).isNotNull();
        assertThat(response.getParts().stream().anyMatch(MessagePart.Tool.Call.class::isInstance)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
    public void integration_ToolWithoutArgsShouldProduceValidResponse(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        MultiLLMPromptExecutor executor = createExecutor(model);

        LLMParams params = JavaUtils.createParams(
            model.getProvider(),
            null,
            null,
            null,
            null,
            256
        );

        Prompt prompt = Prompt.builder("java-interop-tool-no-args")
            .system("You are a helpful assistant with a color picker tool. ALWAYS CALL TOOL.")
            .user("Pick a random color for me.")
            .build()
            .withParams(params);

        Message.Assistant response = executor.execute(prompt, model, List.of(PickColorTool.INSTANCE.getDescriptor()));
        assertThat(response).isNotNull();
        assertThat(response.getParts().stream().anyMatch(p ->
            p instanceof MessagePart.Tool.Call || p instanceof MessagePart.Text
        )).isTrue();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
    public void integration_ToolChoiceNoneShouldNotEmitToolCalls(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        MultiLLMPromptExecutor executor = createExecutor(model);

        LLMParams params = JavaUtils.createParams(
            model.getProvider(),
            LLMParams.ToolChoice.None.INSTANCE,
            null,
            null,
            null,
            128
        );

        Prompt prompt = Prompt.builder("java-interop-tool-choice-none")
            .system("You are a calculator assistant.")
            .user("Answer directly: what is 4 + 4?")
            .build()
            .withParams(params);

        Message.Assistant response = executor.execute(prompt, model, List.of(SimpleCalculatorTool.INSTANCE.getDescriptor()));
        assertThat(response).isNotNull();
        assertThat(response.getParts().stream().noneMatch(MessagePart.Tool.Call.class::isInstance)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
    public void integration_ToolChoiceNamedShouldPreferSpecifiedTool(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        MultiLLMPromptExecutor executor = createExecutor(model);

        LLMParams params = JavaUtils.createParams(
            model.getProvider(),
            new LLMParams.ToolChoice.Named(SimpleCalculatorTool.INSTANCE.getDescriptor().getName()),
            null,
            null,
            null,
            128
        );

        Prompt prompt = Prompt.builder("java-interop-tool-choice-named")
            .system("You are a calculator assistant.")
            .user("Use a tool to compute 6 + 7.")
            .build()
            .withParams(params);

        Message.Assistant response = executor.execute(prompt, model, List.of(SimpleCalculatorTool.INSTANCE.getDescriptor(), CalculatorToolNoArgs.INSTANCE.getDescriptor(), CalculatorTool.INSTANCE.getDescriptor()));
        assertThat(response).isNotNull();
        assertThat(response.getParts().stream().anyMatch(p ->
            p instanceof MessagePart.Tool.Call &&
                ((MessagePart.Tool.Call) p).getTool().equals(SimpleCalculatorTool.INSTANCE.getDescriptor().getName())
        )).isTrue();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
    public void integration_MultipleChoicesShouldRespectNumberOfChoices(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        assumeTrue(model.supports(LLMCapability.MultipleChoices.INSTANCE), "Model does not support multiple choices");
        MultiLLMPromptExecutor executor = createExecutor(model);

        LLMParams params = JavaUtils.createParams(
            model.getProvider(),
            null,
            2,
            null,
            null,
            128
        );

        Prompt prompt = Prompt.builder("java-interop-multiple-choices")
            .system("You are a concise assistant.")
            .user("Say hello in two different short variants.")
            .build()
            .withParams(params);

        assertThat(prompt.getParams().getNumberOfChoices()).isEqualTo(2);

        List<Message.Assistant> choices = executor.executeMultipleChoices(prompt, model);
        assertThat(choices).hasSize(2);
        assertThat(choices).allMatch(choice -> !choice.getParts().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
    public void integration_StreamingWithToolsShouldEmitToolFrames(LLModel model) throws InterruptedException {
        Models.assumeAvailable(model.getProvider());
        MultiLLMPromptExecutor executor = createExecutor(model);

        LLMParams params = JavaUtils.createParams(
            model.getProvider(),
            LLMParams.ToolChoice.Required.INSTANCE,
            null,
            null,
            null,
            256
        );

        Prompt prompt = Prompt.builder("java-interop-streaming-tools")
            .system("You are a calculator assistant. You MUST call tools.")
            .user("Calculate 7 times 2 using the tool.")
            .build()
            .withParams(params);

        JavaUtils.StreamCollectionResult result = JavaUtils.collectFrames(
            executor.executeStreaming(prompt, model, List.of(SimpleCalculatorTool.INSTANCE.getDescriptor()))
        );
        if (result.getError() != null) {
            fail("Streaming with tools failed: " + result.getError().getClass().getSimpleName() + " - " + result.getError().getMessage());
        }

        boolean hasToolFrames = result.getFrames().stream().anyMatch(frame ->
            frame instanceof StreamFrame.ToolCallDelta || frame instanceof StreamFrame.ToolCallComplete
        );
        assertThat(hasToolFrames).isTrue();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
    public void integration_MultipleSystemMessagesShouldExecute(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        MultiLLMPromptExecutor executor = createExecutor(model);

        LLMParams params = JavaUtils.createParams(
            model.getProvider(),
            null,
            null,
            null,
            null,
            128
        );
        Prompt prompt = Prompt.builder("java-interop-multiple-system")
            .system("You are concise.")
            .system("Always answer with one short sentence.")
            .user("Say hello to Java.")
            .build()
            .withParams(params);

        Message.Assistant response = executor.execute(prompt, model);
        assertThat(response).isInstanceOf(Message.Assistant.class);
        assertThat(response.getParts()).isNotEmpty();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
    public void integration_StructuredOutputBasicSchemaShouldReturnJson(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        assumeTrue(model.supports(LLMCapability.Schema.JSON.Basic.INSTANCE), "Model does not support Basic JSON schema");
        MultiLLMPromptExecutor executor = createExecutor(model);

        JsonObject schemaJson = JavaUtils.weatherSchemaJson();
        LLMParams.Schema.JSON.Basic schema = new LLMParams.Schema.JSON.Basic("WeatherReportBasic", schemaJson);
        LLMParams params = JavaUtils.createParams(
            model.getProvider(),
            null,
            null,
            null,
            null,
            256,
            schema
        );

        Prompt prompt = Prompt.builder("java-interop-structured-basic")
            .system("You are a weather assistant.")
            .user("Return ONLY valid JSON for London weather with fields city, temperature, description, humidity.")
            .build()
            .withParams(params);

        Message.Assistant response = executor.execute(prompt, model);
        assertThat(response).isNotNull();

        String content = textContent(response);
        assertThat(content).isNotBlank();
        assertThat(content).doesNotContain("```");
        assertThat(content.trim()).startsWith("{").endsWith("}");
        assertThat(content).contains("\"city\"");
        assertThat(content).contains("\"temperature\"");
        assertThat(content).contains("\"description\"");
        assertThat(content).contains("\"humidity\"");
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
    public void integration_StructuredOutputStandardSchemaShouldReturnJson(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        assumeTrue(model.supports(LLMCapability.Schema.JSON.Standard.INSTANCE), "Model does not support Standard JSON schema");
        MultiLLMPromptExecutor executor = createExecutor(model);

        JsonObject schemaJson = JavaUtils.weatherSchemaJson();
        LLMParams.Schema.JSON.Standard schema = new LLMParams.Schema.JSON.Standard("WeatherReportStandard", schemaJson);
        LLMParams params = JavaUtils.createParams(
            model.getProvider(),
            null,
            null,
            null,
            null,
            256,
            schema
        );

        Prompt prompt = Prompt.builder("java-interop-structured-standard")
            .system("You are a weather assistant.")
            .user("Return ONLY valid JSON for London weather with fields city, temperature, description, humidity.")
            .build()
            .withParams(params);

        Message.Assistant response = executor.execute(prompt, model);
        assertThat(response).isNotNull();

        String content = textContent(response);
        assertThat(content).isNotBlank();
        assertThat(content).doesNotContain("```");
        assertThat(content.trim()).startsWith("{").endsWith("}");
        assertThat(content).contains("\"city\"");
        assertThat(content).contains("\"temperature\"");
        assertThat(content).contains("\"description\"");
        assertThat(content).contains("\"humidity\"");
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
    public void integration_DifferentToolSchemaTypesShouldEmitValidToolCalls(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        assumeTrue(model.supports(LLMCapability.Tools.INSTANCE), "Model does not support tools");

        MultiLLMPromptExecutor executor = createExecutor(model);
        LLMParams params = JavaUtils.createParams(
            model.getProvider(),
            null,
            null,
            null,
            null,
            256
        );

        List<ToolSchemaCase> cases = List.of(
            new ToolSchemaCase(
                "required+optional",
                GenericParameterTool.INSTANCE.getDescriptor(),
                "Call generic_parameter_tool with requiredArg='must-have' and optionalArg='provided'.",
                "generic_parameter_tool",
                List.of("requiredArg", "optionalArg")
            ),
            new ToolSchemaCase(
                "null-anyof",
                SimplePriceCalculatorTool.INSTANCE.getDescriptor(),
                "Use price_calculator with tokens=10, price_per_token=0.003 and discount=null. Return tool call only.",
                "price_calculator",
                List.of("discount", "null")
            ),
            new ToolSchemaCase(
                "anyof",
                PriceCalculatorTool.INSTANCE.getDescriptor(),
                "Use price_calculator with tokens=10, price_per_token='0.003' and discount=0.1.",
                "price_calculator",
                List.of("price_per_token", "discount")
            ),
            new ToolSchemaCase(
                "list-enum",
                PickColorFromListTool.INSTANCE.getDescriptor(),
                "Use pick_color and pass colors from [RED, GREEN, ORANGE].",
                "pick_color",
                List.of("colors", "[")
            ),
            new ToolSchemaCase(
                "nested-list",
                LotteryTool.INSTANCE.getDescriptor(),
                "Use lottery_picker for nested numbers [[10,42,43,51,22],[34,12,4,53,99]].",
                "lottery_picker",
                List.of("numbers", "[[")
            )
        );

        for (ToolSchemaCase currentCase : cases) {
            Prompt prompt = Prompt.builder("java-interop-tool-schema-" + currentCase.id())
                .system("You are a tools-only assistant. ALWAYS CALL THE TOOL and provide valid tool arguments.")
                .user(currentCase.userPrompt())
                .build()
                .withParams(params);

            Message.Assistant response = executor.execute(prompt, model, List.of(currentCase.descriptor()));
            assertThat(response).as(currentCase.id()).isNotNull();

            MessagePart.Tool.Call toolCall = response.getParts().stream()
                .filter(MessagePart.Tool.Call.class::isInstance)
                .map(MessagePart.Tool.Call.class::cast)
                .findFirst()
                .orElse(null);

            if (toolCall != null) {
                assertThat(toolCall.getTool()).as(currentCase.id() + " tool name").isEqualTo(currentCase.expectedToolName());
                String argsContent = toolCall.getArgs();
                for (String expectedSnippet : currentCase.expectedSnippets()) {
                    assertThat(argsContent)
                        .as(currentCase.id() + " args should contain " + expectedSnippet)
                        .contains(expectedSnippet);
                }
            } else {
                String assistantContent = textContent(response);
                assertThat(assistantContent)
                    .as(currentCase.id() + " assistant fallback should be present")
                    .isNotBlank();
            }
        }
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#latestModels")
    public void integration_StructuredOutputFixingParserPathShouldRecoverMalformedJson(LLModel model) {
        Models.assumeAvailable(model.getProvider());
        assumeTrue(model.supports(LLMCapability.Schema.JSON.Standard.INSTANCE), "Model does not support Standard JSON schema");

        MultiLLMPromptExecutor executor = createExecutor(model);
        StructuredRequestConfig<WeatherReport> config =
            WeatherReportKt.getManualConfig(executor.getStandardJsonSchemaGenerator(model));
        Message.Assistant malformedResponse = new Message.Assistant(
            "```json\n{city:\"London\",temperature:\"18\",description:\"Cloudy\",humidity:\"70\"}\n```",
            ResponseMetaInfo.Companion.getEmpty(),
            null
        );

        StructuredResponse<WeatherReport> fixed = runBlocking(continuation ->
            PromptExecutorStructuredKt.parseResponseToStructuredResponse(
                executor,
                malformedResponse,
                config,
                model,
                WeatherReportKt.getFixingParser(model),
                continuation
            )
        );

        assertThat(fixed).isNotNull();
        assertThat(fixed.getData()).isNotNull();
        assertThat(fixed.getData().getCity()).isEqualToIgnoringCase("London");
        assertThat(fixed.getData().getDescription()).isNotBlank();
        assertThat(fixed.getData().getHumidity()).isGreaterThanOrEqualTo(0);
    }
}

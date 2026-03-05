package ai.koog.integration.tests.agent;

import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext;
import ai.koog.agents.core.agent.entity.AIAgentStorageKey;
import ai.koog.integration.tests.base.KoogJavaTestBase;
import ai.koog.integration.tests.utils.JavaUtils;
import ai.koog.integration.tests.utils.Models;
import ai.koog.prompt.llm.LLModel;
import ai.koog.prompt.message.Message;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.opentelemetry.sdk.testing.assertj.TracesAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

public class AgentStorageIntegrationTest extends KoogJavaTestBase {

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_shouldStoreAndRetrieveValue_whenUsingStorageKeys(LLModel model) {
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

                String stored = JavaUtils.storageGet(context.getStorage(), storageKey);
                retrievedValue.set(stored);

                Message.Response response = context.requestLLM(input, true);
                return response.getContent();
            })
            .build();

        runBlocking(continuation -> agent.run("Hello", null, continuation));

        assertThat(retrievedValue.get()).isEqualTo(expectedValue);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_shouldReturnNull_whenKeyNotFoundInStorage(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgentStorageKey<String> nonExistentKey = new AIAgentStorageKey<>("non-existent");
        AtomicReference<String> retrievedValue = new AtomicReference<>("not-null");

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                String value = JavaUtils.storageGet(context.getStorage(), nonExistentKey);
                retrievedValue.set(value);

                Message.Response response = context.requestLLM(input, true);
                return response.getContent();
            })
            .build();

        runBlocking(continuation -> agent.run("Hello", null, continuation));

        assertThat(retrievedValue.get()).isNull();
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_shouldOverwriteValue_whenSettingSameKeyTwice(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgentStorageKey<String> storageKey = new AIAgentStorageKey<>("overwrite-key");
        String initialValue = "initial";
        String updatedValue = "updated";
        AtomicReference<String> firstRead = new AtomicReference<>();
        AtomicReference<String> secondRead = new AtomicReference<>();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                JavaUtils.storageSet(context.getStorage(), storageKey, initialValue);
                String first = JavaUtils.storageGet(context.getStorage(), storageKey);
                firstRead.set(first);

                JavaUtils.storageSet(context.getStorage(), storageKey, updatedValue);
                String second = JavaUtils.storageGet(context.getStorage(), storageKey);
                secondRead.set(second);

                Message.Response response = context.requestLLM(input, true);
                return response.getContent();
            })
            .build();

        runBlocking(continuation -> agent.run("Hello", null, continuation));

        assertThat(firstRead.get()).isEqualTo(initialValue);
        assertThat(secondRead.get()).isEqualTo(updatedValue);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_shouldSupportMultipleKeysWithDifferentTypes(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgentStorageKey<String> stringKey = new AIAgentStorageKey<>("string-key");
        AIAgentStorageKey<Integer> intKey = new AIAgentStorageKey<>("int-key");

        String expectedString = "test-string";
        Integer expectedInt = 42;

        AtomicReference<String> retrievedString = new AtomicReference<>();
        AtomicReference<Integer> retrievedInt = new AtomicReference<>();

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                JavaUtils.storageSet(context.getStorage(), stringKey, expectedString);
                JavaUtils.storageSet(context.getStorage(), intKey, expectedInt);

                String str = JavaUtils.storageGet(context.getStorage(), stringKey);
                Integer num = JavaUtils.storageGet(context.getStorage(), intKey);

                retrievedString.set(str);
                retrievedInt.set(num);

                Message.Response response = context.requestLLM(input, true);
                return response.getContent();
            })
            .build();

        runBlocking(continuation -> agent.run("Hello", null, continuation));

        assertThat(retrievedString.get()).isEqualTo(expectedString);
        assertThat(retrievedInt.get()).isEqualTo(expectedInt);
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.agent.AIAgentTestBase#getLatestModels")
    public void integration_shouldIsolateStorageForMultipleAgentRuns(LLModel model) {
        Models.assumeAvailable(model.getProvider());

        AIAgentStorageKey<Integer> runCountKey = new AIAgentStorageKey<>("run-count");
        AtomicInteger firstRunCount = new AtomicInteger(-1);
        AtomicInteger secondRunCount = new AtomicInteger(-1);

        AIAgent<String, String> agent = AIAgent.builder()
            .promptExecutor(createExecutor(model))
            .llmModel(model)
            .systemPrompt("You are a helpful assistant.")
            .functionalStrategy((AIAgentFunctionalContext context, String input) -> {
                Integer existing = JavaUtils.storageGet(context.getStorage(), runCountKey);

                int newCount = (existing == null) ? 1 : existing + 1;
                JavaUtils.storageSet(context.getStorage(), runCountKey, newCount);

                Message.Response response = context.requestLLM(input, true);
                return String.valueOf(newCount);
            })
            .build();

        String firstResult = runBlocking(continuation -> agent.run("First hello", null, continuation));
        String secondResult = runBlocking(continuation -> agent.run("Second hello", null, continuation));

        firstRunCount.set(Integer.parseInt(firstResult));
        secondRunCount.set(Integer.parseInt(secondResult));

        assertThat(firstRunCount.get()).isEqualTo(1);
        assertThat(secondRunCount.get()).isEqualTo(1);
    }
}

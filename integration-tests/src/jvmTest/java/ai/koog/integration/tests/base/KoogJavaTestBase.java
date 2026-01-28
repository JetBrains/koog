package ai.koog.integration.tests.base;

import ai.koog.integration.tests.utils.JavaInteropUtils;
import ai.koog.integration.tests.utils.TestCredentials;
import ai.koog.prompt.executor.clients.LLMClient;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.llm.LLMProvider;
import ai.koog.prompt.llm.LLModel;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Timeout(value = 120, unit = TimeUnit.SECONDS)
public abstract class KoogJavaTestBase {

    protected final List<AutoCloseable> resourcesToClose = new ArrayList<>();

    @AfterEach
    public void cleanup() {
        List<Exception> exceptions = new ArrayList<>();
        for (AutoCloseable resource : resourcesToClose) {
            try {
                resource.close();
            } catch (Exception e) {
                exceptions.add(e);
            }
        }
        resourcesToClose.clear();
        if (!exceptions.isEmpty()) {
            RuntimeException aggregated = new RuntimeException("Failed to close resources");
            exceptions.forEach(aggregated::addSuppressed);
            throw aggregated;
        }
    }

    protected MultiLLMPromptExecutor createExecutor(LLModel model) {
        LLMClient client;
        if (model.getProvider() == LLMProvider.OpenAI.INSTANCE) {
            client = JavaInteropUtils.createOpenAIClient(TestCredentials.INSTANCE.readTestOpenAIKeyFromEnv());
        } else if (model.getProvider() == LLMProvider.Anthropic.INSTANCE) {
            client = JavaInteropUtils.createAnthropicClient(TestCredentials.INSTANCE.readTestAnthropicKeyFromEnv());
        } else {
            throw new IllegalArgumentException("Unsupported provider: " + model.getProvider());
        }
        if (client instanceof AutoCloseable) {
            resourcesToClose.add((AutoCloseable) client);
        }
        return new MultiLLMPromptExecutor(client);
    }

    protected <T> T runBlocking(SuspendFunction<T> suspendFunction) {
        try {
            return BuildersKt.runBlocking(
                EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> suspendFunction.invoke(continuation)
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Test interrupted", e);
        }
    }

    @FunctionalInterface
    public interface SuspendFunction<T> {
        Object invoke(Continuation<? super T> continuation);
    }
}

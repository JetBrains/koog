import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.executor.cached.CachedPromptExecutor;
import ai.koog.prompt.cache.files.FilePromptCache;
import ai.koog.prompt.executor.model.JavaPromptExecutor;
import ai.koog.prompt.executor.model.JavaPromptExecutorKt;
import ai.koog.prompt.message.Message;
import kotlinx.datetime.Clock;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ExampleLlmResponseCaching {
    public static void main(String[] args) throws Exception {
        Prompt prompt = Prompt.builder("test")
            .user("Hello")
            .build();

        OpenAILLMClient client = new OpenAILLMClient(System.getenv("OPENAI_API_KEY"));
        MultiLLMPromptExecutor promptExecutor = new MultiLLMPromptExecutor(client);

        FilePromptCache cache = new FilePromptCache(Path.of("path/to/your/cache/directory"), null);
        CachedPromptExecutor cachedExecutor = new CachedPromptExecutor(cache, promptExecutor, Clock.System.INSTANCE);

        JavaPromptExecutor javaExecutor = JavaPromptExecutorKt.asJava(cachedExecutor);

        long start1 = System.nanoTime();
        CompletableFuture<List<Message.Response>> future1 = javaExecutor.executeAsync(prompt, OpenAIModels.Chat.GPT4o);
        List<Message.Response> firstResponse = future1.get();
        long firstTimeMs = (System.nanoTime() - start1) / 1_000_000L;
        System.out.println("First response: " + firstResponse.getFirst().getContent());
        System.out.println("First execution took: " + firstTimeMs + "ms");

        long start2 = System.nanoTime();
        CompletableFuture<List<Message.Response>> future2 = javaExecutor.executeAsync(prompt, OpenAIModels.Chat.GPT4o);
        List<Message.Response> secondResponse = future2.get();
        long secondTimeMs = (System.nanoTime() - start2) / 1_000_000L;
        System.out.println("Second response: " + secondResponse.getFirst().getContent());
        System.out.println("Second execution took: " + secondTimeMs + "ms");
    }
}

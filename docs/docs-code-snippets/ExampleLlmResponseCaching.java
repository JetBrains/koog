import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
import ai.koog.prompt.executor.cached.CachedPromptExecutor;
import ai.koog.prompt.cache.files.FilePromptCache;
import ai.koog.prompt.executor.model.PromptExecutor;
import ai.koog.prompt.executor.ollama.client.OllamaClient;
import ai.koog.prompt.executor.ollama.client.OllamaModels;
import ai.koog.prompt.message.Message;
import kotlin.time.Clock;


import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ExampleLlmResponseCaching {
    public static void main(String[] args) throws Exception {
        // Create a prompt
        Prompt prompt = Prompt.builder("test")
                .user("Hello")
                .build();

        // Create a prompt executor
        OpenAILLMClient client = new OpenAILLMClient(System.getenv("OPENAI_API_KEY"));
        MultiLLMPromptExecutor promptExecutor = new MultiLLMPromptExecutor(client);

        // Create a cached prompt executor
        FilePromptCache cache = new FilePromptCache(Path.of("path/to/your/cache/directory"), null);
        CachedPromptExecutor cachedExecutor = new CachedPromptExecutor(cache, promptExecutor, Clock.System.INSTANCE);

        // Run cached prompt executor for the first time
        // This will perform an actual LLM request
        long start1 = System.nanoTime();
        List<Message.Response> firstResponse = cachedExecutor.execute(prompt, OllamaModels.Meta.LLAMA_3_2);
        long firstTimeMs = (System.nanoTime() - start1) / 1_000_000L;
        System.out.println("First response: " + firstResponse.getFirst().getContent());
        System.out.println("First execution took: " + firstTimeMs + "ms");

        // Run cached prompt executor for the second time
        // This will return the result immediately from the cache
        long start2 = System.nanoTime();
        List<Message.Response> secondResponse = cachedExecutor.execute(prompt, OllamaModels.Meta.LLAMA_3_2);
        long secondTimeMs = (System.nanoTime() - start2) / 1_000_000L;
        System.out.println("Second response: " + secondResponse.getFirst().getContent());
        System.out.println("Second execution took: " + secondTimeMs + "ms");
    }
}

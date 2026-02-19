import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor;
// For the Controller and Mapping annotations
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;

// For the Response wrapper and Status codes
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/chat")
public class ExampleSpringBoot_ChatController {
    private final MultiLLMPromptExecutor anthropicExecutor;

    public ExampleSpringBoot_ChatController(MultiLLMPromptExecutor anthropicExecutor) {
        this.anthropicExecutor = anthropicExecutor;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (anthropicExecutor != null) {
            try {
                Prompt prompt = Prompt.builder("chat")
                    .system("You are a helpful assistant")
                    .user(request.message)
                    .build();

                // FAILED: PromptExecutor.execute is suspend-only and requires a model argument.
                // From Java, use a blocking helper and specify a model, e.g.:
                // List<Message.Response> result = JavaUtils.executeExecutorBlocking(anthropicExecutor, prompt, AnthropicModels.Haiku_4_5);
                // return ResponseEntity.ok(new ChatResponse(result.get(0).getContent()));
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ChatResponse("Error processing request (suspend-only API)"));
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ChatResponse("Error processing request"));
            }
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ChatResponse("AI service not configured"));
        }
    }
}

class ChatRequest {
    public String message;
}
class ChatResponse {
    public final String response;
    public ChatResponse(String response) { this.response = response; }
}

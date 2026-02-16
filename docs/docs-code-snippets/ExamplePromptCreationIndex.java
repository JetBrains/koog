import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.message.ContentPart;
import ai.koog.prompt.message.AttachmentContent;
import ai.koog.prompt.params.LLMParams;

import java.util.List;

public class ExamplePromptCreationIndex {
    public static void main(String[] args) {
        Prompt prompt = Prompt.builder("unique_prompt_id")
            // List of messages
            .build();

        Prompt messageTypes = Prompt.builder("unique_prompt_id")
            .system("You are a helpful assistant with access to tools.")
            .user("What is 5 + 3 ?")
            .assistant("The result is 8.")
            .build();

        Prompt promptSystemMessage = Prompt.builder("assistant")
            .system("You are a helpful assistant that explains technical concepts.")
            .build();

        Prompt promptUserMessage = Prompt.builder("question")
            .system("You are a helpful assistant.")
            .user("What is Koog?")
            .build();

        Prompt promptAssistantMessage = Prompt.builder("article_review")
            .system("Evaluate the article.")
            .user("The article is clear and easy to understand.")
            .assistant("positive")
            .user("The article is hard to read but it's clear and useful.")
            .assistant("neutral")
            .user("The article is confusing and misleading.")
            .assistant("negative")
            .user("The article is interesting and helpful.")
            .build();

        Prompt promptToolMessage = Prompt.builder("calculator_example")
            .system("You are a helpful assistant with access to tools.")
            .user("What is 5 + 3?")
            .toolCall("calculator_tool_id", "calculator", "{\"operation\": \"add\", \"a\": 5, \"b\": 3}")
            .toolResult("calculator_tool_id", "calculator", "8")
            .assistant("The result of 5 + 3 is 8.")
            .user("What is 4 + 5?")
            .build();

        // Create params first
        LLMParams params = new LLMParams(
            0.7,         // temperature
            null,                   // maxTokens
            1,                      // numberOfChoices
            null,                   // speculation
            null,                   // schema
            LLMParams.ToolChoice.Auto.INSTANCE, // toolChoice
            null,                   // user
            null                    // additionalProperties
        );

        Prompt promptParameters = Prompt.builder("custom_params")
            .system("You are a creative writing assistant.")
            .user("Write a song about winter.")
            .build();

        // Apply params to the built prompt
        promptParameters = prompt.withParams(params);

        Prompt basePrompt = Prompt.builder("base")
            .system("You are a helpful assistant.")
            .user("Hello!")
            .assistant("Hi! How can I help you?")
            .build();

        Prompt extendedPrompt = Prompt.builder(String.valueOf(basePrompt))
            .user("What's the weather like?")
            .build();
    }
}

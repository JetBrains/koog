package ai.koog.prompt.executor.selection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.llm.LLMCapability;
import ai.koog.prompt.llm.LLMCapability.Audio;
import ai.koog.prompt.llm.LLMCapability.ToolChoice;
import ai.koog.prompt.llm.LLMCapability.Vision.Video;
import ai.koog.prompt.llm.LLMProvider;
import ai.koog.prompt.llm.LLModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class ModelSelectionTest {

    @Test
    public void testModelSelection() {
        // Given
        LLModel openAI = model(
            LLMProvider.OpenAI,
            "open-ai-model",
            List.of(Audio.INSTANCE, Video.INSTANCE)
        );
        LLModel googleA = model(
            LLMProvider.Google,
            "google-model-a",
            List.of(Audio.INSTANCE, Video.INSTANCE)
        );
        LLModel googleB = model(
            LLMProvider.Google,
            "google-model-b",
            List.of(ToolChoice.INSTANCE)
        );
        LLModel googleC = model(
            LLMProvider.Google,
            "deprecated-google-model",
            List.of(Audio.INSTANCE, Video.INSTANCE)
        );

        // And
        TestSelectingPromptExecutor executor = new TestSelectingPromptExecutor(openAI, googleA, googleB, googleC);

        // And
        Prompt prompt = Prompt
            .builder("test-prompt")
            .system("You're a helpful assistant")
            .build();

        // And
        ModelSelector modelSelector = ModelSelector.builder()
            .withFilter(model -> !model.getId().startsWith("deprecated-"))
            .withCapabilities(Audio.INSTANCE, Video.INSTANCE)
            .withRanker(this::preferGoogleModels)
            .build();

        // When
        executor.execute(prompt, modelSelector, Collections.emptyList());

        // Then
        List<LLModel> selectedModels = executor.getLastSelection().getRanked();
        assertEquals(2, selectedModels.size());
        assertEquals(googleA, selectedModels.get(0));
        assertEquals(openAI, selectedModels.get(1));
    }

    private Ranking preferGoogleModels(List<LLModel> models) {
        List<LLModel> googleModels = new ArrayList<>();
        List<LLModel> otherModels = new ArrayList<>();
        for (LLModel model : models) {
            if (LLMProvider.Google.equals(model.getProvider())) {
                googleModels.add(model);
            } else {
                otherModels.add(model);
            }
        }
        return new Ranking(
            new RankBucket(googleModels),
            new RankBucket(otherModels)
        );
    }

    private LLModel model(LLMProvider provider, String name, List<LLMCapability> capabilities) {
        return new LLModel(provider, name, capabilities, 0L, 0L);
    }
}

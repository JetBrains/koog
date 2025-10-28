package ai.koog.prompt.executor.clients.openai

import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.reflect.KClass
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenAILLMClientTest {

    fun openAiClientTestCases(): Stream<Arguments> =
        Stream.of(
            Arguments.of(
                LLMParams(),
                OpenAIModels.Chat.GPT4o,
                OpenAIChatParams::class,
            ),
            Arguments.of(
                OpenAIChatParams(),
                OpenAIModels.Chat.GPT4o,
                OpenAIChatParams::class,
            ),
            Arguments.of(
                OpenAIResponsesParams(),
                OpenAIModels.Chat.GPT4o,
                OpenAIResponsesParams::class,
            ),
            Arguments.of(
                OpenAIChatParams(),
                OpenAIModels.Audio.GPT4oMiniAudio,
                OpenAIChatParams::class,
            )
        )

    @ParameterizedTest
    @MethodSource("openAiClientTestCases")
    fun `Should use determine Params by input params and model`(
        inputParams: LLMParams,
        model: LLModel,
        expectedClass: KClass<out OpenAIChatParams>
    ) {
        val client = OpenAILLMClient("dummy-key")
        val result = client.determineParams(
            params = inputParams,
            model = model,
        )

        assertEquals(expectedClass, result::class)
    }
}

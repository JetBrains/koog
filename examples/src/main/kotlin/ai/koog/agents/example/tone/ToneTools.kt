package ai.koog.agents.example.tone

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.example.ApiKeyService
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.PromptExecutorExt.execute
import kotlinx.serialization.Serializable

object ToneTools {
    /**
     * Base class for tone analysis tools.
     */
    abstract class ToneTool(
        name: String,
        description: String,
        private val toneType: String
    ) : SimpleTool<ToneTool.Args>() {
        @Serializable
        data class Args(val text: String) : ToolArgs

        override val argsSerializer = Args.serializer()

        override val descriptor = ToolDescriptor(
            name = name,
            description = description,
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "text",
                    description = "The text to analyze for tone.",
                    type = ToolParameterType.String,
                )
            )
        )

        override suspend fun doExecute(args: Args): String {
            val executor: PromptExecutor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey)

            // Create a prompt to analyze the tone
            val prompt = prompt("analyze_tone") {
                system(
                    "You are a helpful assistant that analyzes the tone of text. " +
                        "Determine if the text has a $toneType tone. " +
                        "Respond with only 'yes' if the text has a $toneType tone, or 'no' if it doesn't."
                )
                user("Please analyze the tone of the following text: ${args.text}")
            }

            // Execute the prompt and get the response
            val response = executor.execute(prompt = prompt, model = OpenAIModels.Chat.GPT4o)

            // Process the response
            val answer = response.content.trim().lowercase()

            // Return a formatted response based on the LLM's answer
            return if (answer == "yes") {
                "The text has a $toneType tone."
            } else {
                "The text does not have a $toneType tone."
            }
        }
    }

    /**
     * Tool that analyzes if text has a positive tone.
     */
    object PositiveToneTool : ToneTool(
        name = "positive_tone_analyzer",
        description = "Analyzes if the given text has a positive tone.",
        toneType = "positive"
    )

    /**
     * Tool that analyzes if text has a negative tone.
     */
    object NegativeToneTool : ToneTool(
        name = "negative_tone_analyzer",
        description = "Analyzes if the given text has a negative tone.",
        toneType = "negative"
    )

    /**
     * Tool that analyzes if text has a neutral tone.
     */
    object NeutralToneTool : ToneTool(
        name = "neutral_tone_analyzer",
        description = "Analyzes if the given text has a neutral tone.",
        toneType = "neutral"
    )

    /**
     * Helper function to add all tone tools to a ToolStage.Builder.
     */
    fun ToolRegistry.Builder.tools() {
        tool(PositiveToneTool)
        tool(NegativeToneTool)
        tool(NeutralToneTool)
    }
}

package ai.koog.agents.features.tracing.mock

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.serialization.Serializable

internal class TestTool(private val executor: PromptExecutor) : SimpleTool<TestTool.Args>(
    argsSerializer = Args.serializer(),
    name = "test-tool",
    description = "Test tool"
) {
    @Serializable
    data class Args(val dummy: String = "")

    override suspend fun execute(args: Args): String {
        val prompt = Prompt.build("test") {
            system("You are a helpful assistant that uses tools.")
            user("Set the color to blue")
        }
        return executor.execute(
            prompt = prompt,
            model = OllamaModels.Meta.LLAMA_3_2,
            tools = emptyList()
        ).first().content
    }
}

internal class RecursiveTool : SimpleTool<RecursiveTool.Args>(
    argsSerializer = Args.serializer(),
    name = "recursive",
    description = "Recursive tool for testing"
) {
    @Serializable
    data class Args(val dummy: String = "")

    override suspend fun execute(args: Args): String {
        return "Dummy tool result: ${DummyTool().execute(DummyTool.Args())}"
    }
}

internal class LLMCallTool : SimpleTool<LLMCallTool.Args>(
    argsSerializer = Args.serializer(),
    name = "recursive",
    description = "Recursive tool for testing"
) {
    @Serializable
    data class Args(val dummy: String = "")

    val executor = MockLLMExecutor()

    override suspend fun execute(args: Args): String {
        val prompt = Prompt.build("test") {
            system("You are a helpful assistant that uses tools.")
            user("Set the color to blue")
        }
        return executor.execute(
            prompt,
            OllamaModels.Meta.LLAMA_3_2,
            emptyList()
        ).first().content
    }
}

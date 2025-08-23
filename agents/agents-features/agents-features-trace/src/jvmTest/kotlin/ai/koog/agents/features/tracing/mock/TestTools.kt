package ai.koog.agents.features.tracing.mock

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.testing.tools.DummyTool
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.OllamaModels
import kotlinx.serialization.Serializable

internal class TestTool(private val executor: PromptExecutor) : SimpleTool<TestTool.Args>() {

    @Serializable
    data class Args(val dummy: String = "")

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "test-tool",
        description = "Test tool",
        requiredParameters = emptyList()
    )

    override suspend fun doExecute(args: Args): String {
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

internal class RecursiveTool : SimpleTool<RecursiveTool.Args>() {

    @Serializable
    data class Args(val dummy: String = "")

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "recursive",
        description = "Recursive tool for testing",
        requiredParameters = emptyList()
    )

    override suspend fun doExecute(args: Args): String {
        return "Dummy tool result: ${DummyTool().doExecute(DummyTool.Args())}"
    }
}

internal class LLMCallTool : SimpleTool<LLMCallTool.Args>() {

    @Serializable
    data class Args(val dummy: String = "")

    val executor = MockLLMExecutor()

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "recursive",
        description = "Recursive tool for testing",
        requiredParameters = emptyList()
    )

    override suspend fun doExecute(args: Args): String {
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

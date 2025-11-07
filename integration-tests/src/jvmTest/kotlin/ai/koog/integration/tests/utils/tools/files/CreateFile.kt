package ai.koog.integration.tests.utils.tools.files

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

class CreateFile(private val fs: MockFileSystem) : Tool<CreateFile.Args, CreateFile.Result>() {
    @Serializable
    data class Args(
        @property:LLMDescription("The path to create the file")
        val path: String,
        @property:LLMDescription("The content to create the file")
        val content: String
    )

    @Serializable
    data class Result(val successful: Boolean, val message: String? = null)

    override val argsSerializer = Args.serializer()
    override val resultSerializer: KSerializer<Result> = Result.serializer()

    override val name: String = "create_file"
    override val description: String =
        "Create a file and writes the given text content to it"

    override suspend fun execute(args: Args): Result {
        return when (val res = fs.create(args.path, args.content)) {
            is OperationResult.Success -> Result(successful = true)
            is OperationResult.Failure -> Result(successful = false, message = res.error)
        }
    }
}

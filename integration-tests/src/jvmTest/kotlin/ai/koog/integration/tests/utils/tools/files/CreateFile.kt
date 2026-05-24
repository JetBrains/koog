package ai.koog.integration.tests.utils.tools.files

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.serialization.typeToken
import kotlinx.serialization.Serializable

class CreateFile(private val fs: MockFileSystem) : Tool<CreateFile.Args, CreateFile.Result>(
    argsType = typeToken<Args>(),
    resultType = typeToken<Result>(),
    name = "create_file",
    description = "Create a file and writes the given text content to it"
) {
    @Serializable
    data class Args(
        @property:LLMDescription("The path to create the file")
        val path: String,
        @property:LLMDescription("The content to create the file")
        val content: String
    )

    @Serializable
    data class Result(val successful: Boolean, val message: String? = null)

    override suspend fun execute(args: Args): Result {
        return when (val res = fs.create(args.path, args.content)) {
            is OperationResult.Success -> Result(successful = true)
            is OperationResult.Failure -> Result(successful = false, message = res.error)
        }
    }
}

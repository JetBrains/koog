package ai.koog.integration.tests.utils.tools.files

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

class ReadFile(private val fs: MockFileSystem) : Tool<ReadFile.Args, ReadFile.Result>(
    argsSerializer = Args.serializer(),
    resultSerializer = Result.serializer(),
    name = "read_file",
    description = "Reads a file"
) {
    @Serializable
    data class Args(
        @property:LLMDescription("The path of the file to read")
        val path: String
    )

    @Serializable
    data class Result(
        val successful: Boolean,
        val message: String? = null,
        val content: String? = null
    )

    override suspend fun execute(args: Args): Result {
        return when (val res = fs.read(args.path)) {
            is OperationResult.Success<String> -> Result(successful = true, content = res.result)
            is OperationResult.Failure -> Result(successful = false, message = res.error)
        }
    }
}

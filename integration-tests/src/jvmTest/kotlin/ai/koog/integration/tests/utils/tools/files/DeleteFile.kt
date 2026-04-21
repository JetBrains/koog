package ai.koog.integration.tests.utils.tools.files

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.Serializable

class DeleteFile(private val fs: MockFileSystem) : Tool<DeleteFile.Args, DeleteFile.Result>(
    argsSerializer = Args.serializer(),
    resultSerializer = Result.serializer(),
    name = "delete_file",
    description = "Deletes a file"
) {
    @Serializable
    data class Args(
        @property:LLMDescription("The path of the file to be deleted")
        val path: String
    )

    @Serializable
    data class Result(val successful: Boolean, val message: String? = null)

    override suspend fun execute(args: Args): Result {
        return when (val res = fs.delete(args.path)) {
            is OperationResult.Success -> Result(successful = true)
            is OperationResult.Failure -> Result(successful = false, message = res.error)
        }
    }
}

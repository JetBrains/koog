package ai.koog.integration.tests.utils.tools.files

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

class DeleteFile(private val fs: MockFileSystem) : Tool<DeleteFile.Args, DeleteFile.Result>() {
    @Serializable
    data class Args(
        @property:LLMDescription("The path of the file to be deleted")
        val path: String
    )

    @Serializable
    data class Result(val successful: Boolean, val message: String? = null)

    override val argsSerializer = Args.serializer()
    override val resultSerializer: KSerializer<Result> = Result.serializer()

    override val name: String = "delete_file"
    override val description: String = "Deletes a file"

    override suspend fun execute(args: Args): Result {
        return when (val res = fs.delete(args.path)) {
            is OperationResult.Success -> Result(successful = true)
            is OperationResult.Failure -> Result(successful = false, message = res.error)
        }
    }
}

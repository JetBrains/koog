package ai.koog.integration.tests.utils.tools.files

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

class ListFiles(private val fs: MockFileSystem) : Tool<ListFiles.Args, ListFiles.Result>() {
    @Serializable
    data class Args(
        @property:LLMDescription("The path of the directory")
        val path: String
    )

    @Serializable
    data class Result(
        val successful: Boolean,
        val message: String? = null,
        val children: List<String>? = null
    )

    override val argsSerializer = Args.serializer()
    override val resultSerializer: KSerializer<Result> = Result.serializer()

    override val name: String = "list_files"
    override val description: String = "List all files inside the given path of the directory"

    override suspend fun execute(args: Args): Result {
        return when (val res = fs.ls(args.path)) {
            is OperationResult.Success<List<String>> -> Result(successful = true, children = res.result)
            is OperationResult.Failure -> Result(successful = false, message = res.error)
        }
    }
}

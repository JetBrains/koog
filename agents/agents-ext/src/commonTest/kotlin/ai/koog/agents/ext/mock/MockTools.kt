package ai.koog.agents.ext.mock

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

object MockTools {

    object FinishTool : Tool<FinishTool.Args, String>() {

        @Serializable
        data class Args(
            @property:LLMDescription("Finish output") val output: String = ""
        )

        override val argsSerializer: KSerializer<Args> = serializer<Args>()

        override val resultSerializer: KSerializer<String> = serializer<String>()

        override val description: String = "test-finish-tool"

        override suspend fun execute(args: Args): String {
            return args.output
        }
    }

    class BlankTool(name: String? = null) : Tool<BlankTool.Args, String>() {

        @Serializable
        data class Args(
            @property:LLMDescription("Blank parameter") val args: String = ""
        )

        override val name: String = name ?: "blank-tool"

        override val argsSerializer: KSerializer<Args> = serializer<Args>()

        override val resultSerializer: KSerializer<String> = serializer<String>()

        override val description: String = "test-finish-tool"

        override suspend fun execute(args: Args): String {
            return args.args
        }
    }

    class TransferArgumentsTool()
}

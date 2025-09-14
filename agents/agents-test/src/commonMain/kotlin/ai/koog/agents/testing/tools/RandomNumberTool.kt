package ai.koog.agents.testing.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * A tool that provides a random number using the passed seed.
 */
public class RandomNumberTool : Tool<RandomNumberTool.Args, ToolResult.Number>() {

    /**
     * The last generated random number.
     */
    public var last: Int? = null

    private val logger = KotlinLogging.logger {}

    /**
     * Represents the arguments for the RandomNumberTool.
     *
     * @property seed The seed for the random number generator.
     */
    @Serializable
    public data class Args(val seed: Int? = null) : ToolArgs

    override val argsSerializer: KSerializer<Args> = Args.serializer()

    override val descriptor: ToolDescriptor = ToolDescriptor(
        name = "RandomNumberTool",
        description = "Generates a random number",
        optionalParameters = listOf(
            ToolParameterDescriptor(
                name = "seed",
                description = "The seed for the random number generator",
                type = ToolParameterType.Integer,
            )
        )
    )

    override suspend fun execute(args: Args): ToolResult.Number {
        val seed = args.seed
        val random = if (seed == null) Random else Random(seed)

        val result = random.nextInt().also { number ->
            logger.info { "Generated random number: $number [seed=$seed]" }
            last = number
        }

        return ToolResult.Number(result)
    }
}

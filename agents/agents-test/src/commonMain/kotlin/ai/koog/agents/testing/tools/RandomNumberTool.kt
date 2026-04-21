package ai.koog.agents.testing.tools

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.LLMDescription
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlin.random.Random

/**
 * A tool that provides a random number using the passed seed.
 */
public class RandomNumberTool : Tool<RandomNumberTool.Args, Int>(
    argsSerializer = Args.serializer(),
    resultSerializer = Int.serializer(),
    name = "random_number",
    description = "Generates a random number"
) {

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
    public data class Args(
        @property:LLMDescription("The seed for the random number generator")
        val seed: Int? = null
    )

    override suspend fun execute(args: Args): Int {
        val seed = args.seed
        val random = if (seed == null) Random else Random(seed)

        val result = random.nextInt().also { number ->
            logger.info { "Generated random number: $number [seed=$seed]" }
            last = number
        }

        return result
    }
}

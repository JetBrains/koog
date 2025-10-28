package ai.koog.agents.example.guesser

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

/**
 * This example demonstrates a number guessing agent that uses tools to interact with the user.
 * The agent tries to guess a number the user is thinking of by asking questions.
 */
suspend fun main() {
    // Create a tool registry with the guessing tools
    val toolRegistry = ToolRegistry {
        tools(
            listOf(
                SayToUser,
                LessThanTool,
                GreaterThanTool,
                ProposeNumberTool
            )
        )
    }

    simpleOpenAIExecutor(ApiKeyService.openAIApiKey).use { executor ->
        // Create a chat agent with a system prompt and the tool registry
        val agent = AIAgent(
            promptExecutor = executor,
            llmModel = OpenAIModels.CostOptimized.GPT4oMini,
            systemPrompt = """
                You are a number guessing agent. Your goal is to guess a number that the user is thinking of.

                Follow these steps:
                1. Start by asking the user to think of a number between 1 and 100.
                2. Use the less_than and greater_than tools to narrow down the range.
                    a. If it's neither greater nor smaller, use the propose_number tool.
                3. Once you're confident about the number, use the propose_number tool to check if your guess is correct.
                4. If your guess is correct, congratulate the user. If not, continue guessing.

                Be efficient with your guessing strategy. A binary search approach works well.
            """.trimIndent(),
            temperature = 0.0,
            toolRegistry = toolRegistry
        )

        println("Number Guessing Game started!")
        println("Think of a number between 1 and 100, and I'll try to guess it.")
        println("Type 'start' to begin the game.")

        val initialMessage = readln()
        agent.run(initialMessage)
    }
}

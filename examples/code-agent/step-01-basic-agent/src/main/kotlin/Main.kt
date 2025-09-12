package ai.koog.agents.examples.codeagent.step01

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking


val agent = AIAgent(
    executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
    strategy = singleRunStrategy(),
    systemPrompt = "You are a helpful assistant. Use the tools to interact with the user, then provide an answer to finish the conversation.",
    llmModel = OpenAIModels.Chat.GPT4_1,
    temperature = 0.0,
    toolRegistry = ToolRegistry {
        tool(AskUser)
        tool(SayToUser)
    },
    maxIterations = 100
)

fun main() = runBlocking {
    val result = agent.run("x + y = z, I can tell you 2 of 3 vars values.")
    println(result)
}

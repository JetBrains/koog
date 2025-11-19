package ai.koog.agents.examples.tripplanning

import ai.koog.agents.examples.tripplanning.api.OpenMeteoClient
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLMProvider
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

suspend fun main() {
    val openAiKey = System.getenv("OPENAI_API_KEY")
    val anthropicKey = System.getenv("ANTHROPIC_API_KEY")
    val googleAiKey = System.getenv("GOOGLE_AI_API_KEY")
    val googleMapsKey = System.getenv("GOOGLE_MAPS_API_KEY")

    val googleMapsMcp = createGoogleMapsMcp(googleMapsKey)

    try {
        // Create agent
        val agent = createPlannerAgent(
            promptExecutor = MultiLLMPromptExecutor(
                LLMProvider.OpenAI to OpenAILLMClient(openAiKey),
                LLMProvider.Anthropic to AnthropicLLMClient(anthropicKey),
                LLMProvider.Google to GoogleLLMClient(googleAiKey)
            ),
            openMeteoClient = OpenMeteoClient(),
            googleMapsMcpRegistry = McpToolRegistryProvider.fromTransport(googleMapsMcp),
            onToolCallEvent = {
                println("Tool called: $it")
            },
            showMessage = {
                println("Agent: $it")
                print("Response > ")
                readln()
            }
        )

        // Get initial request
        println("Hi, I'm a trip planner agent. Tell me where and when do you want to go, and I'll help you prepare the plan.")
        print("Response > ")
        val message = readln()

        val timezone = TimeZone.currentSystemDefault()
        val userInput = UserInput(
            message = message,
            currentDate = Clock.System.now().toLocalDateTime(timezone).date,
            timezone = timezone,
        )

        // Print final result
        val result: TripPlan = agent.run(userInput)
        println(result.toMarkdownString())
    } finally {
        // Don't forget to close MCP transport after use
        googleMapsMcp.close()
    }
}

private suspend fun createGoogleMapsMcp(googleMapsKey: String): StdioClientTransport {
    // Start MCP server
    val process = ProcessBuilder(
        "docker", "run", "-i",
        "-e", "GOOGLE_MAPS_API_KEY=$googleMapsKey",
        "mcp/google-maps"
    ).start()

    // Stupid straightforward way to wait for the MCP server to boot
    delay(1000)

    // Create transport to MCP
    return McpToolRegistryProvider.defaultStdioTransport(process)
}

package ai.koog.agents.example.codeagent.step05

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.AIAgentService
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.createAgentTool
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.file.ListDirectoryTool
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.rag.base.files.JVMFileSystemProvider
import ai.koog.agents.ext.tool.search.RegexSearchTool
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

val findAgent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
    llmModel = OpenAIModels.Chat.GPT4_1Mini,
    toolRegistry = ToolRegistry {
        tool(ListDirectoryTool(JVMFileSystemProvider.ReadOnly))
        tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
        tool(RegexSearchTool(JVMFileSystemProvider.ReadOnly))
    },
    systemPrompt = """
        You are an AI assistant specializing in code search.
        Your task is to analyze the user's query and provide clear and specific result.
        
        Break down the query, identify what exactly needs to be found, and note any ambiguities or alternative interpretations.
        If the query is ambiguous or could be improved, provide at least one result for each possible interpretation.
        
        Prioritize accuracy and relevance in your search results.
        * For each result, provide a clear and concise explanation of why it was selected.
        * The explanation should state the specific criteria that led to its selection.
        * If the match is partial or inferred, clearly state the limitations and potential inaccuracies.
        * Ensure to include only relevant snippets in the results.
        
        Ensure to utilize maximum amount of parallelization during the tool calling.
        """.trimIndent(),
    strategy = singleRunStrategy(),
    maxIterations = 100
) {
    setupObservability(agentName = "findAgent")
}

fun createFindAgentTool(): Tool<*, *> {
    return AIAgentService
        .fromAgent(findAgent as GraphAIAgent<String, String>)
        .createAgentTool<String, String>(
            agentName = "__find_in_codebase_agent__",
            agentDescription = """
                This tool is powered by an intelligent micro agent that analyzes and understands code context to find specific elements in your codebase.
                Unlike simple text search (ctrl+F), it intelligently interprets your query to locate classes, functions, variables, or files that best match your intent.
                It requires a detailed query describing what to search for, why you need this information, and an absolute path defining the search scope.
                
                When to use:
                - Locating specific declarations or implementations with contextual understanding.
                - Finding relevant usages of code elements across the codebase.
                - Discovering files and code patterns related to your specific needs.
                - When you need intelligent search that understands code structure and semantics.
                
                When NOT to use:
                - Broad, ambiguous, or conceptual searches (e.g., 'find code related to payments' without specific identifiers).
                - Code understanding, explanation, or refactoring suggestions.
                - Searching outside the provided `path` directory.
                
                The agent analyzes your query, searches intelligently, and returns findings with file paths, line numbers, and relevant code snippets, along with explanations of why each result matches your needs.
                
                While this agent is much more cost efficient at executing searches than using shell commands, it does lose context in between searches.
                So give the preference at clustering similar searches in one call rather than doing multiple calls to this tool.
            """.trimIndent(),
            inputDescription = """
                The input contains two components: the absolute_path and the query.
                
                ## Query
                The query is a detailed search query for the intelligent agent to analyze. Unlike simple text search, this agent will understand your intent if you explain it clearly enough.
                The more details you provide, the better the agent can understand your needs and deliver relevant results. 
                Focus on identifiable code structures (class/function names, variable names, specific text snippets, file name patterns). 
                
                Examples of effective queries:
                - Find all implementations of the `UserRepository` interface to understand how data persistence is handled across the application
                - Locate files named `*Service.kt` containing `fun processOrder` because I need to modify the order processing logic to add a new discount feature
                - Find usages of the `calculateDiscount` function as I'm investigating a bug where discounts are incorrectly applied
                - Search for the text 'OAuth authentication failed' to understand how the application handles authentication failures
                - Find class `PaymentProcessor` because I need to add support for a new payment method
                
                Avoid vague queries like 'search for payment logic'
                Always structure your query as: what you're looking for + why you need it.
                
                ## absolute_path
                The absolute file system path to the directory where the search should begin (the search scope).
                This is crucial for focusing the search on the relevant part of the codebase (e.g., the project root, a specific module, or source directory).
                The path must be absolute and correctly formatted for the operating system.
                
                Example: `/my_app/src/main/kotlin`
                
                ## Formatting
                Provide the absolute_path and the query in this format: 'Absolute path for search scope: <absolute_path>\\n\\n## Query\\n<query>'."
            """.trimIndent()
        )
}

package ai.koog.agents.examples.codeagent.step04

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.file.ListDirectoryTool
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.rag.base.files.JVMFileSystemProvider

val findAgent = AIAgent(
    promptExecutor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY")),
    strategy = singleRunStrategy(),

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
    llmModel = OpenAIModels.Chat.GPT5Codex,
    toolRegistry = ToolRegistry {
        tool(ListDirectoryTool(JVMFileSystemProvider.ReadOnly))
        tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
    },
    maxIterations = 400
) {
    setupObservability(agentName = "findAgent")
}

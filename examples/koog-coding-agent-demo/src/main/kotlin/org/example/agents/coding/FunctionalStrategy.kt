package org.example.agents.coding

import ai.koog.agents.core.agent.AIAgentFunctionalStrategy
import ai.koog.agents.core.agent.context.AIAgentFunctionalContext
import ai.koog.agents.core.agent.context.AIAgentLLMContext
import ai.koog.agents.core.agent.functionalStrategy
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.memory.model.Concept
import ai.koog.prompt.markdown.markdown
import kotlinx.serialization.json.Json

fun createFunctionalStrategy(
    readFile: Tool<*, *>,
    writeFile: Tool<*, *>,
    listDir: Tool<*, *>,
    search: Tool<*, *>,
    executeCommand: Tool<*, *>
): AIAgentFunctionalStrategy<String, String> {
    val allTools = listOf(readFile, writeFile, listDir, search, executeCommand)
    return functionalStrategy("code-and-test") { userInput ->
        var changes = subtask(
            taskDescription = "You must implement the code in Kotlin and also make the tests for the code for the following task:\n" +
                    userInput,
            tools = allTools,
            outputClass = ChangesSummary::class
        )

        while (true) {
            if (llm.historyTooLong()) {
                compressHistory(CODE_AGENT_COMPRESSION_STRATEGY)
            }

            val problems = subtask(
                taskDescription = "You must verify that all the performed changes are correct and that the code compiles and runs without errors. Also make sure that your code passes all tests and that everything you produce is well-tested." +
                        "Changes: ${Json.encodeToString(changes)}",
                tools = listOf(readFile, listDir, search, executeCommand),
                outputClass = ProblemsList::class
            )

            if (problems.noProblems()) return@functionalStrategy "Done!"

            changes = subtask(
                taskDescription = markdown {
                    h1("GOAL: fix ALL the issues in the code")

                    numbered {
                        item {
                            text("1. Fix all following problems in the code:")
                            bulleted {
                                problems.problems.forEach { problemSummary ->
                                    item(problemSummary)
                                }
                            }
                        }

                        item {
                            text("2. Add tests for all following classes:")
                            bulleted {
                                problems.untestedClasses.forEach { className ->
                                    item(className)
                                }
                            }
                        }

                        item {
                            text("3. Fix the following failing tests in the code:")
                            bulleted {
                                problems.failedTests.forEach { testName ->
                                    item(testName)
                                }
                            }
                        }
                    }
                },
                tools = allTools,
                outputClass = ChangesSummary::class
            )
        }

        "Done!"
    }
}

fun AIAgentLLMContext.historyTooLong(): Boolean = prompt.latestTokenUsage > 100500
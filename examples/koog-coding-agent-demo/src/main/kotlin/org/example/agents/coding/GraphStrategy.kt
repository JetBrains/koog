package org.example.agents.coding

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy
import ai.koog.agents.core.dsl.extension.nodeDoNothing
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.prompt.markdown.markdown
import kotlinx.serialization.json.Json

fun createGraphStrategy(
    readFile: Tool<*, *>,
    writeFile: Tool<*, *>,
    listDir: Tool<*, *>,
    search: Tool<*, *>,
    executeCommand: Tool<*, *>
): AIAgentGraphStrategy<String, String> {
    val allTools = listOf(readFile, writeFile, listDir, search, executeCommand)
    return strategy<String, String>("code-and-test") {
        val code by subgraphWithTask<String, ChangesSummary>(
            tools = allTools
        ) { input ->
            "You must implement the code in Kotlin and also make the tests for the code for the following task:\n" +
                    input
        }

        val test by subgraphWithTask<ChangesSummary, ProblemsList>(
            tools = listOf(readFile, listDir, search, executeCommand)
        ) { changes ->
            "You must verify that all the performed changes are correct and that the code compiles and runs without errors. Also make sure that your code passes all tests and that everything you produce is well-tested." +
                    "Changes: ${Json.encodeToString(changes)}"
        }

        val fix by subgraphWithTask<ProblemsList, ChangesSummary>(
            tools = allTools
        ) { problems ->
            markdown {
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
            }
        }

//        val chunkingCompress by nodeLLMCompressHistory<ProblemsList>(strategy = HistoryCompressionStrategy.Chunked(20))
        val decide by nodeDoNothing<ProblemsList>()
        val compressHistory by nodeLLMCompressHistory<ProblemsList>(strategy = CODE_AGENT_COMPRESSION_STRATEGY)

        edge(nodeStart forwardTo code)
        edge(code forwardTo test)

        // History compression inserted:
        edge(test forwardTo decide onCondition { !llm.historyTooLong() })
        edge(test forwardTo compressHistory onCondition { llm.historyTooLong() })
        edge(compressHistory forwardTo decide)

        edge(decide forwardTo fix onCondition { it.problems.isNotEmpty() })
        edge(fix forwardTo test)
        edge(decide forwardTo nodeFinish onCondition { it.noProblems() } transformed { "Done!" })
    }
}
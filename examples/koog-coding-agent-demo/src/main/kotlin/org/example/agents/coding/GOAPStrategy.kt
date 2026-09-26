package org.example.agents.coding

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.planner.AIAgentPlannerStrategy
import ai.koog.agents.planner.goap.GoapAgentState
import ai.koog.prompt.markdown.markdown
import kotlinx.serialization.json.Json

private data class AgentState(
    val userInput: String,
    val changes: ChangesSummary? = null,
    val problems: ProblemsList? = null
) : GoapAgentState<String, String>(userInput) {
    override fun provideOutput(): String {
        return "Done! Changes: ${changes!!.mainChanges.joinToString(", ")}"
    }
}

fun createPlannerStrategy(
    readFile: Tool<*, *>,
    writeFile: Tool<*, *>,
    listDir: Tool<*, *>,
    search: Tool<*, *>,
    executeCommand: Tool<*, *>
): AIAgentPlannerStrategy<String, String, *> {
    val allTools = listOf(readFile, writeFile, listDir, search, executeCommand)

    return AIAgentPlannerStrategy.goap("code-and-fix", ::AgentState) {
        goal("verified-and-correct") { state ->
            state.changes != null && state.problems?.noProblems() == true
        }

        action(
            name = "code",
            precondition = { state -> state.changes == null },
            belief = { state -> state.copy(changes = ChangesSummary("", emptyList())) },
        ) { ctx, state ->
            state.copy(
                changes = ctx.subtask(
                    taskDescription = "You must implement the code in Kotlin and also make the tests for the code for the following task:\n" +
                            state.userInput,
                    tools = allTools,
                    outputClass = ChangesSummary::class
                )
            )
        }

        action(
            name = "test",
            precondition = { state -> state.changes != null && state.problems == null },
            belief = { state -> state.copy(problems = ProblemsList()) },
        ) { ctx, state ->
            state.copy(
                problems = ctx.subtask(
                    taskDescription = "You must verify that all the performed changes are correct and that the code compiles and runs without errors. Also make sure that your code passes all tests and that everything you produce is well-tested." +
                            "Changes: ${Json.encodeToString(state.changes)}",
                    tools = listOf(readFile, listDir, search, executeCommand),
                    outputClass = ProblemsList::class
                )
            )
        }

        action(
            name = "fix",
            precondition = { state -> state.problems?.noProblems() == false },
            belief = { state -> state.copy(problems = null) },
        ) { ctx, state ->
            val problems = state.problems!!

            state.copy(
                problems = null,
                changes = ctx.subtask(
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
            )
        }
    }
}
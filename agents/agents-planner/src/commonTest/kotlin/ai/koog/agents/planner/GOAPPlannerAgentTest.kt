package ai.koog.agents.planner

import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.planner.goap.goap
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.llm.OllamaModels
import kotlinx.coroutines.test.runTest
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertTrue

class GOAPPlannerAgentTest {
    // Create a GOAP planner with a simple linear action sequence

    data class SimpleState(
        val hasKey: Boolean = false,
        val doorUnlocked: Boolean = false,
        val treasureFound: Boolean = false
    )

    @Test
    fun testGOAPLinearPath() = runTest {
        val planner = goap<SimpleState>(typeOf<SimpleState>()) {
            // Action to get the key
            action(
                name = "Get key",
                precondition = { state -> !state.hasKey },
                belief = { state -> state.copy(hasKey = true) },
                cost = { 1.0 }
            ) { _, state ->
                state.copy(hasKey = true)
            }

            // Action to unlock door (requires key)
            action(
                name = "Unlock door",
                precondition = { state -> state.hasKey && !state.doorUnlocked },
                belief = { state -> state.copy(doorUnlocked = true) },
                cost = { 1.0 }
            ) { _, state ->
                state.copy(doorUnlocked = true)
            }

            // Action to find treasure (requires unlocked door)
            action(
                name = "Find treasure",
                precondition = { state -> state.doorUnlocked && !state.treasureFound },
                belief = { state -> state.copy(treasureFound = true) },
                cost = { 1.0 }
            ) { _, state ->
                state.copy(treasureFound = true)
            }

            // Goal: find the treasure
            goal(
                name = "Find treasure",
                condition = { state -> state.treasureFound }
            )
        }

        val strategy = AIAgentPlannerStrategy("goap-linear-test", planner)
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("OK").asDefaultResponse
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("goap") { system("GOAP agent") },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 50
        )

        val agent = PlannerAIAgent(
            promptExecutor = mockExecutor,
            strategy = strategy,
            agentConfig = agentConfig
        )

        val initialState = SimpleState()
        val finalState = agent.run(initialState)

        assertTrue(finalState.hasKey, "Agent should have obtained the key")
        assertTrue(finalState.doorUnlocked, "Agent should have unlocked the door")
        assertTrue(finalState.treasureFound, "Agent should have found the treasure")
    }

    // Create a GOAP planner with multiple paths to the goal
    // One path is more expensive than the other

    data class PathState(
        val hasItem: Boolean = false,
        val goalReached: Boolean = false
    )

    @Test
    fun testGOAPOptimalPathSelection() = runTest {
        val planner = goap<PathState>(typeOf<PathState>()) {
            // Expensive path: cost 10
            action(
                name = "Expensive route",
                precondition = { state -> !state.hasItem },
                belief = { state -> state.copy(hasItem = true) },
                cost = { 10.0 }
            ) { _, _ ->
                throw IllegalStateException("Expensive route should not be selected")
            }

            // Cheap path: cost 1
            action(
                name = "Cheap route",
                precondition = { state -> !state.hasItem },
                belief = { state -> state.copy(hasItem = true) },
                cost = { 1.0 }
            ) { _, state ->
                state.copy(hasItem = true)
            }

            // Final action
            action(
                name = "Reach goal",
                precondition = { state -> state.hasItem && !state.goalReached },
                belief = { state -> state.copy(goalReached = true) },
                cost = { 1.0 }
            ) { _, state ->
                state.copy(goalReached = true)
            }

            goal(
                name = "Reach goal",
                condition = { state -> state.goalReached }
            )
        }

        val strategy = AIAgentPlannerStrategy("goap-optimal-path-test", planner)
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("OK").asDefaultResponse
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("goap") { system("GOAP agent") },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 50
        )

        val agent = PlannerAIAgent(
            promptExecutor = mockExecutor,
            strategy = strategy,
            agentConfig = agentConfig
        )

        val initialState = PathState()
        val finalState = agent.run(initialState)

        assertTrue(finalState.hasItem, "Agent should have obtained the item")
        assertTrue(finalState.goalReached, "Agent should have reached the goal")
    }

    // Create a more complex scenario with multiple dependencies
    data class ComplexState(
        val hasWood: Boolean = false,
        val hasStone: Boolean = false,
        val hasAxe: Boolean = false,
        val hasPickaxe: Boolean = false,
        val hasShelter: Boolean = false
    )

    @Test
    fun testGOAPComplexDependencies() = runTest {
        val planner = goap<ComplexState>(typeOf<ComplexState>()) {
            // Gather wood (no prerequisites)
            action(
                name = "Gather wood",
                precondition = { state -> !state.hasWood },
                belief = { state -> state.copy(hasWood = true) },
                cost = { 2.0 }
            ) { _, state ->
                state.copy(hasWood = true)
            }

            // Gather stone (no prerequisites)
            action(
                name = "Gather stone",
                precondition = { state -> !state.hasStone },
                belief = { state -> state.copy(hasStone = true) },
                cost = { 2.0 }
            ) { _, state ->
                state.copy(hasStone = true)
            }

            // Craft axe (requires wood and stone)
            action(
                name = "Craft axe",
                precondition = { state -> state.hasWood && state.hasStone && !state.hasAxe },
                belief = { state -> state.copy(hasAxe = true) },
                cost = { 1.0 }
            ) { _, state ->
                state.copy(hasAxe = true)
            }

            // Craft pickaxe (requires wood and stone)
            action(
                name = "Craft pickaxe",
                precondition = { state -> state.hasWood && state.hasStone && !state.hasPickaxe },
                belief = { state -> state.copy(hasPickaxe = true) },
                cost = { 1.0 }
            ) { _, state ->
                state.copy(hasPickaxe = true)
            }

            // Build shelter (requires axe and pickaxe)
            action(
                name = "Build shelter",
                precondition = { state ->
                    state.hasAxe && state.hasPickaxe && !state.hasShelter
                },
                belief = { state -> state.copy(hasShelter = true) },
                cost = { 3.0 }
            ) { _, state ->
                state.copy(hasShelter = true)
            }

            goal(
                name = "Build shelter",
                condition = { state -> state.hasShelter }
            )
        }

        val strategy = AIAgentPlannerStrategy("goap-complex-test", planner)
        val mockExecutor = getMockExecutor {
            mockLLMAnswer("OK").asDefaultResponse
        }

        val agentConfig = AIAgentConfig(
            prompt = prompt("goap") { system("GOAP agent") },
            model = OllamaModels.Meta.LLAMA_3_2,
            maxAgentIterations = 50
        )

        val agent = PlannerAIAgent(
            promptExecutor = mockExecutor,
            strategy = strategy,
            agentConfig = agentConfig
        )

        val initialState = ComplexState()
        val finalState = agent.run(initialState)

        assertTrue(finalState.hasWood, "Agent should have gathered wood")
        assertTrue(finalState.hasStone, "Agent should have gathered stone")
        assertTrue(finalState.hasAxe, "Agent should have crafted an axe")
        assertTrue(finalState.hasPickaxe, "Agent should have crafted a pickaxe")
        assertTrue(finalState.hasShelter, "Agent should have built the shelter")
    }
}

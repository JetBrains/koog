package ai.koog.agents.snapshot

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentPlannerContext
import ai.koog.agents.core.planner.AIAgentPlanner
import ai.koog.agents.core.planner.AIAgentPlannerStrategy
import ai.koog.agents.core.planner.PlannerAgentExecutionPoint
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.planner.goap
import ai.koog.agents.planner.goap.GoapAgentState
import ai.koog.agents.planner.llm.PlanStep
import ai.koog.agents.planner.llm.SimpleLLMPlanner
import ai.koog.agents.planner.llm.SimplePlan
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.agents.snapshot.feature.PlannerCheckpointProperties
import ai.koog.agents.snapshot.providers.InMemoryPersistenceStorageProvider
import ai.koog.agents.snapshot.providers.PersistenceStorageProvider
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.serialization.JSONPrimitive
import ai.koog.serialization.typeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.collections.emptyList
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

class PlannerPersistenceTests {
    companion object {
        @JvmStatic
        fun storages(): Stream<PersistenceStorageProvider<*>> {
            return Stream.of(
                InMemoryPersistenceStorageProvider(),
                SerializingInMemoryStorageProvider()
            )
        }
    }

    enum class PlannerExecutionPoint {
        BUILD_PLAN,
        EXECUTE_STEP,
        IS_PLAN_COMPLETED
    }

    class TestPlanner(
        var failAt: PlannerExecutionPoint? = null
    ) : AIAgentPlanner<Int, Int, Int, Int>(
        stateType = typeToken<Int>(),
        planType = typeToken<Int>()
    ) {
        var buildPlanCalls = 0
        var executeStepCalls = 0
        var isPlanCompletedCalls = 0

        override fun initializeState(input: Int): Int = input
        override fun provideOutput(state: Int): Int = state

        override suspend fun buildPlan(context: AIAgentPlannerContext, state: Int, plan: Int?): Int {
            buildPlanCalls++
            if (plan == 1 && failAt == PlannerExecutionPoint.BUILD_PLAN) throw IllegalStateException("Simulated failure at buildPlan")
            return (plan ?: 0) + 1
        }

        override suspend fun executeStep(context: AIAgentPlannerContext, state: Int, plan: Int): Int {
            executeStepCalls++
            if (plan == 2 && failAt == PlannerExecutionPoint.EXECUTE_STEP) throw IllegalStateException("Simulated failure at executeStep")
            return state + plan
        }

        override suspend fun isPlanCompleted(context: AIAgentPlannerContext, state: Int, plan: Int): Boolean {
            isPlanCompletedCalls++
            if (plan == 2 && failAt == PlannerExecutionPoint.IS_PLAN_COMPLETED) throw IllegalStateException("Simulated failure at isPlanCompleted")
            return plan >= 2 // plan is counting 1, 2, ..., so the planner will make two iterations
        }
    }

    val testStorage = InMemoryPersistenceStorageProvider()

    fun createTestPlannerAgent(): Pair<AIAgent<Int, Int>, TestPlanner> {
        val planner = TestPlanner()

        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = AIAgentPlannerStrategy("test", planner),
            agentConfig = AIAgentConfig(
                prompt = Prompt.Empty,
                model = OllamaModels.Meta.LLAMA_3_2,
                maxAgentIterations = 10
            )
        ) {
            install(Persistence) {
                this.storage = testStorage
            }
        }

        return Pair(agent, planner)
    }

    @ParameterizedTest
    @EnumSource(PlannerExecutionPoint::class)
    fun testPlannerResumesAfterFailureAtBuildPlan(failAt: PlannerExecutionPoint) = runTest {
        val (agent, planner) = createTestPlannerAgent()

        val runId = "test-run-fail-at-${failAt.name}"
        planner.failAt = failAt

        val message = assertThrows<IllegalStateException> {
            agent.run(0, runId)
        }.message

        assertNotNull(message)
        assertContains(message, "Simulated failure")

        planner.failAt = null

        val result = agent.run(0, runId)
        assertEquals(3, result)

        val expectedBuildPlanCalls = if (failAt == PlannerExecutionPoint.BUILD_PLAN) 3 else 2
        val expectedExecuteStepCalls = if (failAt == PlannerExecutionPoint.EXECUTE_STEP) 3 else 2
        val expectedIsPlanCompletedCalls = if (failAt == PlannerExecutionPoint.IS_PLAN_COMPLETED) 3 else 2

        assertEquals(expectedBuildPlanCalls, planner.buildPlanCalls)
        assertEquals(expectedExecuteStepCalls, planner.executeStepCalls)
        assertEquals(expectedIsPlanCompletedCalls, planner.isPlanCompletedCalls)
    }

    @ParameterizedTest
    @EnumSource(PlannerExecutionPoint::class)
    fun testPlannerResumesFromCheckpoint(resumeAfter: PlannerExecutionPoint) = runTest {
        val (agent, _) = createTestPlannerAgent()

        val runId = "test-run-resume-after-${resumeAfter.name}"

        val state = 42
        val plan = 88

        val executionPoint = when (resumeAfter) {
            PlannerExecutionPoint.BUILD_PLAN -> PlannerAgentExecutionPoint.PlanCreated
            PlannerExecutionPoint.EXECUTE_STEP -> PlannerAgentExecutionPoint.StepExecuted
            PlannerExecutionPoint.IS_PLAN_COMPLETED -> PlannerAgentExecutionPoint.PlanCompletionEvaluated(false)
        }

        val expectedResult = when (resumeAfter) {
            PlannerExecutionPoint.BUILD_PLAN -> state + plan // executeStep will be called, the state will be updated
            PlannerExecutionPoint.EXECUTE_STEP -> state // executeStep will not be called, the state will remain the same
            PlannerExecutionPoint.IS_PLAN_COMPLETED -> state + plan + 1 // buildPlan and executeStep will be called, the plan will be incremented, the state will be updated
        }

        val checkpoint = AgentCheckpointData(
            checkpointId = "id123",
            createdAt = Clock.System.now(),
            messageHistory = emptyList(),
            version = 0,
            plannerProperties = PlannerCheckpointProperties(
                executionPoint = executionPoint,
                state = JSONPrimitive(state),
                plan = JSONPrimitive(plan)
            ),
            graphProperties = null,
            properties = null,
        )

        testStorage.saveCheckpoint(runId, checkpoint)

        val result = agent.run(state, runId)
        assertEquals(expectedResult, result)
    }

    @Serializable
    data class GoapTestState(
        val stepOneDone: Boolean = false,
        val goalReached: Boolean = false
    ) : GoapAgentState<GoapTestState, GoapTestState>() {
        override val agentInput: GoapTestState = this
        override fun provideOutput(): GoapTestState = this
    }

    @ParameterizedTest
    @MethodSource("storages")
    fun testGOAPPlannerResumesAfterActionFailure(storage: PersistenceStorageProvider<*>) = runTest {
        val runId = "test-goap-planner-resume"
        var stepOneCallCount = 0
        var reachGoalCallCount = 0

        val strategy = goap<GoapTestState, GoapTestState, GoapTestState>("goap-strategy", { input -> input }) {
            action(
                name = "Step one",
                precondition = { true },
                belief = { it.copy(stepOneDone = true) }
            ) { _, state ->
                stepOneCallCount++
                state.copy(stepOneDone = true)
            }

            action(
                name = "Reach goal",
                precondition = { it.stepOneDone },
                belief = { it.copy(goalReached = true) }
            ) { _, state ->
                if (++reachGoalCallCount == 1) throw RuntimeException("Reach goal fails on first call")
                state.copy(goalReached = true)
            }

            goal(
                name = "Reach goal",
                condition = { it.goalReached }
            )
        }

        val agent = AIAgent(
            promptExecutor = getMockExecutor { },
            strategy = strategy,
            agentConfig = AIAgentConfig(
                prompt = Prompt.Empty,
                model = OllamaModels.Meta.LLAMA_3_2,
                maxAgentIterations = 10
            )
        ) {
            install(Persistence) { this.storage = storage }
        }

        // First run: Step one succeeds, Reach goal fails
        val exception = assertThrows<RuntimeException> { agent.run(GoapTestState(), runId) }
        assertContains(exception.message!!, "Reach goal fails")
        assertEquals(1, stepOneCallCount)
        assertEquals(1, reachGoalCallCount)

        // Second run: resume from checkpoint → Step one must NOT be re-run (already completed)
        val result = agent.run(GoapTestState(), runId)
        assertTrue(result.stepOneDone)
        assertTrue(result.goalReached)
        assertEquals(1, stepOneCallCount, "Step one should not be re-run on resume (was already completed)")
        assertEquals(2, reachGoalCallCount)
    }

    @ParameterizedTest
    @MethodSource("storages")
    fun testSimpleLLMPlannerResumesAfterLLMFailure(storage: PersistenceStorageProvider<*>) = runTest {
        val runId = "test-simple-llm-planner-resume"
        var llmCallCount = 0

        val initialPlan = SimplePlan(
            goal = "test goal",
            steps = mutableListOf(PlanStep("do the thing", isCompleted = false))
        )
        val planJson = Json.encodeToString(initialPlan)

        val executor = object : PromptExecutor() {
            override suspend fun execute(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): Message.Assistant = when (++llmCallCount) {
                1 -> Message.Assistant(planJson, ResponseMetaInfo.Empty) // buildPlan's requestLLMStructured
                2 -> throw RuntimeException("fail step when executed the first time")
                else -> Message.Assistant("step done", ResponseMetaInfo.Empty) // executeStep on resume
            }

            override fun executeStreaming(
                prompt: Prompt,
                model: LLModel,
                tools: List<ToolDescriptor>
            ): Flow<StreamFrame> = flow { }

            override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult =
                ModerationResult(isHarmful = false, categories = emptyMap())

            override fun close() {}
        }

        fun createAgent() = AIAgent(
            promptExecutor = executor,
            strategy = AIAgentPlannerStrategy("test", SimpleLLMPlanner()),
            agentConfig = AIAgentConfig(
                prompt = Prompt.Empty,
                model = OllamaModels.Meta.LLAMA_3_2,
                maxAgentIterations = 10
            )
        ) {
            install(Persistence) { this.storage = storage }
        }

        val exception = assertThrows<RuntimeException> { createAgent().run("test task", runId) }
        assertContains(exception.message!!, "fail step when executed the first time")
        assertEquals(2, llmCallCount, "LLM should have been called twice in first run (buildPlan + failed executeStep)")

        val result = createAgent().run("test task", runId)
        assertEquals("step done", result)
        assertEquals(3, llmCallCount, "LLM should have been called exactly 3 times total (buildPlan skipped on resume)")
    }
}

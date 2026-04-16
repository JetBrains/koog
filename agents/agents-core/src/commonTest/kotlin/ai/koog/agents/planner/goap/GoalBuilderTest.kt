package ai.koog.agents.planner.goap

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

class GoalBuilderTest {

    @Test
    fun testBuildGoal() {
        val goal = Goal.builder<String>()
            .name("test-goal")
            .description("test-description")
            .condition { it == "end" }
            .cost { 10.0 }
            .value { cost -> cost * 2.0 }
            .build()

        goal.name shouldBe "test-goal"
        goal.description shouldBe "test-description"
        goal.value(10.0) shouldBe 20.0
    }

    @Test
    fun testMissingNameFails() {
        shouldThrow<IllegalArgumentException> {
            Goal.builder<String>()
                .condition { true }
                .cost { 1.0 }
                .build()
        }
    }

    @Test
    fun testMissingConditionFails() {
        shouldThrow<IllegalArgumentException> {
            Goal.builder<String>()
                .name("test")
                .cost { 1.0 }
                .build()
        }
    }
}

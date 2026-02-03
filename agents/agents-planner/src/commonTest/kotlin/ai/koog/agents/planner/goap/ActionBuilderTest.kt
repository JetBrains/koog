package ai.koog.agents.planner.goap

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlin.test.Test

class ActionBuilderTest {

    private companion object {
        private const val NAME = "test-action"
        private const val DESCRIPTION = "test-description"
    }

    @Test
    fun testBuildAction() {
        val action = Action.builder<String>()
            .name(NAME)
            .description(DESCRIPTION)
            .precondition { true }
            .belief { it }
            .executeAsync { _, state -> state }
            .build()

        action.name.shouldBeEqual(NAME)
        action.description
            .shouldNotBeNull()
            .shouldBeEqual(DESCRIPTION)
    }

    @Test
    fun testMissingNameFails() {
        shouldThrow<IllegalArgumentException> {
            Action.builder<String>()
                .description(DESCRIPTION)
                .precondition { true }
                .belief { it }
                .executeAsync { _, state -> state }
                .build()
        }
    }

    @Test
    fun testMissingPreconditionFails() {
        shouldThrow<IllegalArgumentException> {
            Action.builder<String>()
                .name(NAME)
                .description(DESCRIPTION)
                .belief { it }
                .executeAsync { _, state -> state }
                .build()
        }
    }

    @Test
    fun testMissingBeliefFails() {
        shouldThrow<IllegalArgumentException> {
            Action.builder<String>()
                .name(NAME)
                .description(DESCRIPTION)
                .precondition { true }
                .executeAsync { _, state -> state }
                .build()
        }
    }

    @Test
    fun testMissingExecuteFails() {
        shouldThrow<IllegalArgumentException> {
            Action.builder<String>()
                .name(NAME)
                .description(DESCRIPTION)
                .precondition { true }
                .belief { it }
                .build()
        }
    }
}

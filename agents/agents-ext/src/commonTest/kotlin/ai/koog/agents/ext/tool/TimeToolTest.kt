package ai.koog.agents.ext.tool

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test

internal class TimeToolTest {

    private lateinit var subject: TimeTool

    private lateinit var currentTime: Instant

    private val clock: Clock = object : Clock {
        override fun now(): Instant = currentTime
    }

    @BeforeTest
    fun setup() {
        currentTime = Clock.System.now()
        subject = TimeTool(
            clock = clock,
        )
    }

    private fun verifyTimeAtTimezone(
        toolResponse: String,
        timezone: TimeZone,
        truncateToSeconds: Boolean
    ) {
        val expectedTime = if (truncateToSeconds) {
            Instant.fromEpochSeconds(currentTime.epochSeconds)
        } else {
            currentTime
        }.toLocalDateTime(timezone)

        withClue("Unexpected tool response") {
            toolResponse shouldBe "Current time: $expectedTime (Timezone: $timezone)"
        }
    }

    @Test
    fun testCreateTimeToolWithoutParams() = runTest {
        val subject = TimeTool()
        val result = subject.doExecute(TimeTool.Args())
        withClue("Unexpected response for empty arguments") {
            result shouldContain "Current time: "
            result shouldContain " (Timezone: ${TimeZone.currentSystemDefault()})"
        }
    }

    @Test
    fun testTimeToolWithDefaultTimezone() = runTest {
        val defaultTimezone = TimeZone.of("Antarctica/South_Pole") // something exotic
        subject = TimeTool(
            clock = clock,
            timezone = defaultTimezone,
            truncateToSeconds = false,
        )

        val result = subject.doExecute(TimeTool.Args())

        verifyTimeAtTimezone(result, defaultTimezone, false)
    }

    @Test
    fun testTimeToolWithMilliseconds() = runTest {
        val defaultTimezone = TimeZone.of("Europe/Tallinn")
        subject = TimeTool(
            clock = clock,
            timezone = defaultTimezone,
            truncateToSeconds = false,
        )

        val result = subject.doExecute(TimeTool.Args())

        verifyTimeAtTimezone(result, defaultTimezone, false)
    }

    @Test
    fun testTimeToolWithSpecificTimezone() = runTest {
        listOf("UTC", "Europe/Paris", "+01:00").forEach { timezone ->
            val resultText = subject.doExecute(TimeTool.Args(timezone))

            val timeZone = TimeZone.of(timezone)

            verifyTimeAtTimezone(resultText, timeZone, true)
        }
    }

    @Test
    fun testTimeToolWithInvalidTimezone() = runTest {
        val invalidTimezone = "InvalidTimezone"
        val resultText = subject.doExecute(TimeTool.Args(invalidTimezone))

        withClue("Unexpected error response") {
            resultText shouldBe "Invalid timezone: $invalidTimezone. Please provide a valid timezone like 'Europe/Paris' or an offset like '+01:00'."
        }
    }
}


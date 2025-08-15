package ai.koog.agents.ext.tool

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

internal class TimeToolTest {

    private lateinit var subject: TimeTool

    private lateinit var currentTime: Instant

    private val clock: Clock = object : Clock {
        override fun now(): Instant = currentTime
    }

    @BeforeTest
    fun setup() {
        currentTime = Instant.fromEpochMilliseconds(
            Clock.System.now().toEpochMilliseconds()
        )
        subject = TimeTool(
            clock = clock,
        )
    }

    private fun assertTimeAtTimezone(
        toolResponse: String,
        timezone: TimeZone,
        truncateToSeconds: Boolean
    ) {
        val expectedTime = if (truncateToSeconds) {
            Instant.fromEpochSeconds(currentTime.epochSeconds)
        } else {
            currentTime
        }.toLocalDateTime(timezone)
        // then
        assertEquals(
            expected = "Current time: $expectedTime (Timezone: $timezone)",
            actual = toolResponse,
            message = "Unexpected tool response"
        )
    }

    @Test
    fun testCreateTimeToolWithoutParams() = runTest {
        // given
        val subject = TimeTool()
        // when
        val result = subject.doExecute(TimeTool.Args())
        // then
        assertContains(
            charSequence = result,
            other = "Current time: ",
            message = "Response should contain current time"
        )
        assertContains(
            charSequence = result,
            other = " (Timezone: ${TimeZone.currentSystemDefault()})",
            message = "Response should contain system timezone"
        )
    }

    @Test
    fun testTimeToolWithDefaultTimezone() = runTest {
        // given
        val defaultTimezone = TimeZone.of("Antarctica/South_Pole") // something exotic
        subject = TimeTool(
            clock = clock,
            timezone = defaultTimezone,
            truncateToSeconds = false,
        )
        // when
        val result = subject.doExecute(TimeTool.Args())
        // then
        assertTimeAtTimezone(result, defaultTimezone, false)
    }

    @Test
    fun testTimeToolWithMilliseconds() = runTest {
        // given
        val defaultTimezone = TimeZone.of("Europe/Tallinn")
        subject = TimeTool(
            clock = clock,
            timezone = defaultTimezone,
            truncateToSeconds = false,
        )
        // when
        val result = subject.doExecute(TimeTool.Args())
        // then
        assertTimeAtTimezone(result, defaultTimezone, false)
    }

    private suspend fun verifyTimeToolAtTimezone(timeZoneString: String) {
        assertTimeAtTimezone(
            toolResponse = subject.doExecute(TimeTool.Args(timeZoneString)),
            timezone = TimeZone.of(timeZoneString),
            truncateToSeconds = true
        )
    }

    @Test
    fun testTimeToolWithUTCTimezone() = runTest {
        verifyTimeToolAtTimezone("UTC")
    }

    @Test
    fun testTimeToolWithSpecificTimezoneName() = runTest {
        verifyTimeToolAtTimezone("Europe/Paris")
    }

    @Test
    fun testTimeToolWithSpecificTimezoneOffset() = runTest {
        verifyTimeToolAtTimezone("+01:00")
    }

    @Test
    fun testTimeToolWithInvalidTimezone() = runTest {
        // given
        val invalidTimezone = "Mars/BaseAlpha"
        // when
        val resultText = subject.doExecute(TimeTool.Args(invalidTimezone))
        // then
        assertEquals(
            expected = "Invalid timezone: $invalidTimezone. Please provide a valid timezone like 'Europe/Paris' or an offset like '+01:00'.",
            actual = resultText,
            message = "Unexpected error response"
        )
    }
}

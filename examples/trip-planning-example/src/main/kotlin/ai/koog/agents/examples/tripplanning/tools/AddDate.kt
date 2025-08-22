package ai.koog.agents.examples.tripplanning.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.plus

@Tool
@LLMDescription("Add a duration to a date. Use this tool when you need to calculate offsets, such as tomorrow, in two days, yesterday.")
fun addDate(
    @LLMDescription("The date to add to in ISO format, e.g. 2022-01-01")
    date: String,
    @LLMDescription("The number of days to add, defaults to 0")
    days: Int = 0,
    @LLMDescription("The number of months to add, defaults to 0")
    months: Int = 0,
): String = date.parseLocalDate()
    .plus(days, DateTimeUnit.DAY)
    .plus(months, DateTimeUnit.MONTH)
    .format(LocalDate.Formats.ISO)

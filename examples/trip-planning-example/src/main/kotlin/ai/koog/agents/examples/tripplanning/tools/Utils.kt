package ai.koog.agents.examples.tripplanning.tools

import kotlinx.datetime.LocalDate

fun String.parseLocalDate() = LocalDate.parse(this)

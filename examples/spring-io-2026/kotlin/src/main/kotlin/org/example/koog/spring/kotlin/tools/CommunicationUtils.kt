package org.example.koog.spring.kotlin.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

class CommunicationUtils : ToolSet {
    @Tool
    @LLMDescription("Sends email and waits for reply")
    fun sendEmail(
        @LLMDescription("Email of the recipient")
        recipientEmail: String,
        @LLMDescription("Content of the email message")
        text: String,
    ): String {
        return "TODO"
    }
}

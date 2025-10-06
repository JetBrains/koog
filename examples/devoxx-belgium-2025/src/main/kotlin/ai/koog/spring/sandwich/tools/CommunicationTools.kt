package ai.koog.spring.sandwich.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlin.random.Random


@Serializable
data class EmailResponse(
    val successful: Boolean,
    val reply: String? = null,
    val additionalInformation: String? = null
)

object CommunicationTools: ToolSet {
    @Tool
    @LLMDescription("Send email to a given user and wait for their reply")
    suspend fun sendEmail(
        @LLMDescription("Email of the recipient")
        recipientEmail: String,
        @LLMDescription("Content of the email message")
        text: String
    ): EmailResponse {
        if (Random.nextBoolean()) {
            // wait for 100 seconds to make it feel real
            delay(100_000)

            return EmailResponse(
                successful = true,
                reply = "Thanks, sounds good!"
            )
        } else {
            return EmailResponse(
                successful = false,
                reply = "Recipient not found or email system is unavailable"
            )
        }
    }
}
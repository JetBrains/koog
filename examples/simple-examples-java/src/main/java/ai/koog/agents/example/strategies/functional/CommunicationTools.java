package ai.koog.agents.example.strategies.functional;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;

/**
 * Stub communication tools for the functional strategy example.
 * In a real application, these would integrate with email, chat, or notification systems.
 */
public class CommunicationTools implements ToolSet {

    @Tool
    @LLMDescription("Send an email notification to the specified recipient")
    public String sendEmail(
        @LLMDescription("Recipient email address") String recipient,
        @LLMDescription("Email subject line") String subject,
        @LLMDescription("Email body content") String body
    ) {
        return "Email sent to " + recipient;
    }

    @Tool
    @LLMDescription("Send a chat message to the specified user")
    public String sendChatMessage(
        @LLMDescription("User ID of the recipient") String userId,
        @LLMDescription("Message content") String message
    ) {
        return "Message sent to " + userId;
    }

    @Tool
    @LLMDescription("Get the latest messages from a conversation with the specified user")
    public String getMessages(
        @LLMDescription("User ID to get messages from") String userId,
        @LLMDescription("Maximum number of messages to retrieve") int limit
    ) {
        return "No messages found for user " + userId;
    }
}

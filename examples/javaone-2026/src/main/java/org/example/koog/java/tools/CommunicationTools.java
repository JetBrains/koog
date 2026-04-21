package org.example.koog.java.tools;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.core.tools.annotations.Tool;
import ai.koog.agents.core.tools.reflect.ToolSet;

public class CommunicationTools implements ToolSet {
    @Tool
    @LLMDescription("Sends email and waits for reply")
    public String sendEmail(
            @LLMDescription("Email of the recipient")
            String recipientEmail,
            @LLMDescription("Content of the email message")
            String text
    ) {
        return "TODO";
    }
}

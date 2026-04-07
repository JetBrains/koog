package ai.koog.agents.features.chathistory.aws

/**
 * Parses a conversationId into actor and session components.
 *
 * Supported formats:
 * - `"actorId"` → actor = actorId, session = [defaultSession]
 * - `"actorId:sessionId"` → actor = actorId, session = sessionId
 *
 * @param defaultSession The session ID to use when conversationId doesn't include a session component.
 */
public class AgentcoreConversationIdParser(
    private val defaultSession: String = DEFAULT_SESSION
) {
    /**
     * Represents parsed actor and session from a conversationId.
     */
    public data class ActorAndSession(val actor: String, val session: String)

    /**
     * Parse conversationId into an [ActorAndSession] pair.
     *
     * @param conversationId the conversation ID (format: "actorId" or "actorId:sessionId")
     * @return parsed actor and session
     * @throws IllegalArgumentException if conversationId is blank or has empty actor/session parts
     */
    public fun parse(conversationId: String): ActorAndSession {
        require(conversationId.isNotBlank()) {
            "conversationId is required (format: 'actorId' or 'actorId:sessionId')"
        }

        return if (conversationId.contains(":")) {
            val parts = conversationId.split(":", limit = 2)
            val actor = parts[0]
            val session = parts[1]
            require(actor.isNotBlank()) {
                "actorId part of conversationId must not be blank (format: 'actorId:sessionId')"
            }
            require(session.isNotBlank()) {
                "sessionId part of conversationId must not be blank (format: 'actorId:sessionId')"
            }
            ActorAndSession(actor, session)
        } else {
            ActorAndSession(conversationId, defaultSession)
        }
    }

    /**
     * Constants for [AgentcoreConversationIdParser].
     */
    public companion object {
        /**
         * Default session ID used when conversationId doesn't include a session component.
         */
        public const val DEFAULT_SESSION: String = "default-session"
    }
}

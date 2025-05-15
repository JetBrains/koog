package ai.grazie.code.agents.local.memory.model

public actual object DefaultTimeProvider : TimeProvider {
    override actual fun getCurrentTimestamp(): Long = System.currentTimeMillis()
}

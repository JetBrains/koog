package ai.koog.agents.local.memory.model

public actual object DefaultTimeProvider : TimeProvider {
    override actual fun getCurrentTimestamp(): Long = System.currentTimeMillis()
}

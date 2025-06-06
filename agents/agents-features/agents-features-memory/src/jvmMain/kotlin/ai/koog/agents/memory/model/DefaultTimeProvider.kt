package ai.koog.agents.memory.model

public actual object DefaultTimeProvider : TimeProvider {
    actual override fun getCurrentTimestamp(): Long = System.currentTimeMillis()
}

package ai.koog.agents.memory.model

public actual object DefaultTimeProvider : TimeProvider {
    actual override fun getCurrentTimestamp(): Long = js("Date.now()").unsafeCast<Long>()
}

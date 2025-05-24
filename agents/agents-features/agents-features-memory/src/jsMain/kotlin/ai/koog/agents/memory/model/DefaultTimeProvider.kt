package ai.koog.agents.memory.model

public actual object DefaultTimeProvider : TimeProvider {
    override actual fun getCurrentTimestamp(): Long = js("Date.now()").unsafeCast<Long>()
}

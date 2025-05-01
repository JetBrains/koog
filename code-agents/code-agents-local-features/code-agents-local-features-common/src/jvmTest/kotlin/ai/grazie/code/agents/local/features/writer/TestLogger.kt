package ai.grazie.code.agents.local.features.writer

import ai.grazie.utils.mpp.MPPLogger

class TestLogger(
    val name: String,
    override val infoEnabled: Boolean = true,
    override val debugEnabled: Boolean = true
) : MPPLogger {

    val messages = mutableListOf<String>()

    fun reset() {
        messages.clear()
    }

    override val errorEnabled: Boolean = true

    override val warningEnabled: Boolean = true


    override fun debug(message: () -> String) {
        messages.add("[DEBUG] ${message()}")
    }

    override fun error(e: Throwable?, message: () -> String) {
        messages.add("[ERROR] ${message()}")
    }

    override fun info(message: () -> String) {
        messages.add("[INFO] ${message()}")
    }

    override fun warning(e: Throwable?, message: () -> String) {
        messages.add("[WARN] ${message()}")
    }
}

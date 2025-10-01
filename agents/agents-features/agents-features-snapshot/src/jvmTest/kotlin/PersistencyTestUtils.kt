import java.util.concurrent.LinkedBlockingQueue

internal class TestAgentLogsCollector {
    private val logs = LinkedBlockingQueue<String>()

    fun logs(): List<String> {
        return logs.toList()
    }

    fun clear() {
        logs.clear()
    }

    fun log(message: String) {
        logs.add(message)
    }
}

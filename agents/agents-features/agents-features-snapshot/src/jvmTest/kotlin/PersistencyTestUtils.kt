import java.util.concurrent.ConcurrentLinkedQueue

internal class TestAgentLogsCollector {
    val logs = ConcurrentLinkedQueue<String>()
    fun log(message: String) {
        logs.add(message)
    }
}

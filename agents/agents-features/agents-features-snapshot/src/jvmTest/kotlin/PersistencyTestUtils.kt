internal class TestAgentLogsCollector() {
    val logs = mutableListOf<String>()
    fun log(message: String) {
        logs.add(message)
    }
}

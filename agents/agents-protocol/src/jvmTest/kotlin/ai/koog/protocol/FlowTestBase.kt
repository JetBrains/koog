package ai.koog.protocol

abstract class FlowTestBase {

    protected fun readFlow(name: String): String {
        val jsonContent = object {}.javaClass
            .getResourceAsStream(name.trimStart('/').let { "/$it" })
            ?.bufferedReader()
            ?.readText()
            ?: error("Could not read JSON file")

        return jsonContent
    }
}

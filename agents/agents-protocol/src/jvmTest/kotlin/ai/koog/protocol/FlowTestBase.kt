package ai.koog.protocol

import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

abstract class FlowTestBase {

    protected fun readFlow(resourcePath: String): String {
        val normalizedPath = if (resourcePath.startsWith("/")) resourcePath else "/$resourcePath"
        val jsonContent = object {}.javaClass
            .getResourceAsStream(Path.of(normalizedPath).invariantSeparatorsPathString)
            ?.bufferedReader()
            ?.readText()
            ?: error("Could not read JSON file from: $normalizedPath")

        return jsonContent
    }
}

package ai.koog.prompt.executor.clients.google

import ai.koog.prompt.executor.clients.google.models.GoogleGenerationConfig
import ai.koog.prompt.executor.clients.google.models.GoogleThinkingConfig
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test

class ThinkingConfigTest {
    @Test
    fun serializeThinkingBudget() {
        val cfg = GoogleGenerationConfig(
            thinkingConfig = GoogleThinkingConfig(thinkingBudget = 0)
        )
        val json = Json.encodeToString(GoogleGenerationConfig.serializer(), cfg)
        assertTrue("\"thinkingBudget\":0" in json)
    }
}

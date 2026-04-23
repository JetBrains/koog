package ai.koog.prompt.executor.clients.retrollmfit

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.message.Message
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Instrumented test that runs on the Android emulator.
 *
 * Demonstrates RetroLLMFit working on Android with OkHttp engine and
 * a live call to the Jaika model server.
 */
@RunWith(AndroidJUnit4::class)
class RetroLLMFitAndroidTest {

    // On Android we inject OkHttp instead of CIO (platform-recommended HTTP engine).
    private fun androidHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 60_000
        }
    }

    @Test
    fun testRetroLLMFitCallsJaikaFromAndroid() = runBlocking {
        @LLMEndpoint(
            url = "https://35-207-202-131.sslip.io/api/prompt",
            authHeaderName = "X-User-Id",
            authHeaderValue = "116542085266142929154",
        )
        @Serializable
        data class JaikaRequest(@PromptField val prompt: String, val stream: Boolean = false)

        @Serializable
        data class JaikaResponse(
            @ResponseTextField val text: String,
            val session_id: String = "",
            val type: String = "text",
        )

        val client = RetroLLMFit.create<JaikaRequest, JaikaResponse>(
            httpClient = androidHttpClient()
        )

        val prompt = Prompt.build(id = "android-test", clock = Clock.System) {
            user("Hello from Android! Who are you? Answer in one sentence.")
        }

        val responses = client.execute(prompt, RetroLLMFitModel)

        assertTrue(responses.isNotEmpty(), "Expected at least one response")
        val msg = assertIs<Message.Assistant>(responses.first())
        assertNotNull(msg.content)
        assertTrue(msg.content.isNotBlank(), "Expected non-blank reply, got: '${msg.content}'")

        println("Jaika replied on Android: ${msg.content}")
    }
}

package ai.koog.integration.tests.llm

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import me.kpavlov.aimocks.core.AbstractBuildingStep
import me.kpavlov.aimocks.core.AbstractMockLlm
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.argumentSet
import org.junit.jupiter.params.provider.MethodSource
import kotlin.random.Random.Default.nextDouble
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val TIMEOUT: Duration = 1.seconds

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.CONCURRENT)
abstract class AbstractLLMClientErrorsTest<out LLM_CLIENT : LLMClient, out MOCK : AbstractMockLlm>(
    protected val mock: MOCK,
    protected val model: LLModel,
) {

    protected abstract fun createClient(temperature: Double, requestTimeout: Duration? = null): LLM_CLIENT

    protected abstract fun whenMockMatched(question: String, temperature: Double): AbstractBuildingStep<*, *>

    // language=json
    protected open fun errorResponseBody(message: String): String = ""

    @AfterEach
    fun afterEach() {
        mock.verifyNoUnmatchedRequests()
    }

    @ParameterizedTest(name = "{argumentSetName}({0}) -> {1}")
    @MethodSource("errors")
    fun `Should handle error responses`(
        httpStatusCode: Int,
        expectedException: KClass<Exception>
    ) = runTest {
        // given
        val temperature = nextDouble(0.1, 1.0) // ⚠️ Must be unique per execution!

        val question = "Return error: $httpStatusCode"
        val message = "Error : $httpStatusCode"

        whenMockMatched(question = question, temperature = temperature)
            .respondsError(responseType = String::class) {
                httpStatus = HttpStatusCode.fromValue(httpStatusCode)
                body = errorResponseBody(message)
            }

        val client = createClient(temperature = temperature)

        val prompt = prompt(
            id = "error-$httpStatusCode",
            params = LLMParams(temperature = temperature)
        ) {
            user(question)
        }

        // when-then
        val exception = assertThrows<Exception> {
            client.execute(
                prompt = prompt,
                model = model,
            )
        }

        withClue("Exception `$exception` should be instance of $expectedException") {
            exception.javaClass shouldBe expectedException.java
        }
        exception.message shouldContain "$httpStatusCode"
    }

    @Test
    fun `Should handle request timeout`() = runTest {
        // given
        val temperature = nextDouble(0.1, 1.0) // ⚠️ Must be unique per execution!

        val client = createClient(temperature, requestTimeout = TIMEOUT)

        val question = "Simulate timeout"

        whenMockMatched(question = question, temperature = temperature)
            .respondsError(responseType = String::class) {
                httpStatus = HttpStatusCode.OK
                delay = TIMEOUT * 2
                body = errorResponseBody("timeout")
            }

        val prompt = prompt(
            id = "timeout",
            params = LLMParams(temperature = temperature)
        ) {
            user(question)
        }

        // when-then
        val exception = assertThrows<HttpRequestTimeoutException> {
            client.execute(
                prompt = prompt,
                model = model,
            )
        }

        exception.message shouldStartWith "Request timeout has expired"
    }

    protected open fun errors(): List<Arguments> = listOf(
        argumentSet(
            "Bad request",
            400,
            IllegalStateException::class
        ),
        argumentSet(
            "Unauthenticated",
            401,
            IllegalStateException::class
        ),
        argumentSet(
            "Unauthorized",
            403,
            IllegalStateException::class
        ),
        argumentSet(
            "Not found",
            404,
            IllegalStateException::class
        ),
        argumentSet(
            "Request entity too large",
            413,
            IllegalStateException::class
        ),
        argumentSet(
            "Too many requests",
            429,
            IllegalStateException::class
        ),
        argumentSet(
            "Internal server error",
            500,
            IllegalStateException::class
        ),
        argumentSet(
            "Service unavailable",
            503,
            IllegalStateException::class
        )
    )
}

package ai.koog.a2a.server.jsonrpc

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import kotlin.test.BeforeTest
import kotlin.time.Duration.Companion.seconds

/**
 * Stress-testing event-stream related requests to check that events are processed correctly under a high load.
 * Also more samples help with finding some flaky behavior, e.g. race conditions.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD, reason = "Working with the same instance of test server.")
@Disabled("TODO add stress tests in heavy tests only") // TODO add in heavy tests
class StressA2AServerJsonRpcIntegrationTest : BaseA2AServerJsonRpcTest() {
    override val testTimeout = 10.seconds

    @BeforeAll
    override fun setup() {
        super.setup()
    }

    @BeforeTest
    override fun initClient() {
        super.initClient()
    }

    @AfterAll
    override fun tearDown() {
        super.tearDown()
    }

    @RepeatedTest(300, name = "{currentRepetition}/{totalRepetitions}")
    fun `stress test cancel task`() = super.`test cancel task`()

    @RepeatedTest(300, name = "{currentRepetition}/{totalRepetitions}")
    fun `stress test send message`() = super.`test send message`()

    // Long test, lower repetitions
    @RepeatedTest(10, name = "{currentRepetition}/{totalRepetitions}")
    fun `stress test resubscribe task`() = super.`test resubscribe task`()
}

package ai.koog.integration.tests.acp

import ai.koog.agents.testing.tools.RandomNumberTool
import ai.koog.integration.tests.utils.getLLMClientForProvider
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import com.agentclientprotocol.common.SessionParameters
import com.agentclientprotocol.model.AcpMethod
import com.agentclientprotocol.model.AuthMethod
import com.agentclientprotocol.model.AuthMethodId
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.SessionId
import com.agentclientprotocol.model.SessionNotification
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.protocol.sendNotification
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.inspectors.shouldForAny
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AcpProtocolTest {
    companion object {
        val DEFAULT_MODEL = OpenAIModels.Chat.GPT5_2
        val AUTH_METHOD = AuthMethod(AuthMethodId("test-auth"), "Test Auth", "Auth for testing")
        const val NOTIFICATION = "Hello world!"
        const val AUTH_ERROR = "Authentication required"

        @JvmStatic
        fun getCapabilities() = listOf(
            Arguments.of(true, true, true),
            Arguments.of(false, false, false),
        )
    }

    @Test
    fun integration_testNotificationDelivery() = runTest(timeout = 1.minutes) {
        SingleLLMPromptExecutor(getLLMClientForProvider(DEFAULT_MODEL.provider)).use { promptExecutor ->
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(30.seconds) {
                    val randomNumberTool = RandomNumberTool()
                    val setup = setupAcpClient(this, promptExecutor, DEFAULT_MODEL, randomNumberTool)

                    try {
                        setup.agentSupport.protocol.sendNotification(
                            AcpMethod.ClientMethods.SessionUpdate,
                            SessionNotification(
                                setup.session.shouldNotBeNull().sessionId,
                                SessionUpdate.AgentMessageChunk(ContentBlock.Text(NOTIFICATION))
                            )
                        )

                        withTimeout(5.seconds) {
                            while (
                                setup.clientSupport.lastOperations.flatMap { it.notifications }
                                    .none { it is SessionUpdate.AgentMessageChunk }
                            ) {
                                delay(10)
                            }
                        }

                        val received = setup.clientSupport.lastOperations.flatMap { it.notifications }
                        received.shouldForAny {
                            it is SessionUpdate.AgentMessageChunk && (it.content as? ContentBlock.Text)?.text == NOTIFICATION
                        }
                    } finally {
                        setup.cleanup()
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getCapabilities")
    fun integration_testCapabilitiesSetup(
        loadSession: Boolean,
        audio: Boolean,
        image: Boolean
    ) = runTest(timeout = 1.minutes) {
        SingleLLMPromptExecutor(getLLMClientForProvider(DEFAULT_MODEL.provider)).use { promptExecutor ->
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(30.seconds) {
                    val randomNumberTool = RandomNumberTool()
                    val setup = setupAcpClient(
                        this,
                        promptExecutor,
                        DEFAULT_MODEL,
                        randomNumberTool,
                        loadSession = loadSession,
                        audio = audio,
                        image = image
                    )
                    try {
                        val session = setup.session.shouldNotBeNull()

                        with(setup.agentInfo.capabilities) {
                            loadSession shouldBe loadSession
                            promptCapabilities.audio shouldBe audio
                            promptCapabilities.image shouldBe image
                        }

                        if (loadSession) {
                            val loaded = setup.client.loadSession(
                                session.sessionId,
                                SessionParameters(Paths.get("").absolutePathString(), emptyList())
                            )
                            loaded.sessionId shouldBe session.sessionId
                        } else {
                            shouldThrow<Exception> {
                                setup.client.loadSession(
                                    session.sessionId,
                                    SessionParameters(Paths.get("").absolutePathString(), emptyList())
                                )
                            }
                        }

                        if (!audio) {
                            shouldThrow<Exception> {
                                session.prompt(listOf(ContentBlock.Audio("data", "audio/mp3"))).collect {}
                            }
                        }
                        if (!image) {
                            shouldThrow<Exception> {
                                session.prompt(listOf(ContentBlock.Image("data", "image/png", null))).collect {}
                            }
                        }
                    } finally {
                        setup.cleanup()
                    }
                }
            }
        }
    }

    @Test
    fun integration_testAuthenticationWithAuthMethod() = runTest(timeout = 1.minutes) {
        SingleLLMPromptExecutor(getLLMClientForProvider(DEFAULT_MODEL.provider)).use { promptExecutor ->
            withContext(Dispatchers.Default.limitedParallelism(2)) {
                withTimeout(30.seconds) {
                    val setup = setupAcpClient(
                        this,
                        promptExecutor,
                        DEFAULT_MODEL,
                        RandomNumberTool(),
                        authMethods = listOf(AUTH_METHOD),
                        authenticate = true
                    )

                    try {
                        setup.agentInfo.authMethods shouldBe listOf(AUTH_METHOD)

                        val loadedSession = setup.client.loadSession(
                            setup.session.shouldNotBeNull().sessionId,
                            SessionParameters(Paths.get("").absolutePathString(), emptyList())
                        )
                        loadedSession.sessionId shouldBe setup.session.sessionId
                    } finally {
                        setup.cleanup()
                    }
                }
            }
        }
    }

    @Test
    fun integration_testAuthenticationError() = runTest(timeout = 1.minutes) {
        SingleLLMPromptExecutor(getLLMClientForProvider(DEFAULT_MODEL.provider)).use { promptExecutor ->
            withContext(Dispatchers.Default.limitedParallelism(2)) {
                withTimeout(10.seconds) {
                    val setup = setupAcpClient(
                        this,
                        promptExecutor,
                        DEFAULT_MODEL,
                        RandomNumberTool(),
                        authMethods = listOf(AUTH_METHOD),
                        authenticate = false,
                        createSession = false
                    )
                    try {
                        val loadSessionException = shouldThrow<Exception> {
                            setup.client.loadSession(
                                SessionId("test-session"),
                                SessionParameters(Paths.get("").absolutePathString(), emptyList())
                            )
                        }
                        loadSessionException.message shouldContain AUTH_ERROR

                        val newSessionException = shouldThrow<Exception> {
                            setup.client.newSession(
                                SessionParameters(Paths.get("").absolutePathString(), emptyList())
                            )
                        }
                        newSessionException.message shouldContain AUTH_ERROR
                    } finally {
                        setup.cleanup()
                    }
                }
            }
        }
    }
}

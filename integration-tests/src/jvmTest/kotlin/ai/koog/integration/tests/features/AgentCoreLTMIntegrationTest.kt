package ai.koog.integration.tests.features

import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.annotation.ExperimentalAgentsApi
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.features.chathistory.aws.AgentcoreChatHistoryProvider
import ai.koog.agents.features.longtermmemory.aws.AgentcoreNamespaceResolver
import ai.koog.agents.features.longtermmemory.aws.discovery.AgentcoreDiscoveredStrategyType
import ai.koog.agents.features.longtermmemory.aws.discovery.AgentcoreStrategyDiscovery
import ai.koog.agents.features.longtermmemory.aws.dsl.agentcore
import ai.koog.agents.features.longtermmemory.aws.dsl.agentcoreDiscovered
import ai.koog.agents.longtermmemory.feature.LongTermMemory
import ai.koog.agents.longtermmemory.retrieval.augmentation.UserPromptAugmenter
import ai.koog.integration.tests.utils.TestCredentials.readAwsAccessKeyIdFromEnv
import ai.koog.integration.tests.utils.TestCredentials.readAwsSecretAccessKeyFromEnv
import ai.koog.integration.tests.utils.TestCredentials.readAwsSessionTokenFromEnv
import ai.koog.prompt.executor.clients.bedrock.BedrockLLMClient
import ai.koog.prompt.executor.clients.bedrock.BedrockModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.bedrockagentcore.BedrockAgentCoreClient
import aws.sdk.kotlin.services.bedrockagentcorecontrol.BedrockAgentCoreControlClient
import aws.sdk.kotlin.services.bedrockagentcorecontrol.model.GetMemoryRequest
import aws.sdk.kotlin.services.bedrockagentcorecontrol.model.MemoryStrategyType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.util.UUID

/**
 * End-to-end integration test for AgentCore Long-Term Memory via Koog.
 *
 * For each supported strategy discovered on the memory (SEMANTIC / USER_PREFERENCE /
 * SUMMARIZATION) the test:
 *  1. Seeds a strategy-tailored conversation through Koog's STM (ChatMemory +
 *     AgentcoreChatHistoryProvider), which triggers AgentCore's asynchronous extraction.
 *  2. Waits until at least one record appears in the target namespace, then waits a
 *     stabilization window so retrieval is fully indexed.
 *  3. Runs a second Koog agent with [LongTermMemory] installed and asks a
 *     strategy-appropriate question; asserts the answer contains the expected keyword.
 *
 * EPISODIC is covered by a separate slow test in this class, gated behind
 * `KOOG_LTM_EPISODIC_TESTS=true` because extraction latency ranges from 15-20 min.
 *
 * ## Prerequisites
 *
 * An AgentCore memory must exist (referenced by `AWS_AGENTCORE_MEMORY_ID`) with at least
 * one of the following built-in strategies configured: SEMANTIC, USER_PREFERENCE,
 * SUMMARIZATION. Each configured strategy must declare its namespace template using
 * AgentCore's documented placeholders (`{memoryStrategyId}`, `{actorId}`, and for
 * session-scoped strategies `{sessionId}`).
 *
 * The test discovers the configured strategies at runtime via
 * [BedrockAgentCoreControlClient.getMemory] (see `discoverStrategies()` below) and only
 * exercises the ones it finds — no local configuration of strategy IDs or namespaces
 * is required. Strategies that aren't configured on the memory are skipped.
 *
 * ## Required env
 *
 * - `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`
 * - `AWS_AGENTCORE_MEMORY_ID` — id of the AgentCore memory (see Prerequisites above)
 * - `KOOG_HEAVY_TESTS=true`
 *
 * Optional: `AWS_SESSION_TOKEN`, `AWS_REGION` (defaults to `us-east-1`).
 */
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AWS_AGENTCORE_MEMORY_ID", matches = ".+")
@EnabledIfEnvironmentVariable(named = "KOOG_HEAVY_TESTS", matches = "true")
@OptIn(ExperimentalAgentsApi::class)
class AgentCoreLTMIntegrationTest {

    private lateinit var agentCoreClient: BedrockAgentCoreClient
    private lateinit var controlClient: BedrockAgentCoreControlClient
    private lateinit var llmExecutor: MultiLLMPromptExecutor
    private lateinit var strategies: List<DiscoveredStrategy>

    private val region = System.getenv("AWS_REGION") ?: "us-east-1"
    private val memoryId = System.getenv("AWS_AGENTCORE_MEMORY_ID")
        ?: error("Environment variable AWS_AGENTCORE_MEMORY_ID is not set")

    private val credentialsProvider = StaticCredentialsProvider {
        accessKeyId = readAwsAccessKeyIdFromEnv()
        secretAccessKey = readAwsSecretAccessKeyFromEnv()
        readAwsSessionTokenFromEnv()?.let { sessionToken = it }
    }

    private enum class StrategyKind { SEMANTIC, USER_PREFERENCE, SUMMARY }

    private data class DiscoveredStrategy(
        val kind: StrategyKind,
        val strategyId: String,
        /** Raw AWS namespace template. */
        val namespaceTemplate: String,
    )

    /** Strategy-specific seed conversation, retrieval question and expected answer keyword. */
    private data class Scenario(
        val seedTurns: List<String>,
        val question: String,
        val expectedKeyword: String,
    )

    private fun scenarioFor(kind: StrategyKind): Scenario = when (kind) {
        StrategyKind.SEMANTIC -> Scenario(
            seedTurns = listOf(
                "My name is Alice. I live in Berlin near Alexanderplatz."
            ),
            question = "What is my name?",
            expectedKeyword = "Alice",
        )
        StrategyKind.USER_PREFERENCE -> Scenario(
            seedTurns = listOf(
                "I prefer dark mode in every app I use. My favorite programming language is Kotlin. " +
                    "When I fly I always pick economy class and I like window seats."
            ),
            question = "What is my favorite programming language?",
            expectedKeyword = "Kotlin",
        )
        StrategyKind.SUMMARY -> Scenario(
            seedTurns = listOf(
                "My laptop won't connect to the home Wi-Fi. I've tried restarting the router and the laptop.",
                "Other devices still connect fine, just the laptop doesn't.",
                "I tried updating the Wi-Fi driver you suggested and now it works. Thanks, ticket can be closed.",
            ),
            question = "What fixed my Wi-Fi problem?",
            expectedKeyword = "driver",
        )
    }

    @BeforeEach
    fun setup() = runBlocking {
        agentCoreClient = BedrockAgentCoreClient {
            this.region = this@AgentCoreLTMIntegrationTest.region
            this.credentialsProvider = this@AgentCoreLTMIntegrationTest.credentialsProvider
        }
        controlClient = BedrockAgentCoreControlClient {
            this.region = this@AgentCoreLTMIntegrationTest.region
            this.credentialsProvider = this@AgentCoreLTMIntegrationTest.credentialsProvider
        }
        llmExecutor = MultiLLMPromptExecutor(BedrockLLMClient(identityProvider = credentialsProvider))

        strategies = discoverStrategies()
        check(strategies.isNotEmpty()) {
            "Memory '$memoryId' has no supported strategies configured " +
                "(SEMANTIC / USER_PREFERENCE / SUMMARIZATION). LTM integration test cannot run."
        }
    }

    @AfterEach
    fun teardown() = runBlocking {
        agentCoreClient.close()
        controlClient.close()
    }

    @Test
    fun `agent answers from LTM after async extraction`(): Unit = runBlocking {
        println(
            "[LTM test] Seeds STM, then retries the agent call every ${RETRY_INTERVAL_MS / 1000}s " +
                "until the expected keyword appears or the per-strategy budget (${RETRY_BUDGET_MS / 60_000} min) elapses. " +
                "Expect up to ${RETRY_BUDGET_MS / 60_000 * strategies.size} min total."
        )
        for (discovered in strategies) {
            val actorId = "ltm-actor-${discovered.kind.name.lowercase()}-${UUID.randomUUID()}"
            val sessionId = "ltm-session-${discovered.kind.name.lowercase()}-${UUID.randomUUID()}"
            val conversationId = "$actorId:$sessionId"
            val scenario = scenarioFor(discovered.kind)
            val resolver = namespaceResolverFor(discovered.namespaceTemplate)

            println("[${discovered.kind}] seeding actor=$actorId session=$sessionId")
            seedViaChatMemory(sessionId, conversationId, scenario.seedTurns)

            val agent = createLtmAgent(discovered, actorId, sessionId, resolver)
            awaitAgentMatches(
                tag = discovered.kind.name,
                agent = agent,
                question = scenario.question,
                conversationId = conversationId,
                expectedKeyword = scenario.expectedKeyword,
                budgetMs = RETRY_BUDGET_MS,
                intervalMs = RETRY_INTERVAL_MS,
                initialWaitMs = INITIAL_WAIT_MS,
            )
        }
    }

    /**
     * Slow EPISODIC end-to-end test, separated from the main test because EPISODIC
     * extraction runs later and with higher latency than the other built-in strategies
     * (empirically ~15-20 min between seeding and first retrievable episode).
     *
     * Seeds two bounded conversational episodes (a flight booking then a hotel booking)
     * through Koog's [ChatMemory] — each `agent.run(...)` triggers one `CreateEvent` with
     * a complete user→assistant arc ending in an explicit wrap-up line, which is the
     * signal AgentCore's episodic extractor looks for to detect an episode boundary.
     *
     * Retrieval uses the DSL's `episodes(...)` helper (session-scoped only; reflections
     * require multiple sessions and are out of scope for this test).
     *
     * ## Additional prerequisite
     *
     * On top of the class-level prerequisites, the referenced memory must have an
     * EPISODIC strategy configured with a session-scoped namespace template (typically
     * `{memoryStrategyId}/actors/{actorId}/sessions/{sessionId}`). The strategy id and
     * namespace are discovered at runtime; if no EPISODIC strategy is configured on the
     * memory, the test fails with a descriptive error.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "KOOG_LTM_EPISODIC_TESTS", matches = "true")
    fun `agent answers from LTM EPISODIC strategy (slow, ~15-20 min)`(): Unit = runBlocking {
        val episodic = controlClient.getMemory(GetMemoryRequest { memoryId = this@AgentCoreLTMIntegrationTest.memoryId })
            .memory?.strategies.orEmpty()
            .firstOrNull { it.type == MemoryStrategyType.Episodic }
            ?: error("Memory '$memoryId' has no EPISODIC strategy configured")
        val strategyId = episodic.strategyId
        val namespaceTemplate = episodic.namespaceTemplates.firstOrNull() ?: error("EPISODIC strategy has no namespace")
        val resolver = namespaceResolverFor(namespaceTemplate)

        val actorId = "ltm-actor-episodic-${UUID.randomUUID()}"
        val sessionId = "ltm-session-episodic-${UUID.randomUUID()}"
        val conversationId = "$actorId:$sessionId"

        println(
            "[EPISODIC slow test] EPISODIC extraction is asynchronous and slow: expect up to " +
                "${EPISODIC_RETRY_BUDGET_MS / 60_000} min. Seeds two bounded episodes (flight + hotel) via Koog STM, " +
                "then retries the agent every ${EPISODIC_RETRY_INTERVAL_MS / 60_000} min until 'Paris' appears."
        )
        println("[EPISODIC] actor=$actorId session=$sessionId strategyId=$strategyId")

        // Episode 1 — bounded with a clear wrap-up so the extractor detects an episode boundary.
        seedViaChatMemory(
            sessionId,
            conversationId,
            listOf(
                "I need to book a direct morning flight from Berlin to Paris next Tuesday. " +
                    "Please book the 07:30 Lufthansa flight. The flight booking is complete, thanks — that part is done.",
            )
        )
        // Episode 2 — separate bounded interaction.
        seedViaChatMemory(
            sessionId,
            conversationId,
            listOf(
                "Now book Hotel du Petit Louvre in Paris for three nights under 200 EUR per night. " +
                    "That hotel booking completes my Paris trip. Thanks, goodbye.",
            )
        )

        // Retrieval via Koog LTM, using episodes(...) only (session-scoped).
        val agent = AIAgent(
            promptExecutor = llmExecutor,
            llmModel = BedrockModels.AmazonNovaMicro,
            toolRegistry = ToolRegistry.EMPTY,
            systemPrompt = "You are a helpful assistant. Use any provided context about the user to answer.",
        ) {
            install(LongTermMemory) {
                retrieval {
                    agentcore(agentCoreClient, memoryId) {
                        namespaceResolver = resolver
                        augmenter = UserPromptAugmenter()
                        episodes(strategyId = strategyId, actorId = actorId, sessionId = sessionId, topK = 5)
                    }
                }
            }
        }

        awaitAgentMatches(
            tag = "EPISODIC",
            agent = agent,
            question = "What trip did I book?",
            conversationId = conversationId,
            expectedKeyword = "Paris",
            budgetMs = EPISODIC_RETRY_BUDGET_MS,
            intervalMs = EPISODIC_RETRY_INTERVAL_MS,
            initialWaitMs = EPISODIC_INITIAL_WAIT_MS,
            previewChars = 200,
        )
    }

    /**
     * End-to-end test for the auto-discovery feature.
     *
     * Verifies in two phases:
     *
     *  1. **Discovery** — [AgentcoreStrategyDiscovery] returns the configured strategies of the
     *     memory with namespace templates and reflection-episode hierarchy intact. Asserts that
     *     all four supported strategy types (SEMANTIC, USER_PREFERENCE, SUMMARY, EPISODIC) are
     *     present, that namespace templates carry the expected placeholders, and that EPISODIC
     *     reflection namespaces are segment-prefixes of episode namespaces.
     *
     *  2. **DSL wiring** — the [LongTermMemory.RetrievalSettingsBuilder.agentcoreDiscovered] DSL
     *     accepts the discovered list and produces a working `LongTermMemory` retrieval. A single
     *     `agent.run(...)` exercises the retriever for every discovered strategy with an unseeded
     *     actor; assertions are intentionally about *not throwing*. Per-strategy retrieval
     *     correctness is owned by `agent answers from LTM after async extraction`.
     *
     * Placeholder presence is asserted instead of literal templates so the test is robust to
     * AgentCore template renames; literal-template tests would lock in an implementation detail
     * of AWS.
     */
    @Test
    fun `agentcoreDiscovered DSL discovers strategies and wires LongTermMemory retrieval`(): Unit = runBlocking {
        // ── 1. Discovery ──────────────────────────────────────────────────────────
        val discovered = AgentcoreStrategyDiscovery(controlClient).discover(memoryId)

        val byType = discovered.associateBy { it.type }
        // Hard requirement: this test's memory must have all four supported strategies.
        // If any are missing the assertion message names them so the test environment
        // can be fixed rather than papering over with skips.
        val missing = AgentcoreDiscoveredStrategyType.entries.filter { it !in byType }
        assert(missing.isEmpty()) {
            "Memory '$memoryId' is missing required strategies for this test: $missing. " +
                "Configure them on the AgentCore memory before running."
        }

        for ((type, strategy) in byType) {
            assert(strategy.strategyId.isNotBlank()) { "$type strategyId is blank" }
            assert(strategy.namespaces.isNotEmpty()) { "$type has no namespace templates" }
            for (ns in strategy.namespaces) {
                assert("{memoryStrategyId}" in ns) { "$type namespace '$ns' missing {memoryStrategyId}" }
                assert("{actorId}" in ns) { "$type namespace '$ns' missing {actorId}" }
            }
        }

        // Session-scoped strategies must carry {sessionId} so per-session retrieval works.
        // SEMANTIC and USER_PREFERENCE are actor-scoped only on this memory, so they're
        // intentionally excluded.
        for (sessionScoped in listOf(AgentcoreDiscoveredStrategyType.EPISODIC, AgentcoreDiscoveredStrategyType.SUMMARY)) {
            val ns = byType.getValue(sessionScoped).namespaces
            assert(ns.any { "{sessionId}" in it }) {
                "$sessionScoped expected at least one session-scoped namespace, got $ns"
            }
        }

        // EPISODIC reflection namespaces — the only non-trivial mapping in the discovery
        // logic — must be populated. AWS rule: a reflection namespace must be 'less
        // nested' than at least one episode namespace, i.e. a segment-aligned prefix of
        // it (equality allowed).
        val episodic = byType.getValue(AgentcoreDiscoveredStrategyType.EPISODIC)
        assert(episodic.reflectionsNamespaces.isNotEmpty()) {
            "EPISODIC strategy '${episodic.strategyId}' has no reflection namespaces; " +
                "expected discovery to extract them from EpisodicReflectionConfiguration"
        }
        val episodeTemplates = episodic.namespaces
        for (ns in episodic.reflectionsNamespaces) {
            assert("{memoryStrategyId}" in ns) { "EPISODIC reflection namespace '$ns' missing {memoryStrategyId}" }
            val matched = episodeTemplates.any { ep -> isSegmentPrefix(ns, ep) }
            assert(matched) {
                "EPISODIC reflection namespace '$ns' is not a segment-prefix of any " +
                    "episode namespace ($episodeTemplates); reflections must be 'less nested' than episodes."
            }
        }

        // Other strategies must not carry reflection namespaces — those are episodic-only.
        for (type in listOf(
            AgentcoreDiscoveredStrategyType.SEMANTIC,
            AgentcoreDiscoveredStrategyType.USER_PREFERENCE,
            AgentcoreDiscoveredStrategyType.SUMMARY,
        )) {
            assert(byType.getValue(type).reflectionsNamespaces.isEmpty()) {
                "$type unexpectedly carries reflection namespaces: ${byType.getValue(type).reflectionsNamespaces}"
            }
        }

        // ── 2. DSL wiring ─────────────────────────────────────────────────────────
        val actorId = "ltm-actor-discovered-${UUID.randomUUID()}"
        val sessionId = "ltm-session-discovered-${UUID.randomUUID()}"
        val conversationId = "$actorId:$sessionId"

        val agent = AIAgent(
            promptExecutor = llmExecutor,
            llmModel = BedrockModels.AmazonNovaMicro,
            toolRegistry = ToolRegistry.EMPTY,
            systemPrompt = "You are a helpful assistant.",
        ) {
            install(LongTermMemory) {
                retrieval {
                    agentcoreDiscovered(
                        client = agentCoreClient,
                        memoryId = memoryId,
                        discoveredStrategies = discovered,
                        actorId = actorId,
                        sessionId = sessionId,
                    )
                }
            }
        }

        val response = agent.run("hello", conversationId)
        assert(response.isNotBlank()) { "agent.run produced an empty response: '$response'" }
    }

    private companion object {
        const val INITIAL_WAIT_MS = 30_000L
        const val RETRY_INTERVAL_MS = 30_000L
        const val RETRY_BUDGET_MS = 5L * 60_000L
        const val EPISODIC_INITIAL_WAIT_MS = 5L * 60_000L // wait 5 min before first retry
        const val EPISODIC_RETRY_INTERVAL_MS = 2L * 60_000L // retry every 2 min
        const val EPISODIC_RETRY_BUDGET_MS = 20L * 60_000L // 20 min budget — EPISODIC extraction is slow
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    /**
     * Polls [agent] with [question] until the response contains [expectedKeyword] (case
     * insensitive) or [budgetMs] elapses, retrying every [intervalMs] after an initial
     * [initialWaitMs] wait. Each attempt prints a `[tag] attempt #N @ Ts: MATCH/no match
     * yet -> <preview>` line — useful for diagnosing slow async extraction without
     * leaving the test silent for minutes.
     *
     * On timeout, throws `AssertionError` carrying the last response, attempt count,
     * and which keyword was missing — so a flaky run is debuggable from the failure
     * message alone without re-running.
     */
    private fun awaitAgentMatches(
        tag: String,
        agent: AIAgent<String, String>,
        question: String,
        conversationId: String,
        expectedKeyword: String,
        budgetMs: Long,
        intervalMs: Long,
        initialWaitMs: Long,
        previewChars: Int = 140,
    ) {
        val start = System.currentTimeMillis()
        val attempt = java.util.concurrent.atomic.AtomicInteger(0)
        val lastAnswer = java.util.concurrent.atomic.AtomicReference("")
        try {
            org.awaitility.kotlin.await
                .atMost(java.time.Duration.ofMillis(budgetMs))
                .pollDelay(java.time.Duration.ofMillis(initialWaitMs))
                .pollInterval(java.time.Duration.ofMillis(intervalMs))
                .until {
                    val n = attempt.incrementAndGet()
                    val elapsed = (System.currentTimeMillis() - start) / 1000
                    val answer = runBlocking { agent.run(question, conversationId) }
                    lastAnswer.set(answer)
                    val preview = answer.take(previewChars).replace("\n", " ")
                    val match = answer.contains(expectedKeyword, ignoreCase = true)
                    println("[$tag] attempt #$n @ ${elapsed}s: ${if (match) "MATCH" else "no match yet"} -> $preview")
                    match
                }
        } catch (e: org.awaitility.core.ConditionTimeoutException) {
            throw AssertionError(
                "[$tag] agent should eventually answer '$expectedKeyword'. " +
                    "Last answer after ${attempt.get()} attempts: ${lastAnswer.get()}"
            )
        }
    }

    private suspend fun discoverStrategies(): List<DiscoveredStrategy> {
        val response = controlClient.getMemory(GetMemoryRequest { memoryId = this@AgentCoreLTMIntegrationTest.memoryId })
        return response.memory?.strategies.orEmpty().mapNotNull { s ->
            val kind = when (s.type) {
                MemoryStrategyType.Semantic -> StrategyKind.SEMANTIC
                MemoryStrategyType.UserPreference -> StrategyKind.USER_PREFERENCE
                MemoryStrategyType.Summarization -> StrategyKind.SUMMARY
                else -> null
            } ?: return@mapNotNull null
            val namespaceTemplate = s.namespaceTemplates.firstOrNull() ?: return@mapNotNull null
            val strategyId = s.strategyId
            DiscoveredStrategy(kind, strategyId, namespaceTemplate)
        }
    }

    /**
     * AgentCore templates use `{memoryStrategyId}` whereas [AgentcoreNamespaceResolver.template]
     * uses `{strategyId}`. Normalize, then use the same template for both scopes (strategies
     * that don't contain `{sessionId}` will simply ignore it).
     */
    private fun namespaceResolverFor(rawTemplate: String): AgentcoreNamespaceResolver {
        val normalized = rawTemplate.replace("{memoryStrategyId}", "{strategyId}")
        return AgentcoreNamespaceResolver.template(actorScoped = normalized, sessionScoped = normalized)
    }

    /**
     * Segment-aligned prefix check: returns true iff [full] equals [prefix] (after
     * trimming a trailing slash on either side) or [full] starts with `prefix/`. Used to
     * check that a reflection namespace template is 'less nested' than an episode
     * namespace template per the AgentCore reflection rule. String-level only — does
     * not resolve `{...}` placeholders.
     */
    private fun isSegmentPrefix(prefix: String, full: String): Boolean {
        val p = prefix.removeSuffix("/")
        val f = full.removeSuffix("/")
        return f == p || f.startsWith("$p/")
    }

    private suspend fun seedViaChatMemory(sessionId: String, conversationId: String, turns: List<String>) {
        val provider = AgentcoreChatHistoryProvider(
            client = agentCoreClient,
            memoryId = memoryId,
            defaultSession = sessionId,
        )
        val seedAgent = AIAgent(
            promptExecutor = llmExecutor,
            llmModel = BedrockModels.AmazonNovaMicro,
            toolRegistry = ToolRegistry.EMPTY,
            systemPrompt = "You are a helpful assistant. Acknowledge what the user says.",
        ) {
            install(ChatMemory) { chatHistoryProvider = provider }
        }
        for (turn in turns) {
            seedAgent.run(turn, conversationId)
        }
    }

    /**
     * Build a Koog agent that retrieves from AgentCore LTM for the given strategy.
     */
    private fun createLtmAgent(
        discovered: DiscoveredStrategy,
        actorId: String,
        sessionId: String,
        resolver: AgentcoreNamespaceResolver,
    ): AIAgent<String, String> = AIAgent(
        promptExecutor = llmExecutor,
        llmModel = BedrockModels.AmazonNovaMicro,
        toolRegistry = ToolRegistry.EMPTY,
        systemPrompt = "You are a helpful assistant. Use any provided context about the user to answer.",
    ) {
        install(LongTermMemory) {
            retrieval {
                agentcore(agentCoreClient, memoryId) {
                    namespaceResolver = resolver
                    augmenter = UserPromptAugmenter()
                    when (discovered.kind) {
                        StrategyKind.SEMANTIC -> semantic(
                            strategyId = discovered.strategyId,
                            actorId = actorId,
                            topK = 5,
                        )
                        StrategyKind.USER_PREFERENCE -> userPreferences(
                            strategyId = discovered.strategyId,
                            actorId = actorId,
                            limit = 20,
                        )
                        StrategyKind.SUMMARY -> summary(
                            strategyId = discovered.strategyId,
                            actorId = actorId,
                            sessionId = sessionId,
                            topK = 5,
                        )
                    }
                }
            }
        }
    }
}

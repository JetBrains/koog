package ai.koog.agents.features.longtermmemory.aws

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AgentcoreNamespaceResolverTest {

    private val strategyId = "sem-1"
    private val actorId = "alice"
    private val sessionId = "sess-42"

    // ---- fromAwsTemplate: placeholder normalization ----

    @Test
    fun testFromAwsTemplateAcceptsAwsPlaceholderForActorScope() {
        val resolver = AgentcoreNamespaceResolver.fromAwsTemplate(
            "/strategies/{memoryStrategyId}/actors/{actorId}/"
        )

        val resolved = resolver.resolve(AgentcoreNamespaceScope.Actor(strategyId, actorId))

        assertEquals("/strategies/$strategyId/actors/$actorId/", resolved)
    }

    @Test
    fun testFromAwsTemplateAcceptsKoogPlaceholderForActorScope() {
        val resolver = AgentcoreNamespaceResolver.fromAwsTemplate(
            "/strategies/{strategyId}/actors/{actorId}/"
        )

        val resolved = resolver.resolve(AgentcoreNamespaceScope.Actor(strategyId, actorId))

        assertEquals("/strategies/$strategyId/actors/$actorId/", resolved)
    }

    @Test
    fun testFromAwsTemplateResolvesSessionScopedTemplate() {
        val resolver = AgentcoreNamespaceResolver.fromAwsTemplate(
            "/strategies/{memoryStrategyId}/actors/{actorId}/sessions/{sessionId}/"
        )

        val resolved = resolver.resolve(
            AgentcoreNamespaceScope.Session(strategyId, actorId, sessionId)
        )

        assertEquals("/strategies/$strategyId/actors/$actorId/sessions/$sessionId/", resolved)
    }

    // ---- fromAwsTemplate: scope mismatch is rejected ----

    @Test
    fun testActorScopeAgainstSessionTemplateFails() {
        val resolver = AgentcoreNamespaceResolver.fromAwsTemplate(
            "/strategies/{memoryStrategyId}/actors/{actorId}/sessions/{sessionId}/"
        )

        val ex = assertFailsWith<IllegalStateException> {
            resolver.resolve(AgentcoreNamespaceScope.Actor(strategyId, actorId))
        }
        assertTrue(ex.message!!.contains("session-scoped"))
    }

    @Test
    fun testSessionScopeAgainstActorTemplateFails() {
        val resolver = AgentcoreNamespaceResolver.fromAwsTemplate(
            "/strategies/{memoryStrategyId}/actors/{actorId}/"
        )

        val ex = assertFailsWith<IllegalStateException> {
            resolver.resolve(AgentcoreNamespaceScope.Session(strategyId, actorId, sessionId))
        }
        assertTrue(ex.message!!.contains("actor-scoped"))
    }

    // ---- fromAwsTemplate: leftover placeholders are surfaced ----

    @Test
    fun testUnknownPlaceholderLeftoverFailsFast() {
        val resolver = AgentcoreNamespaceResolver.fromAwsTemplate(
            "/tenants/{tenantId}/strategies/{memoryStrategyId}/actors/{actorId}/"
        )

        val ex = assertFailsWith<IllegalStateException> {
            resolver.resolve(AgentcoreNamespaceScope.Actor(strategyId, actorId))
        }
        assertTrue(ex.message!!.contains("{tenantId}"))
    }

    // ---- fromAwsTemplate: input validation ----

    @Test
    fun testBlankTemplateRejected() {
        assertFailsWith<IllegalArgumentException> {
            AgentcoreNamespaceResolver.fromAwsTemplate("   ")
        }
    }

    @Test
    fun testBlankActorIdRejected() {
        val resolver = AgentcoreNamespaceResolver.fromAwsTemplate(
            "/strategies/{memoryStrategyId}/actors/{actorId}/"
        )

        assertFailsWith<IllegalArgumentException> {
            resolver.resolve(AgentcoreNamespaceScope.Actor(strategyId, " "))
        }
    }
}

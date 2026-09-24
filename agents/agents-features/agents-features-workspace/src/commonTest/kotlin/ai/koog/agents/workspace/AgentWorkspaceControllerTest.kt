package ai.koog.agents.workspace

import ai.koog.agents.workspace.model.AgentCancellationMode
import ai.koog.agents.workspace.model.AgentInputOption
import ai.koog.agents.workspace.model.AgentInputRequest
import ai.koog.agents.workspace.model.AgentInputRequestKind
import ai.koog.agents.workspace.model.AgentInputResponse
import ai.koog.agents.workspace.model.AgentWorkspaceRunOutcome
import ai.koog.agents.workspace.model.AgentWorkspaceRunStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AgentWorkspaceControllerTest {
    @Test
    fun testDuplicateRunIdIsFenced() = runTest {
        val controller = AgentWorkspaceController(InMemoryAgentWorkspaceStore()) { "2026-07-23T00:00:00Z" }

        assertIs<AgentWorkspaceRunOutcome.Completed<String>>(controller.run("run-fenced") { "first" })
        assertIs<AgentWorkspaceRunOutcome.Failed>(controller.run("run-fenced") { "duplicate" })
        assertEquals(AgentWorkspaceRunStatus.COMPLETED, controller.snapshot("run-fenced")?.status)
    }

    @Test
    fun testSuspensionPersistsAndEventsReplay() = runTest {
        val store = InMemoryAgentWorkspaceStore()
        val controller = AgentWorkspaceController(store) { "2026-07-23T00:00:00Z" }

        val outcome = controller.run("run-1") {
            controller.suspendForInput(
                runId = "run-1",
                graphNode = "chat/clarify",
                checkpointId = "checkpoint-1",
                request = AgentInputRequest("question-1", AgentInputRequestKind.FREE_TEXT, "Which service?"),
                requestHash = "hash-1",
            )
        }

        assertIs<AgentWorkspaceRunOutcome.Suspended>(outcome)
        assertEquals(AgentWorkspaceRunStatus.WAITING_FOR_INPUT, controller.snapshot("run-1")?.status)
        assertEquals(listOf(1L, 2L), controller.events("run-1").map { it.sequence })
        assertEquals(listOf(2L), controller.events("run-1", 1L).map { it.sequence })
    }

    @Test
    fun testDecisionMustBeRevalidated() = runTest {
        val controller = AgentWorkspaceController(InMemoryAgentWorkspaceStore()) { "2026-07-23T00:00:00Z" }
        controller.run("run-2") {
            controller.suspendForInput(
                "run-2",
                "chat/approve",
                "checkpoint-2",
                AgentInputRequest(
                    id = "question-2",
                    kind = AgentInputRequestKind.APPROVAL,
                    prompt = "Continue?",
                    options = listOf(AgentInputOption("approve", "Approve"), AgentInputOption("reject", "Reject")),
                ),
                "hash-2",
            )
        }

        val receipt = controller.answer(
            "run-2",
            AgentInputResponse("question-2", listOf("approve"), respondedAt = "2026-07-23T00:01:00Z")
        )
        assertTrue(!receipt.revalidated)
        assertFailsWith<IllegalArgumentException> {
            controller.answer(
                "run-2",
                AgentInputResponse("question-2", listOf("reject"), respondedAt = "2026-07-23T00:02:00Z")
            )
        }
        assertTrue(controller.revalidateDecision("run-2").revalidated)
    }

    @Test
    fun testExpiredDecisionCannotBeRevalidatedOrClaimedForResume() = runTest {
        var currentTime = "2026-07-23T00:00:00Z"
        val controller = AgentWorkspaceController(InMemoryAgentWorkspaceStore()) { currentTime }
        controller.run("run-expired") {
            controller.suspendForInput(
                "run-expired",
                "chat/approve",
                "checkpoint-expired",
                AgentInputRequest(
                    id = "question-expired",
                    kind = AgentInputRequestKind.APPROVAL,
                    prompt = "Continue?",
                    options = listOf(AgentInputOption("approve", "Approve"), AgentInputOption("reject", "Reject")),
                ),
                "hash-expired",
                expiresAt = "2026-07-23T00:05:00Z",
            )
        }
        controller.answer(
            "run-expired",
            AgentInputResponse("question-expired", listOf("approve"), respondedAt = "2026-07-23T00:01:00Z")
        )

        currentTime = "2026-07-23T00:05:00Z"

        assertFailsWith<IllegalArgumentException> { controller.revalidateDecision("run-expired") }
        val snapshot = controller.snapshot("run-expired")
        assertTrue(snapshot?.decisionReceipt?.revalidated == false)
        val resumeError = controller.claimResume("run-expired", "checkpoint-expired")
        assertTrue(resumeError is IllegalArgumentException)
        assertEquals(AgentWorkspaceRunStatus.WAITING_FOR_INPUT, controller.snapshot("run-expired")?.status)
        assertEquals("run.resume_failed", controller.events("run-expired").last().type)
    }

    @Test
    fun testChoiceValidationRejectsUnknownOption() = runTest {
        val controller = AgentWorkspaceController(InMemoryAgentWorkspaceStore()) { "2026-07-23T00:00:00Z" }
        controller.run("run-3") {
            controller.suspendForInput(
                "run-3",
                "chat/choose",
                "checkpoint-3",
                AgentInputRequest(
                    "question-3",
                    AgentInputRequestKind.SINGLE_CHOICE,
                    "Choose",
                    listOf(AgentInputOption("known", "Known")),
                ),
                "hash-3",
            )
        }

        assertFailsWith<IllegalArgumentException> {
            controller.answer(
                "run-3",
                AgentInputResponse("question-3", listOf("unknown"), respondedAt = "2026-07-23T00:01:00Z")
            )
        }
    }

    @Test
    fun testAfterNodeCancellationHasExplicitOutcome() = runTest {
        val controller = AgentWorkspaceController(InMemoryAgentWorkspaceStore()) { "2026-07-23T00:00:00Z" }
        val outcome = controller.run("run-4") {
            controller.requestCancellation("run-4", AgentCancellationMode.AFTER_NODE, "redirected")
            controller.beforeAction("run-4")
            controller.afterNode("run-4")
            "unreachable"
        }

        val cancelled = assertIs<AgentWorkspaceRunOutcome.Cancelled>(outcome)
        assertEquals(AgentCancellationMode.AFTER_NODE, cancelled.mode)
        assertEquals("redirected", cancelled.reason)
        assertEquals(AgentWorkspaceRunStatus.CANCELLED, controller.snapshot("run-4")?.status)
    }
}

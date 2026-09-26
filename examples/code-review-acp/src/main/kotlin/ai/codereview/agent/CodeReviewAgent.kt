package ai.codereview.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.entity.createStorageKey
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.builder.subgraph
import ai.koog.agents.core.dsl.extension.nodeExecuteTools
import ai.koog.agents.core.dsl.extension.onToolCalls
import ai.koog.agents.core.tools.ToolBase
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.agent.ConditionResult
import ai.koog.agents.ext.tool.file.ListDirectoryTool
import ai.koog.agents.ext.tool.file.ReadFileTool
import ai.koog.agents.ext.agent.subgraphWithRetrySimple
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.features.acp.AcpAgent
import ai.koog.agents.features.acp.toKoogMessage
import ai.koog.agents.features.acp.withAcpAgent
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.rag.base.files.JVMFileSystemProvider
import ai.koog.utils.time.KoogClock
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.PlanEntry
import com.agentclientprotocol.model.PlanEntryPriority
import com.agentclientprotocol.model.PlanEntryStatus
import com.agentclientprotocol.model.SessionUpdate
import ai.koog.prompt.message.Message
import com.agentclientprotocol.protocol.Protocol
import kotlinx.coroutines.channels.ProducerScope

private const val SYSTEM_PROMPT = """
You are a senior code reviewer. Your job is to give a SHORT review focusing ONLY on critical issues.

Once the changed files have already been grouped into logical review units,
For each group:
1. Identify commit messages first to understand the intent behind the changes.
2. See the diff of every file in the group to inspect the actual diff.
3. Do NOT explore the wider codebase unless absolutely necessary. Only look at the diffs themselves.
4. Report ONLY critical findings: bugs that will cause failures, security vulnerabilities,
   data loss risks, or serious correctness issues. Ignore everything else — no style,
   naming, formatting, documentation, or minor design comments.
5. End each group with an LGTM / NEEDS_CHANGES verdict and a one-line summary.

Keep your review as short as possible. If there are no critical issues, just say LGTM.
Do not pad the review with minor observations. Silence is better than noise.
"""

/**
 * Builds a [AIAgent] configured for code review against the given target, wired into the ACP
 * session identified by [sessionId] so that plan and message events flow back to the [eventsProducer].
 */
fun createCodeReviewAgent(
    promptExecutor: PromptExecutor,
    protocol: Protocol,
    sessionId: String,
    eventsProducer: ProducerScope<Event>,
    gitUtils: GitUtils,
): AIAgent<List<ContentBlock>, List<ReviewResult>> {
    val toolRegistry = ToolRegistry {
        tools(gitUtils.asTools())
        tool(ListDirectoryTool(JVMFileSystemProvider.ReadOnly))
        tool(ReadFileTool(JVMFileSystemProvider.ReadOnly))
    }

    val agentConfig = AIAgentConfig(
        prompt = prompt("code-review") { system(SYSTEM_PROMPT) },
        model = AnthropicModels.Haiku_4_5,
        maxAgentIterations = 1000,
    )

    return AIAgent(
        promptExecutor = promptExecutor,
        agentConfig = agentConfig,
        strategy = codeReviewStrategy(gitUtils, toolRegistry.tools),
        toolRegistry = toolRegistry,
    ) {
        install(AcpAgent) {
            this.sessionId = sessionId
            this.protocol = protocol
            this.eventsProducer = eventsProducer
            this.setDefaultNotifications = true
        }
    }
}

private fun codeReviewStrategy(gitUtils: GitUtils, tools: List<ToolBase<*, *>>) =
    strategy<List<ContentBlock>, List<ReviewResult>>("code-review") {
        val changedFilesKey = createStorageKey<Set<String>>("changedFiles")
        val groupsKey = createStorageKey<List<FileGroup>>("pendingGroups")
        val reviewedGroupsKey = createStorageKey<MutableList<ReviewResult>>("reviewed")
        val planEntriesKey = createStorageKey<MutableList<PlanEntry>>("planEntries")

        val setBaseStepContent = "Determine review base from user message"
        val groupingStepContent = "Discover and group changed files"

        val nodeRecordInput by node<List<ContentBlock>, Unit> { input ->
            llm.writeSession {
                appendPrompt {
                    message(input.toKoogMessage(KoogClock.System))
                }
            }
        }

        val nodeSendInitialPlan by node<Unit, Unit> {
            sendPlan(
                listOf(
                    PlanEntry(
                        content = setBaseStepContent,
                        priority = PlanEntryPriority.HIGH,
                        status = PlanEntryStatus.IN_PROGRESS,
                    )
                )
            )
        }

        val nodeSetBase by subgraph(tools = tools.filter { it.name.startsWith("set") }) {
            val nodeCallLLM by node<Unit, Message.Assistant> {
                // use read session instead of write session so that response is not appended to the prompt
                llm.readSession { requestLLMOnlyCallingTools() }
            }
            val nodeExecuteTools by nodeExecuteTools().transform {  }

            nodeStart then nodeCallLLM
            edge(nodeCallLLM forwardTo nodeExecuteTools onToolCalls { true })
            nodeExecuteTools then nodeFinish
        }

        val nodePostSetBase by node<Unit, Unit> {
            sendPlan(
                listOf(
                    PlanEntry(
                        content = setBaseStepContent,
                        priority = PlanEntryPriority.HIGH,
                        status = PlanEntryStatus.COMPLETED,
                    ),
                    PlanEntry(
                        content = groupingStepContent,
                        priority = PlanEntryPriority.HIGH,
                        status = PlanEntryStatus.IN_PROGRESS,
                    )
                )
            )
        }

        val nodeGatherFiles by node<Unit, Set<String>> {
            gitUtils.getChangedFiles().also { storage.set(changedFilesKey, it) }
        }

        val nodeGroupFilesWithRetry by subgraphWithRetrySimple<Set<String>, FileGrouping>(
            condition = { filesCorrectlyGroupedCondition(it, storage.getValue(changedFilesKey)) },
            maxRetries = 3,
        ) {
            val nodeGroupFiles by subgraphWithTask<Set<String>, FileGrouping>(parallelTools = true) { files ->
                buildString {
                    appendLine(
                        "The following ${files.size} files were changed."
                    )
                    appendLine("Group them into logical units that make sense to review together.")
                    appendLine("Each group needs a short description, a list of files, and a priority.")
                    appendLine("It is fine and expected to have groups with only one file.")
                    appendLine()
                    appendLine("Files:")
                    files.forEach { appendLine("- $it") }
                }
            }
            nodeStart then nodeGroupFiles then nodeFinish
        }

        val nodeInitGroupsState by node<List<FileGroup>, Unit> { groups ->
            val sortedGroups = groups.sortedBy { it.priority.ordinal }

            val entries = listOf(
                PlanEntry(
                    content = setBaseStepContent,
                    priority = PlanEntryPriority.HIGH,
                    status = PlanEntryStatus.COMPLETED,
                ),
                PlanEntry(
                    content = groupingStepContent,
                    priority = PlanEntryPriority.HIGH,
                    status = PlanEntryStatus.COMPLETED,
                )
            ) + sortedGroups.map { group ->
                PlanEntry(
                    content = "Review group '${group.description}'",
                    priority = group.priority,
                    status = PlanEntryStatus.PENDING,
                )
            }

            storage.set(groupsKey, sortedGroups.toMutableList())
            storage.set(reviewedGroupsKey, mutableListOf())
            storage.set(planEntriesKey, entries.toMutableList())
        }

        val nodeMarkPreviousEntryCompleted by node<Unit, Unit> {
            val planEntries = storage.getValue(planEntriesKey)
            val previousEntryIndex = storage.getValue(reviewedGroupsKey).size + 1

            planEntries[previousEntryIndex] = planEntries[previousEntryIndex].copy(status = PlanEntryStatus.COMPLETED)
        }

        val nodeStartNextGroup by node<Unit, FileGroup> {
            val planEntries = storage.getValue(planEntriesKey)
            val nextGroupIndex = storage.getValue(reviewedGroupsKey).size

            planEntries[nextGroupIndex + 2] = planEntries[nextGroupIndex + 2].copy(status = PlanEntryStatus.IN_PROGRESS)

            sendPlan(planEntries)

            storage.getValue(groupsKey)[nextGroupIndex]
        }

        val nodeReviewGroup by subgraphWithTask<FileGroup, ReviewResult> { group ->
            val fileList = group.files.joinToString("\n") { "   - $it" }
            """
            Review group '${group.description}' (priority: ${group.priority}):
            $fileList

            Keep it quick. Read the commit messages and diffs, but do not explore
            surrounding code unless something looks clearly broken. Focus only on
            high-impact issues — serious bugs, security problems, or major design
            concerns. Skip style nits and minor suggestions.

            For each finding, cite file:line in one sentence. End with an
            LGTM / NEEDS_CHANGES verdict and a one-line summary.

            Call finish_task with a single ReviewResult for this group.
            """.trimIndent()
        }

        val nodeRecordReview by node<ReviewResult, Unit> { reviewedGroup ->
            storage.getValue(reviewedGroupsKey).add(reviewedGroup)
        }

        val nodeSendReview by node<Unit, List<ReviewResult>>("sendReview") {
            val results = storage.getValue(reviewedGroupsKey)
            val text = formatReview(results, gitUtils.target)
            sendPlan(storage.getValue(planEntriesKey))
            withAcpAgent {
                sendEvent(
                    Event.SessionUpdateEvent(
                        SessionUpdate.AgentMessageChunk(ContentBlock.Text(text))
                    )
                )
            }
            results
        }

        nodeStart then
            nodeRecordInput then
            nodeSendInitialPlan then
            nodeSetBase then
            nodePostSetBase then
            nodeGatherFiles then
            nodeGroupFilesWithRetry

        edge(nodeGroupFilesWithRetry forwardTo nodeInitGroupsState transformed { it.groups })

        nodeInitGroupsState then nodeMarkPreviousEntryCompleted

        edge(
            nodeMarkPreviousEntryCompleted forwardTo nodeStartNextGroup
            onCondition { storage.getValue(reviewedGroupsKey).size < storage.getValue(groupsKey).size }
        )

        edge(
            nodeMarkPreviousEntryCompleted forwardTo nodeSendReview
            onCondition { storage.getValue(reviewedGroupsKey).size >= storage.getValue(groupsKey).size }
        )

        nodeStartNextGroup then nodeReviewGroup then nodeRecordReview then nodeMarkPreviousEntryCompleted

        nodeSendReview then nodeFinish
    }

private fun formatReview(results: List<ReviewResult>, target: ReviewTarget): String = buildString {
    appendLine("# Code Review — ${target.describe()}")
    appendLine()
    results.forEach { result ->
        appendLine("## ${result.groupDescription} — ${result.verdict}")
        appendLine()
        appendLine(result.findings)
        appendLine()
    }
    val overallVerdict =
        if (results.all { it.verdict == ReviewVerdict.LGTM }) ReviewVerdict.LGTM else ReviewVerdict.NEEDS_CHANGES
    appendLine("## Overall verdict: $overallVerdict")
}

private fun filesCorrectlyGroupedCondition(
    grouping: FileGrouping,
    changedFiles: Set<String>,
): ConditionResult {
    val groupedFiles = grouping.groups.flatMap { it.files }
    val groupedOnce = groupedFiles.groupingBy { it }.eachCount()

    val missing = changedFiles - groupedOnce.keys
    val duplicated = groupedOnce.filterValues { it > 1 }.keys

    if (missing.isEmpty() && duplicated.isEmpty()) return ConditionResult.Approve

    val feedback = buildString {
        if (missing.isNotEmpty()) {
            appendLine("Missing files (not in any group): ${missing.joinToString(", ")}")
        }
        if (duplicated.isNotEmpty()) {
            appendLine("Duplicated files (appear in more than one group): ${duplicated.joinToString(", ")}")
        }
    }.trimEnd()
    return ConditionResult.Reject(feedback)
}

private suspend fun AIAgentContext.sendPlan(entries: List<PlanEntry>) {
    withAcpAgent {
        sendEvent(Event.SessionUpdateEvent(SessionUpdate.PlanUpdate(entries)))
    }
}

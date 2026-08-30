package ai.codereview.agent

import ai.koog.agents.core.tools.annotations.LLMDescription
import com.agentclientprotocol.model.PlanEntryPriority
import kotlinx.serialization.Serializable

sealed interface ReviewTarget {
    /** Compare against the tip of a named branch, e.g. `develop`. */
    data class Branch(val name: String) : ReviewTarget

    /** Compare against a specific commit SHA. */
    data class Commit(val sha: String) : ReviewTarget

    /** Compare against `HEAD~n` — the last n commits on the current branch. */
    data class LastNCommits(val n: Int) : ReviewTarget
}

fun ReviewTarget.toGitRef(): String = when (this) {
    is ReviewTarget.Branch -> name
    is ReviewTarget.Commit -> sha
    is ReviewTarget.LastNCommits -> "HEAD~$n"
}

fun ReviewTarget.describe(): String = when (this) {
    is ReviewTarget.Branch -> "branch '$name'"
    is ReviewTarget.Commit -> "commit $sha"
    is ReviewTarget.LastNCommits -> "last $n commit${if (n == 1) "" else "s"}"
}

@Serializable
data class FileGroup(
    val description: String,
    val files: List<String>,
    val priority: PlanEntryPriority,
)

@Serializable
data class FileGrouping(val groups: List<FileGroup>)

@Serializable
enum class ReviewVerdict { LGTM, NEEDS_CHANGES }

@Serializable
@LLMDescription("The result of reviewing one group of files")
data class ReviewResult(
    @property:LLMDescription("Short description of the reviewed group")
    val groupDescription: String,
    @property:LLMDescription("Detailed findings: issues found, file paths, line numbers, suggestions")
    val findings: String,
    @property:LLMDescription("LGTM if no significant issues, NEEDS_CHANGES otherwise")
    val verdict: ReviewVerdict,
)

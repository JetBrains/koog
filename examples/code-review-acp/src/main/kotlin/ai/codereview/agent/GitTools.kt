package ai.codereview.agent

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import java.io.File

@LLMDescription("Git tools for inspecting changes on the current branch.")
class GitUtils(private val workingDir: File) : ToolSet {

    private var _target: ReviewTarget? = null

    val target: ReviewTarget
        get() = _target ?: error("Review base has not been set yet. Call setBaseBranch, setBaseCommit, or setLastNCommits first.")

    @Tool
    @LLMDescription(
        "Sets the base reference for the review to a named branch (e.g. 'main', 'develop'). " +
            "Must be called before any other tool if comparing against a branch."
    )
    fun setBaseBranch(
        @LLMDescription("Branch name to compare against, e.g. 'main' or 'develop'")
        name: String,
    ): String {
        _target = ReviewTarget.Branch(name.trim())
        return "Base set to ${target.describe()} (git ref: ${target.toGitRef()})"
    }

    @Tool
    @LLMDescription(
        "Sets the base reference for the review to a specific commit SHA. " +
            "Must be called before any other tool if comparing against a commit."
    )
    fun setBaseCommit(
        @LLMDescription("Full or abbreviated commit SHA to compare against")
        sha: String,
    ): String {
        _target = ReviewTarget.Commit(sha.trim())
        return "Base set to ${target.describe()} (git ref: ${target.toGitRef()})"
    }

    @Tool
    @LLMDescription(
        "Sets the base reference for the review to the last N commits on the current branch (i.e. HEAD~N). " +
            "Must be called before any other tool if reviewing recent commits."
    )
    fun setLastNCommits(
        @LLMDescription("Number of recent commits to include in the review")
        n: Int,
    ): String {
        require(n > 0) { "n must be positive, got $n" }
        _target = ReviewTarget.LastNCommits(n)
        return "Base set to ${target.describe()} (git ref: ${target.toGitRef()})"
    }

    fun getChangedFiles(): Set<String> =
        runGit("git", "diff", "--name-only", "${target.toGitRef()}...HEAD")
            .lines()
            .filter { it.isNotBlank() }
            .toSet()

    @Tool
    @LLMDescription("Returns the commit messages on the current branch that are not in the base ref.")
    fun getCommitMessages(): String =
        runGit("git", "log", "--oneline", "${target.toGitRef()}...HEAD")

    @Tool
    @LLMDescription("Returns the unified diff of a specific file between the current branch and the base ref.")
    fun getFileDiff(
        @LLMDescription("Path to the file relative to repository root")
        filePath: String,
    ): String =
        runGit("git", "diff", "${target.toGitRef()}...HEAD", "--", filePath)

    private fun runGit(vararg command: String): String {
        val process = ProcessBuilder(*command)
            .directory(workingDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val exitCode = process.waitFor()
        check(exitCode == 0) { "Git command failed (exit $exitCode): ${command.joinToString(" ")}\n$output" }
        return output
    }
}


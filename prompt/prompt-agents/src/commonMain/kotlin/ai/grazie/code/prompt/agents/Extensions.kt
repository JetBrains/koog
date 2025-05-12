package ai.grazie.code.prompt.agents

import ai.grazie.code.prompt.markdown.MarkdownContentBuilder


public fun MarkdownContentBuilder.trajectory(trajectory: AgentTrajectory) {
    +"Request: ${trajectory.request}"
    newline()
    +"Trajectory"
    for ((index, step) in trajectory.steps.withIndex()) {
        +"Step ${index + 1}: ${step.tool}"
        +"Parameters:"
        codeblock(step.params)
        +"Reason for execution:"
        +step.reason
        +"Outcome:"
        +step.outcome
        newline()
    }
    +"Explanation"
    +trajectory.explanation
}

package ai.koog.runtime

/**
 *
 */
public class AgentFlowKoogRuntimeProvider : AgentFlowRuntimeProvider {

    /**
     *
     */
    override fun provide(): AgentFlowRuntime {
        return AgentFlowKoogRuntime()
    }
}

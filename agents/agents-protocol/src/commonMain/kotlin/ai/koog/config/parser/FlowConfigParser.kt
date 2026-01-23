package ai.koog.config.parser

import ai.koog.flow.FlowConfig

/**
 *
 */
public interface FlowConfigParser {

    /**
     *
     */
    public fun parse(input: String): FlowConfig
}

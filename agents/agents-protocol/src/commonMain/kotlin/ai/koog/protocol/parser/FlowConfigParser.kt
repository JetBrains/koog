package ai.koog.protocol.parser

import ai.koog.protocol.flow.FlowConfig

/**
 *
 */
public interface FlowConfigParser {

    /**
     *
     */
    public fun parse(input: String): FlowConfig
}

package ai.koog.protocol.parser

import ai.koog.protocol.flow.FlowConfig

/**
 * Interface for parsing flow configuration from different formats.
 */
public interface FlowConfigParser {

    /**
     * Parses a flow configuration from a string input.
     */
    public fun parse(input: String): FlowConfig
}

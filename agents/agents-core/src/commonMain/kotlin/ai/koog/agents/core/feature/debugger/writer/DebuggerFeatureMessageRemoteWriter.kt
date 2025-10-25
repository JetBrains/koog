package ai.koog.agents.core.feature.debugger.writer

import ai.koog.agents.core.feature.remote.server.config.ServerConnectionConfig
import ai.koog.agents.core.feature.writer.FeatureMessageRemoteWriter

/**
 * Handles writing debug feature messages remotely to a server, inheriting common functionality
 * from the `FeatureMessageRemoteWriter` base class.
 *
 * @param connectionConfig The configuration settings for establishing the remote server connection.
 */
public class DebuggerFeatureMessageRemoteWriter(connectionConfig: ServerConnectionConfig) :
    FeatureMessageRemoteWriter(connectionConfig)

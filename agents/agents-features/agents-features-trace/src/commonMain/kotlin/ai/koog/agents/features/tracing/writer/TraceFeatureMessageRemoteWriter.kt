package ai.koog.agents.features.tracing.writer

import ai.koog.agents.features.common.remote.server.config.ServerConnectionConfig
import ai.koog.agents.features.common.writer.FeatureMessageRemoteWriter

/**
 * A message processor that sends trace events to a remote server.
 * 
 * This writer captures all trace events and sends them to a configured remote server.
 * Remote tracing is crucial for evaluation and analysis of the working agent in distributed environments.
 * 
 * Tracing to remote servers is particularly useful for:
 * - Centralized collection and analysis of trace data from multiple agents
 * - Real-time monitoring of agent behavior across distributed systems
 * - Integration with specialized analysis and visualization tools
 * - Sharing trace data with team members or other stakeholders
 * 
 * Example usage:
 * ```kotlin
 * val agent = AIAgent(...) {
 *     install(Tracing) {
 *         // Send trace events to a remote server using default connection settings
 *         addMessageProcessor(TraceFeatureMessageRemoteWriter())
 *         
 *         // Send trace events to a specific remote server
 *         addMessageProcessor(TraceFeatureMessageRemoteWriter(
 *             connectionConfig = ServerConnectionConfig(
 *                 host = "trace-collector.example.com",
 *                 port = 8080,
 *                 useSsl = true
 *             )
 *         ))
 *     }
 * }
 * ```
 * 
 * @param connectionConfig Optional configuration for the remote server connection.
 *                         If null, default connection settings will be used.
 */
public class TraceFeatureMessageRemoteWriter(connectionConfig: ServerConnectionConfig? = null)
    : FeatureMessageRemoteWriter(connectionConfig)

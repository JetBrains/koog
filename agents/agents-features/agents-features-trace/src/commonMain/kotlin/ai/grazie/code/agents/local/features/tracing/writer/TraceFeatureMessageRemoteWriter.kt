package ai.grazie.code.agents.local.features.tracing.writer

import ai.grazie.code.agents.local.features.common.remote.server.config.ServerConnectionConfig
import ai.grazie.code.agents.local.features.common.writer.FeatureMessageRemoteWriter

class TraceFeatureMessageRemoteWriter(connectionConfig: ServerConnectionConfig? = null)
    : FeatureMessageRemoteWriter(connectionConfig)

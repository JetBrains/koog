package ai.grazie.code.agents.local.features.tracing.writer

import ai.grazie.code.agents.local.features.common.writer.FeatureMessageRemoteWriter
import ai.grazie.code.agents.local.features.common.remote.server.ServerConnectionConfig

class TraceFeatureMessageRemoteWriter(connectionConfig: ServerConnectionConfig? = null)
    : FeatureMessageRemoteWriter(connectionConfig)

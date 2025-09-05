package ai.koog.agents.core.utils

import ai.koog.prompt.streaming.StreamFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

/**
 * Map a [Flow] of [StreamFrame] to a [Flow] of [String] containing only the text content.
 */
public fun Flow<StreamFrame>.mapTextOnly(): Flow<String> =
    filterIsInstance<StreamFrame.Append>().map { append -> append.text }

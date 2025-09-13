package ai.koog.prompt.streaming

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map

/**
 * Map a [kotlinx.coroutines.flow.Flow] of [StreamFrame] to a [kotlinx.coroutines.flow.Flow] of [String] containing only the text content.
 */
public fun Flow<StreamFrame>.filterTextOnly(): Flow<String> =
    filterIsInstance<StreamFrame.Append>()
        .map { frame -> frame.text }

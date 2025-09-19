package ai.koog.prompt.streaming

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.map

/**
 * Returns a transformed flow of [StreamFrame.Append] objects that contains only the textual content.
 */
public fun Flow<StreamFrame>.filterTextOnly(): Flow<String> =
    filterIsInstance<StreamFrame.Append>()
        .map { frame -> frame.text }

/**
 * Collects the textual content of a [Flow] of [StreamFrame] objects and returns it as a single string.
 */
public suspend fun Flow<StreamFrame>.collectText(): String =
    filterTextOnly().fold("") { acc, s -> acc + s }

@file:JvmName("SQLChatHistoryProviderJvm")

package ai.koog.agents.features.chatmemory.sql

import kotlinx.coroutines.runBlocking

/**
 * Blocking variant of [SQLChatHistoryProvider.migrate] for Java callers.
 *
 * Runs the schema migration synchronously, blocking the calling thread
 * until completion. Prefer the suspend [SQLChatHistoryProvider.migrate]
 * when calling from Kotlin coroutines.
 */
public fun SQLChatHistoryProvider.migrateBlocking() {
    runBlocking { migrate() }
}

package ai.koog.agents.features.pubsub.providers.local

import ai.koog.agents.features.pubsub.message.PubSubMessage
import ai.koog.agents.features.pubsub.message.PubSubStringMessage
import ai.koog.agents.features.pubsub.providers.PubSubException
import ai.koog.agents.features.pubsub.providers.PubSubProvider
import ai.koog.agents.features.pubsub.providers.ReceivedMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.awaitClose
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * File-based implementation of [PubSubProvider] for cross-process local development.
 *
 * This provider enables true cross-process coordination by using the filesystem as a message broker:
 * - Messages are stored as JSON files in a shared directory
 * - Different processes can publish and subscribe through file operations
 * - Supports proper message acknowledgment through file management
 * - Includes cleanup mechanisms for processed messages
 * - Uses file locking to prevent race conditions
 *
 * **Architecture:**
 * - Base directory: `System.tmpdir/koog-pubsub-{instanceId}/`
 * - Messages: `messages/{timestamp}_{messageId}_{topic}.json`
 * - Acknowledged: `acked/{messageId}.marker` (for cleanup tracking)
 * - Polling interval: Configurable (default 100ms)
 *
 * **Ideal for:**
 * - Local development with agents in different processes
 * - Testing distributed agent scenarios without external services
 * - CI/CD environments where Redis/GCP aren't available
 * - Cross-process coordination on a single machine
 *
 * **Limitations:**
 * - Only works on a single machine (not distributed across hosts)
 * - Performance is limited by filesystem operations
 * - Not suitable for high-throughput production scenarios
 * - Requires periodic cleanup to prevent disk space issues
 *
 * Example usage:
 * ```kotlin
 * // Process 1: Publisher
 * val provider = LocalFilePubSubProvider()
 * provider.publish("commands", "execute task", mapOf("priority" to "high"))
 *
 * // Process 2: Subscriber
 * val provider = LocalFilePubSubProvider() // Same shared directory
 * provider.subscribe("commands").collect { message ->
 *     println("Received: ${message.content}")
 *     message.acknowledge() // Removes file from filesystem
 * }
 * ```
 *
 * @property baseDirectory Base directory for message storage (default: system temp + "koog-pubsub")
 * @property pollingIntervalMs Interval for checking new messages (default: 100ms)
 * @property cleanupIntervalMs Interval for cleanup operations (default: 60000ms = 1 minute)
 * @property messageRetentionMs How long to keep acknowledged messages (default: 300000ms = 5 minutes)
 */
@OptIn(ExperimentalUuidApi::class)
public class LocalFilePubSubProvider(
    private val baseDirectory: File = File(System.getProperty("java.io.tmpdir"), "koog-pubsub"),
    private val pollingIntervalMs: Long = 100,
    private val cleanupIntervalMs: Long = 60_000,
    private val messageRetentionMs: Long = 300_000
) : PubSubProvider {

    private companion object {
        private val logger = KotlinLogging.logger { }
        private val json = Json { 
            ignoreUnknownKeys = true
            prettyPrint = false
        }
    }

    private val messagesDir = File(baseDirectory, "messages")
    private val ackedDir = File(baseDirectory, "acked")
    private val subscribersDir = File(baseDirectory, "subscribers")
    
    // Track active subscriptions for health monitoring
    private val activeSubscriptions = mutableSetOf<String>()
    private var isConnected = true
    private var lastCleanup = System.currentTimeMillis()

    init {
        // Ensure directories exist
        messagesDir.mkdirs()
        ackedDir.mkdirs()
        subscribersDir.mkdirs()
        
        logger.info { "LocalFilePubSubProvider initialized with base directory: ${baseDirectory.absolutePath}" }
    }

    override suspend fun publish(message: PubSubMessage): String? {
        return when (message) {
            is PubSubStringMessage -> publish(message.topic, message.content, message.attributes)
            else -> {
                logger.warn { "Unsupported message type for file-based provider: ${message::class.simpleName}" }
                null
            }
        }
    }

    override suspend fun publish(
        topic: String,
        content: String,
        attributes: Map<String, String>
    ): String? {
        if (!isConnected) {
            throw PubSubException("publish", topic, "LocalFile provider is not connected")
        }

        return try {
            val messageId = Uuid.random().toString()
            val timestamp = System.currentTimeMillis()
            
            val fileMessage = FileMessage(
                messageId = messageId,
                topic = topic,
                content = content,
                attributes = attributes,
                timestamp = timestamp
            )

            // Create message file
            val messageFile = File(messagesDir, "${timestamp}_${messageId}_${topic}.json")
            val jsonContent = json.encodeToString(fileMessage)
            
            // Use file locking to ensure atomic write
            withFileLock(messageFile) { file ->
                file.writeText(jsonContent)
            }

            logger.debug { "Published message to file: ${messageFile.name}" }
            messageId

        } catch (e: Exception) {
            throw PubSubException("publish", topic, "Failed to publish message to file system", e)
        }
    }

    override suspend fun subscribe(topic: String, subscriptionId: String?): Flow<ReceivedMessage> {
        return subscribe(listOf(topic))
    }

    override suspend fun subscribe(topics: List<String>): Flow<ReceivedMessage> {
        if (!isConnected) {
            throw PubSubException("subscribe", topics.joinToString(","), "LocalFile provider is not connected")
        }
        
        if (topics.isEmpty()) {
            return emptyFlow()
        }

        logger.info { "Subscribing to topics: ${topics.joinToString()}" }
        
        // Track subscriptions
        synchronized(activeSubscriptions) {
            activeSubscriptions.addAll(topics)
        }

        return callbackFlow {
            val processedMessages = mutableSetOf<String>()
            
            try {
                while (isActive) {
                    // Perform periodic cleanup
                    if (System.currentTimeMillis() - lastCleanup > cleanupIntervalMs) {
                        performCleanup()
                        lastCleanup = System.currentTimeMillis()
                    }
                    
                    // Scan for new messages
                    val messageFiles = messagesDir.listFiles { file: File ->
                        file.isFile && file.name.endsWith(".json") && 
                        topics.any { topic -> file.name.contains("_${topic}.json") }
                    }?.sortedBy { it.name } ?: emptyList<File>()

                    for (messageFile in messageFiles) {
                        if (processedMessages.contains(messageFile.name)) {
                            continue
                        }

                        try {
                            val fileMessage = withFileLock(messageFile) { file ->
                                json.decodeFromString<FileMessage>(file.readText())
                            }

                            if (topics.contains(fileMessage.topic)) {
                                val receivedMessage = LocalFileReceivedMessage(
                                    messageId = fileMessage.messageId,
                                    topic = fileMessage.topic,
                                    content = fileMessage.content,
                                    attributes = fileMessage.attributes,
                                    messageFile = messageFile,
                                    ackedDir = ackedDir
                                )

                                processedMessages.add(messageFile.name)
                                
                                if (isActive) {
                                    trySend(receivedMessage)
                                }
                            }

                        } catch (e: Exception) {
                            logger.error(e) { "Error processing message file: ${messageFile.name}" }
                            // Move corrupted file to avoid repeated processing
                            messageFile.renameTo(File(messageFile.parent, "${messageFile.name}.corrupted"))
                        }
                    }

                    delay(pollingIntervalMs)
                }
            } catch (e: Exception) {
                logger.error(e) { "Error in file subscription loop" }
            }

            awaitClose {
                synchronized(activeSubscriptions) {
                    activeSubscriptions.removeAll(topics.toSet())
                }
                logger.debug { "Unsubscribed from topics: ${topics.joinToString()}" }
            }
        }
    }

    override suspend fun unsubscribe(topic: String, subscriptionId: String?) {
        synchronized(activeSubscriptions) {
            activeSubscriptions.remove(topic)
        }
        logger.debug { "Unsubscribed from topic: $topic" }
    }

    override suspend fun isConnected(): Boolean = isConnected && baseDirectory.exists()

    override suspend fun getHealthInfo(): Map<String, Any> {
        val messageCount = messagesDir.listFiles()?.size ?: 0
        val ackedCount = ackedDir.listFiles()?.size ?: 0
        
        return mapOf(
            "provider" to "local-file",
            "connected" to isConnected(),
            "healthy" to isConnected(),
            "baseDirectory" to baseDirectory.absolutePath,
            "activeSubscriptions" to synchronized(activeSubscriptions) { activeSubscriptions.size },
            "subscribedTopics" to synchronized(activeSubscriptions) { activeSubscriptions.toList() },
            "pendingMessages" to messageCount,
            "acknowledgedMessages" to ackedCount,
            "pollingIntervalMs" to pollingIntervalMs,
            "lastCleanup" to lastCleanup
        )
    }

    override fun close() {
        isConnected = false
        
        synchronized(activeSubscriptions) {
            activeSubscriptions.clear()
        }
        
        // Perform final cleanup
        try {
            performCleanup()
        } catch (e: Exception) {
            logger.warn(e) { "Error during final cleanup" }
        }
        
        logger.info { "LocalFilePubSubProvider closed" }
    }

    private fun performCleanup() {
        try {
            val now = System.currentTimeMillis()
            
            // Clean up old acknowledged messages
            ackedDir.listFiles()?.forEach { ackedFile ->
                if (now - ackedFile.lastModified() > messageRetentionMs) {
                    ackedFile.delete()
                }
            }
            
            // Clean up very old unprocessed messages (prevent disk space issues)
            messagesDir.listFiles()?.forEach { messageFile ->
                if (now - messageFile.lastModified() > messageRetentionMs * 2) {
                    logger.warn { "Cleaning up old unprocessed message: ${messageFile.name}" }
                    messageFile.delete()
                }
            }
            
            logger.debug { "Cleanup completed" }
            
        } catch (e: Exception) {
            logger.error(e) { "Error during cleanup" }
        }
    }

    private inline fun <T> withFileLock(file: File, action: (File) -> T): T {
        val lockFile = File(file.parent, "${file.name}.lock")
        
        return RandomAccessFile(lockFile, "rw").use { raf ->
            raf.channel.use { channel ->
                var lock: FileLock? = null
                try {
                    lock = channel.lock()
                    action(file)
                } finally {
                    lock?.release()
                    lockFile.delete()
                }
            }
        }
    }
}

/**
 * Message format for file-based storage.
 */
@Serializable
private data class FileMessage(
    val messageId: String,
    val topic: String,
    val content: String,
    val attributes: Map<String, String>,
    val timestamp: Long
)

/**
 * File-based ReceivedMessage that handles acknowledgments through file operations.
 */
private class LocalFileReceivedMessage(
    messageId: String,
    topic: String,
    content: String,
    attributes: Map<String, String>,
    private val messageFile: File,
    private val ackedDir: File
) : ReceivedMessage(messageId, topic, content, attributes, messageFile) {
    
    private companion object {
        private val logger = KotlinLogging.logger { }
    }
    
    override suspend fun acknowledge() {
        try {
            // Move message to acknowledged directory
            val ackedFile = File(ackedDir, "${messageId}.marker")
            ackedFile.createNewFile()
            
            // Remove original message file
            if (messageFile.exists()) {
                messageFile.delete()
            }
            
            logger.debug { "Acknowledged message: $messageId" }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to acknowledge message: $messageId" }
        }
    }
    
    override suspend fun nack() {
        // For file-based provider, nack could mean:
        // 1. Leave the message file for reprocessing
        // 2. Move to a "failed" directory
        // 3. Add a retry counter
        
        // For now, just log the nack - message will be reprocessed
        logger.debug { "Nacked message: $messageId (file will be reprocessed)" }
    }
}
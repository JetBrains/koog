package ai.koog.agents.features.longtermmemory.aws

import ai.koog.agents.features.longtermmemory.aws.augmentation.AgentcoreMemoryStrategy
import ai.koog.rag.base.TextDocument

/**
 * A memory record retrieved from AWS Bedrock AgentCore, wrapping the content and metadata of a MemoryRecordSummary.
 *
 * @property content The main textual content to be embedded and searched
 * @property id Unique identifier for the record
 * @property metadata Flexible key-value metadata for filtering and custom fields.
 *   Values must be primitive types (String, Number, or Boolean) when used with
 *   AWS Bedrock AgentCore backends.
 */
public data class AgentcoreMemoryRecord(
    /**
     * The main textual content to be embedded and searched
     */
    override val content: String,
    /**
     * Unique identifier for the record
     */
    override val id: String? = null,
    /**
     * Flexible key-value metadata for filtering and custom fields
     */
    override val metadata: Map<String, Any> = emptyMap(),
    /**
     * The AWS Bedrock AgentCore memory strategy that determines how this record is used
     * during prompt augmentation (e.g. injected into the system message or used to rewrite
     * the last user message).
     *
     * @see AgentcoreMemoryStrategy
     */
    val strategy: AgentcoreMemoryStrategy
) : TextDocument

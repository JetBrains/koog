package ai.koog.rag.base.storage.capability

/**
 * Mixin interface for storage implementations that can report their supported capabilities.
 *
 * Storage backends implement this interface to advertise which search and filtering features
 * they support (e.g., similarity search, keyword search, hybrid search, metadata filtering).
 * Consumers can query capabilities at runtime to adapt their search strategy accordingly.
 *
 * @see StorageCapability for the list of available capabilities
 * @see CapabilityDetails for optional per-capability metadata
 */
public interface CapabilityAwareStorage {
    /**
     * The set of capabilities supported by this storage implementation.
     */
    public val capabilities: Set<StorageCapability>

    /**
     * Returns optional metadata details for the given [capability], or `null` if no additional
     * details are available.
     *
     * @param capability the capability to retrieve details for
     * @return capability-specific metadata, or `null`
     */
    public fun capabilityDetails(capability: StorageCapability): CapabilityDetails? = null

    /**
     * Checks whether this storage supports the given [capability].
     *
     * @param capability the capability to check
     * @return `true` if the capability is supported, `false` otherwise
     */
    public fun supports(capability: StorageCapability): Boolean =
        capability in capabilities
}

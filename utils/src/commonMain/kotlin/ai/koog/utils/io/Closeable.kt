package ai.koog.utils.io

/**
 * Represents a resource or entity that can be closed to release any associated resources.
 */
public interface Closeable {

    /**
     * Closes the current resource and releases any underlying resources associated with it.
     */
    public suspend fun close()
}

/**
 * Executes the given [action] block on this [Closeable] resource and ensures that the resource
 * is closed after the block execution, whether it completes normally or with an exception.
 */
public suspend inline fun <Type : Closeable, Return> Type.use(action: suspend (Type) -> Return): Return {
    val instance = this
    try {
        return action(instance)
    } finally {
        instance.close()
    }
}

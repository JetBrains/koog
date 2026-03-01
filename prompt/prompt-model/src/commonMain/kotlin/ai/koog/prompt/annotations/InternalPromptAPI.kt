package ai.koog.prompt.annotations

/**
 * Indicates that the annotated API is internal to prompt-related implementations and not intended for public use.
 *
 * Classes, functions, or members marked with this annotation are part of the internal functionality of the Koog core infrastructure
 * and are not guaranteed to maintain backwards compatibility. They are subject to change, removal, or modification without notice.
 *
 * This annotation is primarily used to signal to developers that the API is not designed for general application development
 * and should only be used with caution in specialized scenarios, such as extending or modifying internal behavior.
 */
@RequiresOptIn
public annotation class InternalPromptAPI

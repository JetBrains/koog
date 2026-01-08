/**
 * Kotlin 2.3 compatibility annotations and utilities.
 *
 * This file provides forward-compatible annotations that become fully functional
 * with Kotlin 2.3's experimental features:
 *
 * ## Return Value Checker (`-Xreturn-value-checker=check`)
 *
 * Functions annotated with [@MustUseReturnValue] will generate compiler warnings
 * when their return values are ignored.
 *
 * ## Explicit Backing Fields (`-Xexplicit-backing-fields`)
 *
 * Kotlin 2.3 introduces explicit backing fields using the `field` keyword.
 * This allows separating the backing field type from the property type:
 *
 * ```kotlin
 * // Kotlin 2.3+ syntax (not available in 2.2):
 * var items: List<String>
 *     field = mutableListOf()  // Backing field is MutableList
 *     get() = field.toList()   // Expose as immutable
 *     set(value) { field.clear(); field.addAll(value) }
 *
 * // Pre-2.3 equivalent (current approach):
 * private val _items = mutableListOf<String>()
 * val items: List<String> get() = _items.toList()
 * ```
 *
 * When Kotlin 2.3 is adopted, candidates for explicit backing fields in this module:
 * - Properties that need mutable backing but immutable public access
 * - Properties with validation in setters
 *
 * @see <a href="https://kotlinlang.org/docs/whatsnew23.html">Kotlin 2.3 What's New</a>
 */
package ai.koog.prompt.executor.litertlm.client

/**
 * Marks a function whose return value should not be ignored.
 *
 * On Kotlin 2.3+ with `-Xreturn-value-checker=check`, the compiler will warn
 * if the return value of a function marked with this annotation is unused.
 *
 * On Kotlin < 2.3, this annotation is a no-op but serves as documentation
 * for the intended contract.
 *
 * ## Example
 *
 * ```kotlin
 * @MustUseReturnValue
 * suspend fun create(config: Config): Client
 *
 * // Kotlin 2.3+ will warn:
 * create(config)  // Warning: Return value of 'create' is unused
 *
 * // Correct usage:
 * val client = create(config)  // OK
 * ```
 *
 * @see <a href="https://kotlinlang.org/docs/whatsnew23.html">Kotlin 2.3 What's New</a>
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY_GETTER
)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
public annotation class MustUseReturnValue(
    val message: String = "The return value of this function should not be ignored"
)

/**
 * Marks a return value that can be safely ignored.
 *
 * Use this to suppress warnings from [MustUseReturnValue] when ignoring
 * the return value is intentional.
 *
 * On Kotlin < 2.3, this annotation is a no-op.
 */
@Target(AnnotationTarget.EXPRESSION)
@Retention(AnnotationRetention.SOURCE)
public annotation class IgnorableReturnValue

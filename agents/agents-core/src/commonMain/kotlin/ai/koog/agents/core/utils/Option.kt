package ai.koog.agents.core.utils

internal sealed class Option<out A> {
    abstract val value: A

    abstract val isEmpty: Boolean

    inline fun <B> map(f: (A) -> B): Option<B> =
        if (isEmpty) None else Some(f(value))

    inline fun <B> flatMap(f: (A) -> Option<B>): Option<B> =
        if (isEmpty) None else f(value)

    inline fun filter(f: (A) -> Boolean): Option<A> =
        if (isEmpty || f(value)) this else None

    inline fun forEach(f: (A) -> Unit) {
        if (!isEmpty) f(value)
    }
}

internal data object None : Option<Nothing>() {
    override val isEmpty: Boolean = true

    override val value get() = throw NoSuchElementException("None.value")
}

internal data class Some<T>(override val value: T) : Option<T>() {
    override val isEmpty: Boolean = false
}

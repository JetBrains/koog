package ai.grazie.code.agents.local.utils

import kotlin.reflect.KProperty

/**
 * A property delegate that ensures the property can only be accessed or modified
 * when it is in an active state, as determined by a provided predicate.
 *
 * @param T The type of the value held by this property.
 * @property value The initial value of the property.
 * @property checkIsActive A lambda function that determines if the property is active.
 */
internal class ActiveProperty<T>(
    private var value: T,
    private val checkIsActive: () -> Boolean
) {
    private fun validate() {
        check(checkIsActive()) { "Cannot use the property because it is not active anymore" }
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        validate()
        return value
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        validate()
        this.value = value
    }
}

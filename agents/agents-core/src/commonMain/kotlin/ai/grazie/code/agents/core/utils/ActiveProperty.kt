package ai.grazie.code.agents.core.utils

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

    /**
     **/
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        validate()
        return value
    }

    /**
     * Sets the value of the property while ensuring that it satisfies the active state validation.
     *
     * @param thisRef The reference to the object this property is part of.
     * @param property Metadata about the property being accessed.
     * @param value The new value to assign to the property.
     */
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        validate()
        this.value = value
    }
}

package ai.koog.agents.core.utils

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
     * Provides the logic to retrieve the value of a delegated property.
     *
     * @param thisRef The object for which the property value is requested. Can be null if the property is global or does not depend on a specific class instance.
     * @param property Metadata for the property being accessed.
     * @return The value of the delegated property.
     */
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

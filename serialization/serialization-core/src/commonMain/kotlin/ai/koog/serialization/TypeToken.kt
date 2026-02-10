@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.serialization

import ai.koog.serialization.annotations.InternalKoogSerializationApi
import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Platform-agnostic type token for runtime type representation.
 *
 * Sealed interface with platform-specific implementations to enable exhaustive when expressions.
 */
public expect sealed interface TypeToken

/**
 * Type token based on [KType].
 *
 * @property type The [KType] representing the type.
 */
public class KotlinTypeToken(
    public val type: KType,
) : TypeToken

/**
 * Type token implementation based on [KClass] with generic type arguments information.
 *
 * @property klass The [KClass] representing the class.
 * @property typeArguments List of [TypeToken] representing the generic type arguments.
 */
public class KotlinClassToken(
    public val klass: KClass<*>,
    public val typeArguments: List<TypeToken> = emptyList(),
) : TypeToken

/**
 * Temporary used during migration from [kotlinx.serialization.KSerializer] to [TypeToken] in public APIs.
 */
// TODO finalize the migration and remove
@InternalKoogSerializationApi
public class KSerializerTypeToken<T>(
    public val serializer: KSerializer<T>
) : TypeToken

// Factories

/**
 * Creates a [KotlinTypeToken] from a Kotlin [KType].
 */
public fun typeToken(type: KType): KotlinTypeToken = KotlinTypeToken(type)

/**
 * Creates a [KotlinTypeToken] from [T]
 */
public inline fun <reified T> typeToken(): KotlinTypeToken = typeToken(typeOf<T>())

/**
 * Creates a [KotlinClassToken] from a Kotlin [KClass] and optional generic type arguments.
 */
public fun typeToken(klass: KClass<*>, typeArguments: List<TypeToken> = emptyList()): KotlinClassToken =
    KotlinClassToken(klass, typeArguments)

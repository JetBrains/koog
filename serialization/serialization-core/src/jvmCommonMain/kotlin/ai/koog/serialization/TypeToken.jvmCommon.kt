@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.koog.serialization

import java.lang.reflect.Type

@Suppress("MissingKDocForPublicAPI")
public actual sealed interface TypeToken {
    /**
     * Factory functions to create [TypeToken]
     */
    public companion object {
        /**
         * Creates a [JavaTypeToken] from a Java [Type].
         *
         * Java usage:
         * ```java
         * TypeToken.of(MyClass.class);
         * ```
         */
        @JvmStatic
        public fun of(type: Type): JavaTypeToken = JavaTypeToken(type)

        /**
         * Creates a [JavaTypeToken] from a [TypeCapture] anonymous subclass, preserving generic type information.
         *
         * Java usage:
         * ```java
         * // Non-generic
         * TypeToken.of(new TypeCapture<MyClass>() {});
         *
         * // Generic
         * TypeToken.of(new TypeCapture<List<String>>() {});
         * ```
         */
        @JvmStatic
        public fun of(capture: TypeCapture<*>): JavaTypeToken {
            val superClass = capture.javaClass.genericSuperclass
            require(superClass is java.lang.reflect.ParameterizedType) {
                "TypeCapture must be parameterized. Use: new TypeCapture<YourType>() {}"
            }
            return JavaTypeToken(superClass.actualTypeArguments[0])
        }

        /**
         * Creates a [JavaClassToken] from a Java [Class] with generic type arguments information.
         */
        @JvmStatic
        @JvmOverloads
        public fun of(
            klass: Class<*>,
            typeArguments: List<TypeToken> = emptyList(),
        ): JavaClassToken = JavaClassToken(klass, typeArguments)
    }
}

/**
 * Helper abstract class for capturing generic types from Java via anonymous subclass.
 */
public abstract class TypeCapture<@Suppress("unused") T>

/**
 * Type token based on [Type].
 */
public class JavaTypeToken(
    public val type: Type,
) : TypeToken

/**
 * Type token based on [Class] with generic type arguments information.
 */
public class JavaClassToken(
    public val klass: Class<*>,
    public val typeArguments: List<TypeToken> = emptyList(),
) : TypeToken

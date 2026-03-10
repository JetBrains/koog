---
name: split-jvm-nonjvm
description: |
    Splits a class or several classes in the common source set commonMain into JVM and non-JVM parts into jvmCommonMain and nonJvmCommonMain. 
    This is used to provide some JVM-specific functionality for a class on JVM targets (e.g. Kotlin/JVM or Java backend or Android).
---

The following example shows how to split a class into JVM and non-JVM parts.
When using this skill, avoid unnecessarily looking at existing implementations of the split in the project, just follow this instruction.
You can, however, check existing implementations for reference if you have a complex case or otherwise stuck.

Here's a sample class in a `commonMain` source set
```kt
package com.example

/**
* A sample class with an external dependency, some logic and public methods
 * @param name Some public property
 * @param myRepo Some external dependency, private property
*/
public class MyClass(
    public val name: String,
    private val myRepo: MyRepo,
) {
    /**
     * Public API method
     */
    public fun doSomething(): String {
        return fetchData()
    }
    
    /**
     * Private implementation logic
     */
    private fun fetchData(): String {
        return myRepo.getData()
    }
    
    /*
     * Internal method
     */
    internal fun doInternal(): String {
        return "Internal"
    }
}
```

In the same package as `MyClass` in `commonMain`, create the following files. The original `MyClass.kt` file will be overwritten with the expect class (see step three).

First, the API interface describing the contract. It should contain only public symbols from the `MyClass`.
Take KDocs from the respective symbols in `MyClass` and add them to the interface.

```kt
package com.example

/**
 * API for [MyClass]
 */
public interface MyClassAPI {
    /**
     * Some public property
     */
    public val name: String
    
    /**
     * Public API method
     */
    public fun doSomething(): String
}
```

Second, `MyClassImpl.kt` — implementation of the API interface providing the implementation logic. The filename matches the class name.

```kt
internal class MyClassImpl(
    override val name: String,
    private val myRepo: MyRepo,
) : MyClassAPI {
    override fun doSomething(): String {
        return fetchData()
    }
    
    private fun fetchData(): String {
        return myRepo.getData()
    }
    
    internal fun doInternal(): String {
        return "Internal"
    }
}
```

Third, the original class becomes `expect` class
`MyClass` should extend `MyClassAPI` and override all public symbols from `MyClassAPI`, without method bodies and property values, since `expect` classes can't have any.
If `MyClass` had any internal symbols, these should also be listed in the `expect` class along with their KDocs.
If `MyClass` had a KDoc, add it on top of the expect class without parameters/properties description.
If `MyClass` original KDoc described certain parameters/properties that it accepted in its constructor, add these to KDoc of the expect class secondary constructor.
This constructor KDoc can't have `@property` tag for parameters, use only `@param` for all parameters, even if the original was `@property`.

```kt
// Required to put on top of the file containing expect classes to suppress IntelliJ warning about the experimental feature
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.example

/**
 * A sample class with an external dependency, some logic and public methods
 */
public expect class MyClass internal constructor(
    delegate: MyClassImpl,
) : MyClassAPI {
    /**
     * Constructs a new instance of [MyClass]
     * 
     * @param name Some public property
     * @param myRepo Some external dependency,
     */
    public constructor(
        name: String,
        myRepo: MyRepo,
    )
    
    internal val delegate: MyClassImpl
    
    override val name: String
    override fun doSomething(): String
    
    /**
     * Internal method
     */
    internal fun doInternal(): String
}
```

Fourth, ensure that `jvmCommonMain` and `nonJvmCommonMain` source sets have a package directory for `MyClass`, i.e., `com.example`. If not, create missing package directories.
The source sets themselves are already declared in the Gradle convention plugin — you do NOT need to add them to the module's `build.gradle.kts` unless you need to add dependencies specifically for these source sets.
Then, create `MyClass.kt` files in `jvmCommonMain` and `nonJvmCommonMain` source sets.

After these steps, the structure should look as follows, omitting irrelevant packages and files:
```
commonMain/
    kotlin/
        com/example/
            MyClass.kt
            MyClassAPI.kt
            MyClassImpl.kt
jvmCommonMain/
    kotlin/
        com/example/
            MyClass.kt
nonJvmCommonMain/
    kotlin/
        com/example/
            MyClass.kt
```

Fifth, in `MyClass.kt` files in `jvmCommonMain` and `nonJvmCommonMain` create `actual` declarations for `MyClass`.
Initially, they should be identical for both source sets.
It should use `by delegate` to delegate all public symbols from `MyClassAPI` to the implementation, and direct delegation to delegate internal symbols.

```kt
// Suppress IntelliJ warnings
@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.example

public actual class MyClass internal actual constructor(
    internal actual val delegate: MyClassImpl,
) : MyClassAPI by delegate {
    public actual constructor(
        name: String,
        myRepo: MyRepo,
    ) : this(
        delegate = MyClassImpl(name, myRepo)
    )
    
    internal actual fun doInternal(): String = delegate.doInternal()
}
```

Sixth, optional, step.
If the users asked for certain JVM-specific/non-JVM-specific functionality, these actual classes might contain additional 
public symbols not present in the API interface. Such additional public symbols should be documented properly and concisely with KDoc

For example, `jvmCommonMain` implementation that uses JVM-specific API
```kt
// Suppress IntelliJ warnings
@file:Suppress("MissingKDocForPublicAPI", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.example

public actual class MyClass internal actual constructor(
    internal actual val delegate: MyClassImpl,
) : MyClassAPI by delegate {
    public actual constructor(
        name: String,
        myRepo: MyRepo,
    ) : this(
        delegate = MyClassImpl(name, myRepo)
    )
    
    /**
     * Gets environment variable value by [key]
     */
    public fun getEnv(key: String): String = System.getenv(key)
}
```

Seventh, if `MyClass` had any tests in the original `commonTest` source set, use `MyClassImpl` in the test to verify common implementation.
Do not rename the test, it should still be called e.g. `MyClassTest`.
If you need to test some platform-specific functionality, create tests in `jvmCommonTest` and/or `nonJvmCommonTest` source sets.
Kotlin tests for JVM-specific functionality should go to `jvmCommonTest`.
IMPORTANT: if the test suite is written in Java, it MUST go to `jvmTest`, not `jvmCommonTest`. KMP only compiles Java code in `jvmMain`/`jvmTest` source sets.

Finally, ensure that the implementation compiles by running:
```sh
./gradlew :mymodule:compileKotlinJvm
./gradlew :mymodule:compileKotlinJs
```
Replace `:mymodule` with the actual Gradle module path derived from the file location (e.g., `agents/agents-core` → `:agents:agents-core`).
If both pass, the split is working correctly.

### IntelliJ false positives
Due to the experimental state of expect/actual classifiers, IntelliJ may report false positive errors such as "no actual for expect" or "no expect for actual". 
These diagnostics should NOT be trusted. The only reliable verification is running the Gradle compile tasks listed above.

## Important notes

### Imports
When creating the new files (API interface, Impl, expect class, actual classes), carry over all relevant imports from the original class.

### Annotations
Classes being split should not have annotations. If the original class has annotations (e.g., `@Serializable`, `@Deprecated`), ask the user explicitly how to handle them before proceeding.

### Companion objects
Classes being split should not have companion objects. If the original class has a companion object, ask the user explicitly how to handle it before proceeding.

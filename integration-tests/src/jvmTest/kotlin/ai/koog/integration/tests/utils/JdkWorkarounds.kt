package ai.koog.integration.tests.utils

import java.text.Normalizer

/**
 * Workarounds for JDK bugs that affect integration tests.
 */
object JdkWorkarounds {
    private val lock = Object()
    private var initialized = false

    init {
        // Eagerly initialize Normalizer when this object is loaded.
        // This static initializer runs once per JVM, preventing race conditions.
        initializeNormalizer()
    }

    /**
     * Workaround for JDK 21 ExceptionInInitializerError in ICUBinary during Normalizer2 initialization.
     * This happens in a race condition when multiple threads (coroutines) initialize SSL/TLS related classes.
     *
     * Called automatically in init block, but can also be called explicitly from @BeforeAll methods.
     * Uses synchronized block to prevent race condition when JUnit runs @BeforeAll methods in parallel.
     */
    @JvmStatic
    fun initializeNormalizer() {
        synchronized(lock) {
            if (!initialized) {
                // Force ICU data loading in a synchronized context
                Normalizer.normalize("test", Normalizer.Form.NFKD)
                initialized = true
            }
        }
    }
}

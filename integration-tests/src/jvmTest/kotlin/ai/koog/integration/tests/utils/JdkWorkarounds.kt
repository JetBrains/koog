package ai.koog.integration.tests.utils

import java.text.Normalizer

/**
 * Workarounds for JDK bugs that affect integration tests.
 */
object JdkWorkarounds {
    private val lock = Object()
    private var initialized = false

    /**
     * Workaround for JDK 21 ExceptionInInitializerError in ICUBinary during Normalizer2 initialization.
     * This happens in a race condition when multiple threads (coroutines) initialize SSL/TLS related classes.
     *
     * Must be called explicitly from @BeforeAll methods.
     * Uses synchronized block to prevent race condition when JUnit runs @BeforeAll methods in parallel.
     *
     * NOTE: This doesn't actually fix the race condition in CI, because the error happens
     * during static initialization before any test code runs. Keeping this for documentation purposes.
     */
    @JvmStatic
    fun initializeNormalizer() {
        synchronized(lock) {
            if (!initialized) {
                try {
                    // Force ICU data loading in a synchronized context
                    Normalizer.normalize("test", Normalizer.Form.NFKD)
                    initialized = true
                } catch (e: ExceptionInInitializerError) {
                    // Swallow the error if it happens here - it means the race condition already occurred
                    System.err.println("WARNING: Failed to initialize Normalizer: ${e.message}")
                }
            }
        }
    }
}

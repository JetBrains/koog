package ai.koog.integration.tests.utils

import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * JUnit extension that ensures JdkWorkarounds is initialized before any tests run.
 * This is registered globally via META-INF/services.
 */
class JdkWorkaroundsExtension : BeforeAllCallback {
    override fun beforeAll(context: ExtensionContext) {
        // Trigger JdkWorkarounds class loading, which will run its init block
        JdkWorkarounds.initializeNormalizer()
    }
}

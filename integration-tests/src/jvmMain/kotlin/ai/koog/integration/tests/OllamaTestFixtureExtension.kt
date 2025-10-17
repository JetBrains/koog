package ai.koog.integration.tests

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.support.AnnotationSupport.findAnnotatedFields
import org.junit.platform.commons.support.ModifierSupport
import java.lang.reflect.Field

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class InjectOllamaTestFixture

class OllamaTestFixtureExtension : BeforeAllCallback, AfterAllCallback {

    companion object {
        private val SHARED_FIXTURE_LOCK = Any()
        private var sharedFixture: OllamaTestFixture? = null

        init {
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    synchronized(SHARED_FIXTURE_LOCK) {
                        sharedFixture?.runCatching { tearDown() }
                        sharedFixture = null
                    }
                }
            )
        }
    }

    override fun beforeAll(context: ExtensionContext) {
        val testClass = context.requiredTestClass
        try {
            findFields(testClass).forEach { field ->
                field.isAccessible = true
                val fixture = acquireSharedFixture()
                try {
                    field.set(null, fixture)
                } catch (e: Exception) {
                    println("Failed to inject fixture for field ${field.name}: ${e.message}")
                    disposeFixture()
                    throw e
                }
            }
        } catch (e: Exception) {
            println("Error in beforeAll: ${e.message}")
            throw e
        }
    }

    override fun afterAll(context: ExtensionContext) {
        val testClass = context.requiredTestClass

        try {
            findFields(testClass).forEach { field ->
                field.isAccessible = true
                try {
                    field.set(null, null)
                } catch (e: Exception) {
                    println("Failed to nullify field ${field.name}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("Error nullifying fields: ${e.message}")
        }
    }

    private fun findFields(testClass: Class<*>): List<Field> {
        return findAnnotatedFields(
            testClass,
            InjectOllamaTestFixture::class.java,
        ) { field ->
            ModifierSupport.isStatic(field) && field.type == OllamaTestFixture::class.java
        }
    }

    private fun acquireSharedFixture(): OllamaTestFixture {
        synchronized(SHARED_FIXTURE_LOCK) {
            sharedFixture?.let { return it }

            val fixture = OllamaTestFixture()
            runCatching { fixture.setUp() }
                .onFailure { error ->
                    println("Failed to set up shared Ollama fixture: ${error.message}")
                    fixture.runCatching { tearDown() }
                    throw error
                }
            sharedFixture = fixture
            return fixture
        }
    }

    private fun disposeFixture() {
        synchronized(SHARED_FIXTURE_LOCK) {
            sharedFixture?.runCatching { tearDown() }
            sharedFixture = null
        }
    }
}

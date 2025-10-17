package ai.koog.integration.tests

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.platform.commons.support.AnnotationSupport.findAnnotatedFields
import org.junit.platform.commons.support.ModifierSupport
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class InjectOllamaTestFixture

class OllamaTestFixtureExtension : BeforeAllCallback, AfterAllCallback {

    companion object {
        private val SHARED_FIXTURE_LOCK = Any()
        private var sharedFixture: OllamaTestFixture? = null
        private var activeConsumers: Int = 0
        private val TEST_CONSUMERS = ConcurrentHashMap<String, Int>()

        init {
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    synchronized(SHARED_FIXTURE_LOCK) {
                        sharedFixture?.runCatching { tearDown() }
                        sharedFixture = null
                        activeConsumers = 0
                    }
                }
            )
        }
    }

    override fun beforeAll(context: ExtensionContext) {
        val testClass = context.requiredTestClass
        val testId = context.uniqueId
        try {
            var injectedFields = 0
            findFields(testClass).forEach { field ->
                field.isAccessible = true
                val fixture = acquireSharedFixture()
                try {
                    field.set(null, fixture)
                    injectedFields += 1
                } catch (e: Exception) {
                    println("Failed to inject fixture for field ${field.name}: ${e.message}")
                    releaseSharedFixture()
                    throw e
                }
            }

            TEST_CONSUMERS[testId] = injectedFields
        } catch (e: Exception) {
            println("Error in beforeAll: ${e.message}")
            throw e
        }
    }

    override fun afterAll(context: ExtensionContext) {
        val testId = context.uniqueId
        val fixturesCount = TEST_CONSUMERS.remove(testId) ?: 0

        val testClass = context.requiredTestClass
        repeat(fixturesCount) { releaseSharedFixture() }

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
            if (sharedFixture == null) {
                val fixture = OllamaTestFixture()
                fixture.setUp()
                sharedFixture = fixture
            }
            activeConsumers += 1
            return sharedFixture!!
        }
    }

    private fun releaseSharedFixture() {
        synchronized(SHARED_FIXTURE_LOCK) {
            if (activeConsumers > 0) {
                activeConsumers -= 1
            }
        }
    }
}

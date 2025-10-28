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
        private val FIXTURES = ConcurrentHashMap<String, MutableList<OllamaTestFixture>>()
    }

    override fun beforeAll(context: ExtensionContext) {
        val testClass = context.requiredTestClass
        val testId = context.uniqueId
        val fixtures = mutableListOf<OllamaTestFixture>()

        try {
            findFields(testClass).forEach { field ->
                field.isAccessible = true
                val fixture = OllamaTestFixture()

                try {
                    fixture.setup()
                    field.set(null, fixture)
                    fixtures.add(fixture)
                } catch (e: Exception) {
                    println("Failed to setup fixture for field ${field.name}: ${e.message}")
                    fixtures.forEach { it.teardown() }
                    throw e
                }
            }

            FIXTURES[testId] = fixtures
        } catch (e: Exception) {
            println("Error in beforeAll: ${e.message}")
            throw e
        }
    }

    override fun afterAll(context: ExtensionContext) {
        val testId = context.uniqueId
        val fixtures = FIXTURES.remove(testId) ?: emptyList()

        val testClass = context.requiredTestClass
        val errors = mutableListOf<Exception>()

        fixtures.forEach { fixture ->
            try {
                fixture.teardown()
            } catch (e: Exception) {
                println("Failed to teardown fixture: ${e.message}")
                e.printStackTrace()
                errors.add(e)
            }
        }

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

        if (errors.isNotEmpty()) {
            throw errors.first()
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
}

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
    override fun beforeAll(context: ExtensionContext) {
        val testClass = context.requiredTestClass
        setupFields(testClass)
    }

    override fun afterAll(context: ExtensionContext) {
        val testClass = context.requiredTestClass
        tearDownFields(testClass)
    }

    private fun findFields(testClass: Class<*>): List<Field> {
        return findAnnotatedFields(
            testClass,
            InjectOllamaTestFixture::class.java,
        ) { field -> ModifierSupport.isStatic(field) && field.type == OllamaTestFixture::class.java }
    }

    private fun setupFields(testClass: Class<*>) {
        findFields(testClass).forEach { field ->
            field.isAccessible = true
            field.set(null, OllamaTestFixture().apply { setUp() })
        }
    }

    private fun tearDownFields(testClass: Class<*>) {
        findFields(testClass).forEach { field ->
            field.isAccessible = true
            (field.get(null) as OllamaTestFixture).tearDown()
            field.set(null, null)
        }
    }
}

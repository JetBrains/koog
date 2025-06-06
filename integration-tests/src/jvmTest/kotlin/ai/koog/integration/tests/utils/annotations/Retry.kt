package ai.koog.integration.tests.utils.annotations

import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.ExtendWith

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@TestTemplate
@ExtendWith(RetryExtension::class)
annotation class Retry(val times: Int = 3)
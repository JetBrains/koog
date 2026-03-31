package ai.koog.spring.ai.common.conditions

import org.springframework.boot.autoconfigure.condition.ConditionOutcome
import org.springframework.boot.autoconfigure.condition.SpringBootCondition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.core.type.AnnotatedTypeMetadata
import kotlin.reflect.jvm.jvmName

// TODO: The code below was copied from koog-spring-boot-starter. Move it to some shared module.

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Conditional(OnPropertyNotEmptyCondition::class)
public annotation class ConditionalOnPropertyNotEmpty(
    val prefix: String = "",
    val name: String
)

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Conditional(OnPropertyMissingOrEmptyCondition::class)
public annotation class ConditionalOnPropertyMissingOrEmpty(
    val prefix: String = "",
    val name: String
)

public class OnPropertyNotEmptyCondition : SpringBootCondition() {

    override fun getMatchOutcome(
        context: ConditionContext,
        metadata: AnnotatedTypeMetadata
    ): ConditionOutcome {
        val propertyKey = resolvePropertyKey(metadata, ConditionalOnPropertyNotEmpty::class.jvmName)
        val value = context.environment.getProperty(propertyKey)

        return if (!value.isNullOrEmpty()) {
            ConditionOutcome.match("Property '$propertyKey' has non-empty value")
        } else {
            ConditionOutcome.noMatch("Property '$propertyKey' is missing or empty")
        }
    }
}

public class OnPropertyMissingOrEmptyCondition : SpringBootCondition() {

    override fun getMatchOutcome(
        context: ConditionContext,
        metadata: AnnotatedTypeMetadata
    ): ConditionOutcome {
        val propertyKey = resolvePropertyKey(metadata, ConditionalOnPropertyMissingOrEmpty::class.jvmName)
        val value = context.environment.getProperty(propertyKey)

        return if (value.isNullOrEmpty()) {
            ConditionOutcome.match("Property '$propertyKey' is missing or empty")
        } else {
            ConditionOutcome.noMatch("Property '$propertyKey' has non-empty value")
        }
    }
}

private fun resolvePropertyKey(metadata: AnnotatedTypeMetadata, annotationName: String): String {
    val attributes = metadata.getAllAnnotationAttributes(annotationName)
    val prefix = attributes?.get("prefix")?.firstOrNull() as? String ?: ""
    val name = attributes?.get("name")?.firstOrNull() as? String ?: ""
    return if (prefix.isNotEmpty()) "$prefix.$name" else name
}

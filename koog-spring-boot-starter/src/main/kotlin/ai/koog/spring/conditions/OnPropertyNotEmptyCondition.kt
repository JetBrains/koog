package ai.koog.spring.conditions

import org.springframework.boot.autoconfigure.condition.ConditionOutcome
import org.springframework.boot.autoconfigure.condition.SpringBootCondition
import org.springframework.context.annotation.ConditionContext
import org.springframework.context.annotation.Conditional
import org.springframework.core.type.AnnotatedTypeMetadata
import kotlin.reflect.jvm.jvmName

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Conditional(OnPropertyNotEmptyCondition::class)
public annotation class ConditionalOnPropertyNotEmpty(
    val prefix: String = "",
    val name: String
)

public class OnPropertyNotEmptyCondition : SpringBootCondition() {

    override fun getMatchOutcome(
        context: ConditionContext,
        metadata: AnnotatedTypeMetadata
    ): ConditionOutcome {
        val attributes = metadata.getAllAnnotationAttributes(
            ConditionalOnPropertyNotEmpty::class.jvmName,
        )

        val prefix = attributes?.get("prefix")?.firstOrNull() as? String ?: ""
        val name = attributes?.get("name")?.firstOrNull() as? String ?: ""

        val propertyKey = if (prefix.isNotEmpty()) {
            "$prefix.$name"
        } else {
            name
        }

        val value = context.environment.getProperty(propertyKey)

        return if (!value.isNullOrEmpty()) {
            ConditionOutcome.match("Property '$propertyKey' has non-empty value")
        } else {
            ConditionOutcome.noMatch("Property '$propertyKey' is missing or empty")
        }
    }
}

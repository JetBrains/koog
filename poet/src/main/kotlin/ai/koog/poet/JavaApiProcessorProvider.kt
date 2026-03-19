package ai.koog.poet

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import kotlinx.schema.ksp.JavaApiProcessor

/**
 * Provider for the JavaApiProcessor.
 *
 * This class is registered as a service in META-INF/services to be discovered by KSP.
 */
internal class JavaApiProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        JavaApiProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            options = environment.options,
        )
}

package kotlinx.schema.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate

/**
 * KSP processor that generates extension properties for classes and functions,
 * annotated with `@Schema`.
 *
 * For a class annotated with @Schema, this processor generates an extension property:
 * ```kotlin
 * val MyClass.jsonSchemaString: String get() = "..."
 * ```
 */
internal class JavaApiProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>,
) : SymbolProcessor {

    internal companion object {
        private const val JAVA_API_ANNOTATION = "ai.koog.agents.annotations.JavaAPI"

        const val OPTION_ENABLED = "ai.koog.poet.enabled"

        const val OPTION_ROOT_PACKAGE = "ai.koog.poet.rootPackage"
    }

    override fun finish() {
        logger.info("[koog-ksp] ✅ Done!")
    }

    override fun onError() {
        logger.error(
            "[koog-ksp] 💥 Error! KSP Processor Options: ${
                options.entries.joinToString(
                    prefix = "[",
                    separator = ", ",
                    postfix = "]",
                ) { it.toString() }
            }",
        )
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val enabled = options[OPTION_ENABLED]?.trim()?.takeIf { it.isNotEmpty() } != "false"

        logger.info("[koog-ksp] Options: ${options.entries.joinToString()}")

        if (!enabled) {
            logger.info("[koog-ksp] Plugin is disabled")
            return emptyList()
        }

        val unprocessable = mutableListOf<KSAnnotated>()

        val symbols =
            resolver
                .getSymbolsWithAnnotation(JAVA_API_ANNOTATION)

        processClassDeclarations(symbols.filterIsInstance<KSClassDeclaration>(), unprocessable)

        return unprocessable
    }

    private fun processClassDeclarations(
        classDeclarations: Sequence<KSClassDeclaration>,
        unprocessable: MutableList<KSAnnotated>,
    ) {
        classDeclarations.forEach { classDeclaration ->
            if (!classDeclaration.validate()) {
                unprocessable.add(classDeclaration)
                return@forEach
            }

            @Suppress("TooGenericExceptionCaught")
            try {
                val qualifiedName = classDeclaration.qualifiedName?.asString()
                logger.info("🧶Processing $qualifiedName...")

                // Create context for strategy
                val className = classDeclaration.simpleName.asString() + "Java"
                val packageName = classDeclaration.packageName.asString()

                codeGenerator.createNewFile(
                    Dependencies(aggregating = false, classDeclaration.containingFile!!),
                    packageName = packageName,
                    fileName = className,
                    extensionName = "kt"
                ).bufferedWriter().use { writer ->
                    writer.write(
                        // language=kotlin
                        """
                        |package $packageName
                        |
                        |public class $className {
                        |
                        |    public fun hello(): String = "Hello from $packageName.$className" 
                        |}
                        """.trimMargin()
                    )
                }
                //                generateSchemaExtension(classDeclaration)
            } catch (e: Exception) {
                logger.error(
                    "Failed to generate schema extension " +
                        "for ${classDeclaration.qualifiedName?.asString()}: ${e.message}",
                )
            }
        }
    }
}

import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import kotlin.text.contains

@Language("HTML")
val htmlDescription = """
    <html>
    <body>
        Checks if public API declaration has a missing KDoc
    </body>
    </html>
""".trimIndent()

val anyLanguageInspectionTemplate = localInspection { psiFile, inspection ->
    fun PsiElement.isPublic() = this.children
        .filterIsInstance<KtModifierList>()
        .firstOrNull()
        ?.text
        ?.contains("public")
        ?: false

    fun PsiElement.isOverride() = this.children
        .filterIsInstance<KtModifierList>()
        .firstOrNull()
        ?.text
        ?.contains("override")
        ?: false

    fun PsiElement.hasKdoc() = this.children
        .filterIsInstance<KDoc>()
        .isNotEmpty()

    val declarations =
        psiFile.descendantsOfType<KtClass>() + psiFile.descendantsOfType<KtFunction>() + psiFile.descendantsOfType<KtProperty>() + psiFile.descendantsOfType<KtObjectDeclaration>()

    declarations
        .filter { it.isPublic() }
        .filter { !it.isOverride() }
        .forEach { declaration ->
            if (!declaration.hasKdoc()) {
                inspection.registerProblem(
                    declaration,
                    "Missing KDoc for the public API declaration ${declaration.kotlinFqName}"
                )
            }
        }
}

listOf(
    InspectionKts(
        id = "MissingKDocForPublicAPI",
        localTool = anyLanguageInspectionTemplate,
        name = "Missing KDoc for public API declaration",
        htmlDescription = htmlDescription,
        level = HighlightDisplayLevel.WARNING,
    )
)
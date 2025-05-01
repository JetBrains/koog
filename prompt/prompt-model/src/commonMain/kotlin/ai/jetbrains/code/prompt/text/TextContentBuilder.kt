package ai.jetbrains.code.prompt.text

open class TextContentBuilder {
    data class Caret(val line: Int, val offset: Int)

    private val builder = StringBuilder()

    val caret: Caret
        get() = Caret(builder.lines().size, builder.lines().lastOrNull()?.length ?: 0)

    operator fun String.not() {
        text(this)
    }

    open operator fun String.unaryPlus() {
        textWithNewLine(this)
    }

    fun text(text: String) {
        builder.append(text)
    }

    fun textWithNewLine(text: String) {
        if (caret.offset > 0) newline()
        text(text)
    }

    fun padding(padding: String, body: TextContentBuilder.() -> Unit) {
        val content = TextContentBuilder().apply(body).build()
        for (line in content.lines()) {
            +"$padding$line"
        }
    }


    fun newline() {
        builder.append("\n")
    }

    fun br() {
        newline()
        newline()
    }

    fun build(): String = builder.toString()
}
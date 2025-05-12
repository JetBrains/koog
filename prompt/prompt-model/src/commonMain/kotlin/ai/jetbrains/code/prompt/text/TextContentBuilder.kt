package ai.jetbrains.code.prompt.text

public open class TextContentBuilder {
    public data class Caret(val line: Int, val offset: Int)

    private val builder = StringBuilder()

    public val caret: Caret
        get() = Caret(builder.lines().size, builder.lines().lastOrNull()?.length ?: 0)

    public operator fun String.not() {
        text(this)
    }

    public open operator fun String.unaryPlus() {
        textWithNewLine(this)
    }

    public fun text(text: String) {
        builder.append(text)
    }

    public fun textWithNewLine(text: String) {
        if (caret.offset > 0) newline()
        text(text)
    }

    public fun padding(padding: String, body: TextContentBuilder.() -> Unit) {
        val content = TextContentBuilder().apply(body).build()
        for (line in content.lines()) {
            +"$padding$line"
        }
    }

    public fun newline() {
        builder.append("\n")
    }

    public fun br() {
        newline()
        newline()
    }

    public fun build(): String = builder.toString()
}

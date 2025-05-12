package ai.jetbrains.code.prompt.text

public fun text(block: TextContentBuilder.() -> Unit): String = TextContentBuilder().apply(block).build()


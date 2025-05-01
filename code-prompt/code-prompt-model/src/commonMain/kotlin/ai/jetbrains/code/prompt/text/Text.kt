package ai.jetbrains.code.prompt.text

fun text(block: TextContentBuilder.() -> Unit): String = TextContentBuilder().apply(block).build()


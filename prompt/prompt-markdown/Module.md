# Module prompt-markdown

A utility module for creating and manipulating Markdown content with a fluent builder API.

### Overview

The prompt-markdown module provides a dedicated builder for creating Markdown content programmatically. It wraps TextContentBuilder and offers Markdown-specific functionality for generating well-formatted Markdown documents. The module supports various Markdown features including headers, text formatting (bold, italic, strikethrough), code blocks, links, images, tables, and both bulleted and numbered lists.

### Example of usage

```kotlin
val document = markdown {
    h1("Hello, Markdown!")
    +"This is a paragraph of text."

    h2("Features")
    bulleted {
        item("Easy to use")
        item("Type-safe")
        item("Extensible")
    }

    codeblock("""
        fun example() {
            println("Hello, World!")
        }
    """, "kotlin")

    line {
        text("This is ")
        bold("bold")
        text(" and ")
        italic("italic")
        text(" text with a ")
        link("link", "https://example.com")
    }

    table(
        headers = listOf("Name", "Age", "Role"),
        rows = listOf(
            listOf("John", "30", "Developer"),
            listOf("Jane", "28", "Designer")
        ),
        alignments = listOf(TableAlignment.LEFT, TableAlignment.CENTER, TableAlignment.RIGHT)
    )
}
```

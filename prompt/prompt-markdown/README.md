# Code Prompt Markdown

A Kotlin DSL for generating Markdown content using a dedicated MarkdownContentBuilder.

## Overview

This module provides a specialized builder for creating Markdown content. It wraps TextContentBuilder and offers a rich set of markdown-specific functions to create well-formatted documents programmatically.

## Features

- Headers (h1-h6)
- Text formatting (bold, italic, strikethrough)
- Code blocks and inline code
- Links and images
- Lists (ordered, unordered, task lists)
- Structured lists with nesting support
- Tables with column alignment
- Blockquotes
- Horizontal rules

## Usage

### Basic Example

```kotlin
import ai.koog.prompt.markdown.*

val markdown: String = markdown {
    h1("Hello, Markdown!")
    +"This is a paragraph of text."
    
    h2("Features")
    unorderedList(
        "Easy to use",
        "Type-safe",
        "Extensible"
    )
    
    link("Koog", "https://github.com/JetBrains/koog")
}
```

### Structured Lists Example

```kotlin
val document = markdown {
    h1("Project Tasks")
    
    // Bulleted list with nested items
    bulleted {
        item("Frontend")
        item("Backend") {
            // Nested checked list
            checked {
                item("Set up database")
                checked("Create API endpoints")
                item("Implement authentication") {
                    // Deeply nested numbered list
                    numbered {
                        item("Research OAuth providers")
                        item("Implement login flow")
                        item("Add session management")
                    }
                }
            }
        }
        item("DevOps")
    }
    
    // Numbered list
    numbered {
        item("Planning")
        item("Development")
        item("Testing")
        item("Deployment")
    }
}
```

### Advanced Example

```kotlin
val document = markdown {
    h1("Project Documentation")
    +"Welcome to the project documentation."
    
    h2("Installation")
    +"To install the project, run the following command:"
    codeBlock("npm install my-project", "bash")
    
    h2("Usage")
    +"Here's how to use the project:"
    codeBlock("""
        import { MyProject } from 'my-project';
        
        const project = new MyProject();
        project.start();
    """.trimIndent(), "javascript")
    
    h2("Features")
    bulleted {
        item("Easy to use")
        item("Lightweight")
        item("Extensible")
    }
    
    h2("Roadmap")
    checked {
        checked("Basic functionality")
        item("Advanced features")
        checked("Documentation")
        item("Tests")
    }
    
    h2("Contributors")
    table(
        headers = listOf("Name", "Role", "Contributions"),
        rows = listOf(
            listOf("John Doe", "Developer", "100"),
            listOf("Jane Smith", "Designer", "42"),
            listOf("Bob Johnson", "Tester", "28")
        ),
        alignments = listOf(TableAlignment.LEFT, TableAlignment.CENTER, TableAlignment.RIGHT)
    )
    
    h2("License")
    blockquote("This project is licensed under the MIT License.")
}
```

## API Reference

### Main Functions

- `markdown(init: MarkdownContentBuilder.() -> Unit): String` - Creates a markdown document with the given content

### MarkdownContentBuilder Methods

#### Headers

- `h1(text: String)` - Level 1 header
- `h2(text: String)` - Level 2 header
- `h3(text: String)` - Level 3 header
- `h4(text: String)` - Level 4 header
- `h5(text: String)` - Level 5 header
- `h6(text: String)` - Level 6 header
- `header(level: Int, text: String)` - Custom level header (1-6)

#### Text Formatting

- `bold(text: String)` - Bold text
- `italic(text: String)` - Italic text
- `strikethrough(text: String)` - Strikethrough text
- `code(code: String)` - Inline code

#### Blocks

- `codeBlock(code: String, language: String = "")` - Code block with optional language
- `blockquote(text: String)` - Blockquote
- `horizontalRule()` - Horizontal rule

#### Links and Images

- `link(text: String, url: String)` - Link
- `image(alt: String, url: String)` - Image

#### Simple Lists

- `unorderedList(vararg items: String)` - Unordered list
- `orderedList(vararg items: String)` - Ordered list
- `taskList(vararg items: Pair<String, Boolean>)` - Task list

#### Structured Lists

- `bulleted(block: BulletedListContext.() -> Unit)` - Bulleted list with structured content
- `numbered(block: NumberedListContext.() -> Unit)` - Numbered list with structured content
- `checked(block: CheckedListContext.() -> Unit)` - Checked list with structured content

##### List Context Methods

- `item(text: String)` - Add a list item
- `item(text: String, block: ListItemContext.() -> Unit)` - Add a list item with nested content
- `checked(text: String)` - Add a checked item (only in CheckedListContext)
- `checked(text: String, block: ListItemContext.() -> Unit)` - Add a checked item with nested content

##### Nested List Methods

Within a list item block, you can add nested lists:
- `bulleted(block: BulletedListContext.() -> Unit)` - Add a nested bulleted list
- `numbered(block: NumberedListContext.() -> Unit)` - Add a nested numbered list
- `checked(block: CheckedListContext.() -> Unit)` - Add a nested checked list

#### Tables

- `table(headers: List<String>, rows: List<List<String>>, alignments: List<TableAlignment> = ...)` - Table with optional column alignments

#### Other

- `String.unaryPlus()` - Adds a text line to the content (used as `+"text"`)
- `newline()` - Adds a newline
- `br()` - Adds a blank line
- `build()` - Builds the markdown content as a string

## Integration with TextContentBuilder

This module uses `TextContentBuilder` from the `prompt-model` module under the hood, but provides a more specialized interface for markdown generation.
# Module prompt-xml

A utility module for creating and manipulating XML content with a fluent builder API.

### Overview

The prompt-xml module provides a dedicated builder for creating XML content programmatically. It wraps TextContentBuilder and offers XML-specific functionality for generating well-formatted XML documents. The module supports various XML features including tags, attributes, CDATA sections, comments, processing instructions, and DOCTYPE declarations.

### Example of usage

```kotlin
val xmlDocument = xml {
    xmlDeclaration()
    tag("root", linkedMapOf("version" to "1.0")) {
        tag("person", linkedMapOf("id" to "1")) {
            tag("name") {
                +"John Doe"
            }
            tag("age") {
                +"30"
            }
            tag("skills") {
                tag("skill") {
                    +"Programming"
                }
                tag("skill") {
                    +"Design"
                }
            }
        }
        comment("This is a comment")
        cdata("<some>raw content</some>")
    }
}
```

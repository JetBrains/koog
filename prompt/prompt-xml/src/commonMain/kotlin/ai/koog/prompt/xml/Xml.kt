package ai.koog.prompt.xml

import ai.koog.prompt.text.TextContentBuilder
import ai.koog.prompt.text.TextContentBuilderBase

/**
 * A dedicated builder for creating XML content.
 * Wraps TextContentBuilder and provides XML-specific functionality.
 */
public class XmlContentBuilder(private val indented: Boolean) : TextContentBuilder() {
    private companion object {
        private val INDENTATION_ITEM = " ".repeat(2)
    }

    override fun String.unaryPlus() {
        if (!indented) {
            text(this)
            return
        }
        textWithNewLine(this)
    }

    /**
     * Creates an XML tag with the given name, attributes, and content.
     * @param name The tag name
     * @param attributes The tag attributes as key-value pairs
     * @param block The content builder
     */
    public fun tag(
        name: String,
        attributes: LinkedHashMap<String, String> = linkedMapOf(),
        block: XmlContentBuilder.() -> Unit = {}
    ) {
        val attributesString = if (attributes.isEmpty()) {
            ""
        } else {
            attributes.entries.joinToString(" ") { "${it.key}=\"${it.value}\"" }
        }

        val content = XmlContentBuilder(indented).apply(block).build()

        if (content.isEmpty()) {
            selfClosingTag(name, attributes)
            return
        }

        // Opening tag
        if (attributesString.isEmpty()) {
            +("<$name>")
        } else {
            +("<$name $attributesString>")
        }

        // Create a new builder for the content

        // If there's content, add it with proper indentation
        if (content.isNotEmpty()) {
            // Process each line of content
            val lines = content.lines()
            for ((index, line) in lines.withIndex()) {
                if (index == lines.lastIndex && line.isBlank()) continue
                if (indented) {
                    +(INDENTATION_ITEM + line)
                } else {
                    +(line)
                }
            }
        }

        // Closing tag
        +("</$name>")
    }

    /**
     * Creates a self-closing XML tag with the given name and attributes.
     * @param name The tag name
     * @param attributes The tag attributes as key-value pairs
     */
    public fun selfClosingTag(name: String, attributes: LinkedHashMap<String, String> = linkedMapOf()) {
        val attributesString =
            if (attributes.isEmpty()) "" else attributes.entries.joinToString(" ") { "${it.key}=\"${it.value}\"" }

        if (attributesString.isEmpty()) {
            +("<$name/>")
        } else {
            +("<$name $attributesString/>")
        }
    }

    /**
     * Adds an XML declaration to the document.
     * @param version The XML version (default is "1.0")
     * @param encoding The character encoding (default is "UTF-8")
     * @param standalone Whether the document is standalone (default is null, which means the attribute is not included)
     */
    public fun xmlDeclaration(version: String = "1.0", encoding: String = "UTF-8", standalone: Boolean? = null) {
        val standaloneAttr = standalone?.let { " standalone=\"${if (it) "yes" else "no"}\"" } ?: ""
        +("<?xml version=\"$version\" encoding=\"$encoding\"$standaloneAttr?>")
        newline()
    }

    /**
     * Adds a CDATA section to the content.
     * @param content The content to be wrapped in CDATA
     */
    public fun cdata(content: String) {
        +("<![CDATA[$content]]>")
    }

    /**
     * Adds an XML comment to the content.
     * @param comment The comment text
     */
    public fun comment(comment: String) {
        +("<!-- $comment -->")
    }

    /**
     * Adds a processing instruction to the content.
     * @param target The processing instruction target
     * @param data The processing instruction data
     */
    public fun processingInstruction(target: String, data: String) {
        +("<?$target $data?>")
    }

    /**
     * Adds a DOCTYPE declaration to the content.
     * @param rootElement The root element name
     * @param publicId The public identifier (optional)
     * @param systemId The system identifier (optional)
     */
    public fun doctype(rootElement: String, publicId: String? = null, systemId: String? = null) {
        if (publicId != null && systemId != null) {
            +("<!DOCTYPE $rootElement PUBLIC \"$publicId\" \"$systemId\">")
        } else if (systemId != null) {
            +("<!DOCTYPE $rootElement SYSTEM \"$systemId\">")
        } else {
            +("<!DOCTYPE $rootElement>")
        }
    }
}

/**
 * Extension function to add XML content to a StringBuilder.
 * @param init The XML content builder
 */
public inline fun StringBuilder.xml(indented: Boolean = true, init: XmlContentBuilder.() -> Unit) {
    append(XmlContentBuilder(indented).apply(init).build())
}

/**
 * Extension function to add XML content to a TextContentBuilder.
 * @param init The XML content builder
 */
public inline fun TextContentBuilderBase<*>.xml(indented: Boolean = true, init: XmlContentBuilder.() -> Unit) {
    text(XmlContentBuilder(indented).apply(init).build())
}

/**
 * Creates an XML document with the given content.
 * @param init The content builder
 * @return The XML document as a string
 */
public fun xml(indented: Boolean = true, init: XmlContentBuilder.() -> Unit): String {
    return XmlContentBuilder(indented).apply(init).build()
}

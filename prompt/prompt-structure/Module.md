# Module prompt-structure

A module for defining, parsing, and formatting structured data in various formats.

### Overview

The prompt-structure module provides a framework for handling structured data with specific schemas. It includes abstract classes and interfaces for defining structured data entities, parsing text into structured formats, and formatting structured data into human-readable representations. The module supports different structure languages including JSON and Markdown, allowing for flexible data representation and manipulation.

### Example of usage

```kotlin
// Define a structured data type for a person
class PersonStructure(
    id: String,
    examples: List<Person>,
    schema: LLMParams.Schema
) : Structure<Person>(id, examples, schema) {
    override fun parse(text: String): Person {
        // Parse JSON text into a Person object
        val json = Json.parseToJsonElement(text).jsonObject
        return Person(
            name = json["name"]?.jsonPrimitive?.content ?: "",
            age = json["age"]?.jsonPrimitive?.int ?: 0,
            skills = json["skills"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        )
    }

    override fun pretty(value: Person): String {
        // Format Person object as a pretty JSON string
        return Json.encodeToString(
            buildJsonObject {
                put("name", value.name)
                put("age", value.age)
                putJsonArray("skills") {
                    value.skills.forEach { add(it) }
                }
            }
        )
    }
}

// Create an instance with examples
val personStructure = PersonStructure(
    id = "person",
    examples = listOf(
        Person("John Doe", 30, listOf("Programming", "Design")),
        Person("Jane Smith", 28, listOf("Management", "Communication"))
    ),
    schema = LLMParams.Schema.JSON.Simple(
        name = "Person",
        schema = JsonObject(mapOf(
            "type" to JsonPrimitive("object"),
            "properties" to JsonObject(mapOf(
                "name" to JsonObject(mapOf("type" to JsonPrimitive("string"))),
                "age" to JsonObject(mapOf("type" to JsonPrimitive("number"))),
                "skills" to JsonObject(mapOf(
                    "type" to JsonPrimitive("array"),
                    "items" to JsonObject(mapOf("type" to JsonPrimitive("string")))
                ))
            ))
        ))
    )
)

// Parse text into a Person object
val personText = """{"name":"Alice","age":25,"skills":["Writing","Research"]}"""
val person = personStructure.parse(personText)

// Format a Person object as a pretty string
val prettyOutput = personStructure.pretty(person)
```

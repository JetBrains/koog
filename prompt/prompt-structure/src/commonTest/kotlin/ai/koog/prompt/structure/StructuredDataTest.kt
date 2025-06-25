package ai.koog.prompt.structure

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.structure.json.JsonStructuredData
import ai.koog.prompt.text.TextContentBuilder
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class JsonStructuredDataTest {
    // Simple data structure for basic tests
    @Serializable
    @LLMDescription("SimpleData description")
    data class SimpleData(
        @property:LLMDescription("SimpleData.value description")
        val value: String
    )

    // Array data structure
    @Serializable
    data class ArrayData(
        val primitiveArray: List<String>,
        val numberArray: List<Int>
    )

    // Nested object structures
    @Serializable
    data class NestedObject(
        val name: String,
        val details: Details
    )

    @Serializable
    data class Details(
        val age: Int,
        val contact: Contact
    )

    @Serializable
    data class Contact(
        val email: String,
        val phone: String?
    )

    // Sealed interface for polymorphic serialization
    @Serializable
    sealed interface Animal {
        val name: String
        val age: Int
    }

    @Serializable
    @SerialName("Dog")
    data class Dog(
        override val name: String,
        override val age: Int,
        val breed: String
    ) : Animal

    @Serializable
    @SerialName("Cat")
    data class Cat(
        override val name: String,
        override val age: Int,
        val color: String
    ) : Animal

    // Data class with array of objects
    @Serializable
    data class Zoo(
        val location: String,
        val animals: List<Animal>
    )

    // Data class with polymorphic array and regular fields
    @Serializable
    data class PetStore(
        val name: String,
        val address: String,
        val inventory: List<Animal>,
        val rating: Int
    )

    // Complex data structure with arrays and nested objects
    @Serializable
    data class ComplexData(
        val id: String,
        val items: List<NestedObject>,
        val tags: List<String>
    )

    // Simple data tests
    private val structuredSimple = JsonStructuredData.createJsonStructure<SimpleData>(
        id = "simple",
        examples = listOf(SimpleData("example1"), SimpleData("example2"))
    )

    private val structuredSimpleOneExample = JsonStructuredData.createJsonStructure<SimpleData>(
        id = "simple",
        examples = listOf(SimpleData("example1"))
    )

    private val structuredSimpleNoExample = JsonStructuredData.createJsonStructure<SimpleData>(
        id = "simple",
        examples = listOf()
    )

    @Test
    fun testSimpleParseValid() {
        val json = """{"value":"test"}"""
        val data = structuredSimple.parse(json)
        assertEquals("test", data.value)
    }

    @Test
    fun testSimplePrettyFormatting() {
        val data = SimpleData("pretty")
        val prettyJson = structuredSimple.pretty(data)
        val parsed = structuredSimple.parse(prettyJson)
        assertEquals("pretty", parsed.value)
    }

    @Test
    fun testSimpleParseError() {
        val invalidJson = """{"not_value":"oops"}"""
        assertFailsWith<Exception> {
            structuredSimple.parse(invalidJson)
        }
    }

    @Test
    fun testSimpleDefinitionContent() {
        val builder = TextContentBuilder()
        structuredSimple.definition(builder)
        val content = builder.build()

        assertTrue(content.contains("DEFINITION OF simple"))
        assertTrue(content.contains("SimpleData description"))
        assertTrue(content.contains("SimpleData.value description"))
        assertTrue(content.contains("is defined only and solely with JSON, without any additional characters, backticks or anything similar."))
    }

    @Test
    fun testSimpleDefinitionWithExamples() {
        val builder = TextContentBuilder()
        structuredSimple.definition(builder)
        val content = builder.build()

        assertTrue(content.contains("""
            Here are some examples of valid responses:
            {
              "value": "example1"
            }
            {
              "value": "example2"
            }
        """.trimIndent()))
    }

    @Test
    fun testSimpleDefinitionWithOneExample() {
        val builder = TextContentBuilder()
        structuredSimpleOneExample.definition(builder)
        val content = builder.build()

        assertTrue(content.contains("""
            Here is an example of a valid response:
            {
              "value": "example1"
            }
        """.trimIndent()))
    }

    @Test
    fun testSimpleDefinitionWithNoExamples() {
        val builder = TextContentBuilder()
        structuredSimpleNoExample.definition(builder)
        val content = builder.build()

        assertFalse(content.contains("Here are some examples of valid responses"))
        assertFalse(content.contains("Here is an example of a valid response"))
    }

    // Array data tests
    @Test
    fun testArrayData() {
        val arrayData = JsonStructuredData.createJsonStructure(
            id = "array_data",
            serializer = ArrayData.serializer(),
            examples = listOf(
                ArrayData(
                    primitiveArray = listOf("one", "two"),
                    numberArray = listOf(1, 2)
                )
            )
        )

        val json = """{"primitiveArray":["test1","test2"],"numberArray":[10,20]}"""
        val parsed = arrayData.parse(json)

        assertEquals(listOf("test1", "test2"), parsed.primitiveArray)
        assertEquals(listOf(10, 20), parsed.numberArray)

        val pretty = arrayData.pretty(parsed)
        val reparsed = arrayData.parse(pretty)
        assertEquals(parsed, reparsed)
    }

    @Test
    fun testEmptyArrays() {
        val arrayData = JsonStructuredData.createJsonStructure<ArrayData>(
            examples = listOf(ArrayData(primitiveArray = emptyList(), numberArray = emptyList()))
        )

        val json = """{"primitiveArray":[],"numberArray":[]}"""
        val parsed = arrayData.parse(json)

        assertTrue(parsed.primitiveArray.isEmpty())
        assertTrue(parsed.numberArray.isEmpty())
    }

    // Nested objects tests
    @Test
    fun testNestedObjects() {
        val nestedData = JsonStructuredData.createJsonStructure(
            id = "nested_data",
            serializer = NestedObject.serializer(),
            examples = listOf(
                NestedObject(
                    name = "John",
                    details = Details(
                        age = 30,
                        contact = Contact(
                            email = "john@example.com",
                            phone = null
                        )
                    )
                )
            )
        )

        val json = """
            {
                "name": "Alice",
                "details": {
                    "age": 25,
                    "contact": {
                        "email": "alice@example.com",
                        "phone": "+1234567890"
                    }
                }
            }
        """.trimIndent()

        val parsed = nestedData.parse(json)
        assertEquals("Alice", parsed.name)
        assertEquals(25, parsed.details.age)
        assertEquals("alice@example.com", parsed.details.contact.email)
        assertEquals("+1234567890", parsed.details.contact.phone)

        val pretty = nestedData.pretty(parsed)
        val reparsed = nestedData.parse(pretty)
        assertEquals(parsed, reparsed)
    }

    // Complex data tests with arrays of nested objects
    @Test
    fun testComplexData() {
        val complexData = JsonStructuredData.createJsonStructure<ComplexData>(
            examples = listOf(
                ComplexData(
                    id = "test",
                    items = listOf(
                        NestedObject(
                            name = "Item1",
                            details = Details(
                                age = 1,
                                contact = Contact(
                                    email = "item1@test.com",
                                    phone = null
                                )
                            )
                        )
                    ),
                    tags = listOf("tag1", "tag2")
                )
            )
        )

        val json = """
            {
                "id": "complex1",
                "items": [
                    {
                        "name": "First",
                        "details": {
                            "age": 10,
                            "contact": {
                                "email": "first@test.com",
                                "phone": "123"
                            }
                        }
                    },
                    {
                        "name": "Second",
                        "details": {
                            "age": 20,
                            "contact": {
                                "email": "second@test.com",
                                "phone": null
                            }
                        }
                    }
                ],
                "tags": ["important", "urgent"]
            }
        """.trimIndent()

        val parsed = complexData.parse(json)
        assertEquals("complex1", parsed.id)
        assertEquals(2, parsed.items.size)
        assertEquals("First", parsed.items[0].name)
        assertEquals("Second", parsed.items[1].name)
        assertEquals(listOf("important", "urgent"), parsed.tags)

        val pretty = complexData.pretty(parsed)
        val reparsed = complexData.parse(pretty)
        assertEquals(parsed, reparsed)
    }

    @Test
    fun testSealedInterfaceDefinition() {
        val animalData = JsonStructuredData.createJsonStructure<Animal>(
            examples = listOf(
                Dog("Max", 1, "Poodle"),
                Cat("Kitty", 2, "White")
            )
        )

        val builder = TextContentBuilder()
        animalData.definition(builder)
        val content = builder.build()

        assertTrue(content.contains("DEFINITION OF Animal"))
        assertTrue(content.contains("kind"))  // Type discriminator
        assertTrue(content.contains("Dog"))   // Subclass name
        assertTrue(content.contains("Cat"))   // Subclass name
        assertTrue(content.contains("name"))  // Common property
        assertTrue(content.contains("age"))   // Common property
        assertTrue(content.contains("breed")) // Dog-specific property
        assertTrue(content.contains("color")) // Cat-specific property
    }

    @Test
    fun testPolymorphicArrayInClass() {
        val petStore = JsonStructuredData.createJsonStructure(
            id = "pet_store",
            serializer = PetStore.serializer(),
            examples = listOf(
                PetStore(
                    name = "Happy Pets",
                    address = "123 Pet Street",
                    inventory = listOf(
                        Dog("Buddy", 1, "Golden Retriever"),
                        Cat("Whiskers", 2, "Tabby")
                    ),
                    rating = 5
                )
            )
        )

        // Test serialization and deserialization
        val json = """
            {
                "name": "City Pets",
                "address": "456 Animal Avenue",
                "inventory": [
                    {
                        "kind": "Dog",
                        "name": "Max",
                        "age": 3,
                        "breed": "Husky"
                    },
                    {
                        "kind": "Cat",
                        "name": "Luna",
                        "age": 2,
                        "color": "Black"
                    }
                ],
                "rating": 4
            }
        """.trimIndent()

        val parsed = petStore.parse(json)
        assertEquals("City Pets", parsed.name)
        assertEquals("456 Animal Avenue", parsed.address)
        assertEquals(4, parsed.rating)
        assertEquals(2, parsed.inventory.size)

        val dog = parsed.inventory[0] as Dog
        assertEquals("Max", dog.name)
        assertEquals(3, dog.age)
        assertEquals("Husky", dog.breed)

        val cat = parsed.inventory[1] as Cat
        assertEquals("Luna", cat.name)
        assertEquals(2, cat.age)
        assertEquals("Black", cat.color)


        // Check example format
        val pretty = petStore.pretty(parsed)
        val reparsed = petStore.parse(pretty)
        assertEquals(parsed, reparsed)
    }

    @Test
    fun testComplexDataWithEmptyCollections() {
        val complexData = JsonStructuredData.createJsonStructure(
            id = "complex_data",
            serializer = ComplexData.serializer(),
            examples = listOf(ComplexData(id = "empty", items = emptyList(), tags = emptyList()))
        )

        val json = """{"id":"empty","items":[],"tags":[]}"""
        val parsed = complexData.parse(json)

        assertEquals("empty", parsed.id)
        assertTrue(parsed.items.isEmpty())
        assertTrue(parsed.tags.isEmpty())
    }

    @Test
    fun testObjectsInArrays() {
        val arrayData = JsonStructuredData.createJsonStructure(
            id = "array_objects",
            serializer = Zoo.serializer(),
            examples = listOf(
                Zoo(
                    location = "Test Zoo",
                    animals = listOf(
                        Dog("Buddy", 3, "Labrador"),
                        Cat("Whiskers", 5, "Gray")
                    )
                )
            )
        )

        val json = """
            {
                "location": "City Zoo",
                "animals": [
                    {
                        "kind": "Dog",
                        "name": "Rex",
                        "age": 2,
                        "breed": "German Shepherd"
                    },
                    {
                        "kind": "Cat",
                        "name": "Luna",
                        "age": 4,
                        "color": "Black"
                    }
                ]
            }
        """.trimIndent()

        val parsed = arrayData.parse(json)
        assertEquals("City Zoo", parsed.location)
        assertEquals(2, parsed.animals.size)

        val dog = parsed.animals[0] as Dog
        assertEquals("Rex", dog.name)
        assertEquals(2, dog.age)
        assertEquals("German Shepherd", dog.breed)

        val cat = parsed.animals[1] as Cat
        assertEquals("Luna", cat.name)
        assertEquals(4, cat.age)
        assertEquals("Black", cat.color)

        val pretty = arrayData.pretty(parsed)
        val reparsed = arrayData.parse(pretty)
        assertEquals(parsed, reparsed)
    }

    @Test
    fun testSealedInterfacePolymorphism() {
        val animalData = JsonStructuredData.createJsonStructure(
            id = "animal",
            serializer = Animal.serializer(),
            examples = listOf(
                Dog("Max", 1, "Poodle"),
                Cat("Kitty", 2, "White")
            )
        )

        // Test Dog serialization
        val dogJson = """
            {
                "kind": "Dog",
                "name": "Spot",
                "age": 3,
                "breed": "Dalmatian"
            }
        """.trimIndent()

        val parsedDog = animalData.parse(dogJson) as Dog
        assertEquals("Spot", parsedDog.name)
        assertEquals(3, parsedDog.age)
        assertEquals("Dalmatian", parsedDog.breed)

        // Test Cat serialization
        val catJson = """
            {
                "kind": "Cat",
                "name": "Tiger",
                "age": 5,
                "color": "Orange"
            }
        """.trimIndent()

        val parsedCat = animalData.parse(catJson) as Cat
        assertEquals("Tiger", parsedCat.name)
        assertEquals(5, parsedCat.age)
        assertEquals("Orange", parsedCat.color)

        // Test pretty formatting and reparsing for both types
        val prettyDog = animalData.pretty(parsedDog)
        val reparsedDog = animalData.parse(prettyDog)
        assertEquals(parsedDog, reparsedDog)

        val prettyCat = animalData.pretty(parsedCat)
        val reparsedCat = animalData.parse(prettyCat)
        assertEquals(parsedCat, reparsedCat)
    }
}

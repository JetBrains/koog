package ai.koog.prompt.structure.json

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonSchemaGeneratorTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
        classDiscriminator = "kind"
        prettyPrint = true
        prettyPrintIndent = "  "

        serializersModule = SerializersModule {
            polymorphic(TestOpenPolymorphism::class) {
                subclass(TestOpenPolymorphism.SubClass1::class, TestOpenPolymorphism.SubClass1.serializer())
                subclass(TestOpenPolymorphism.SubClass2::class, TestOpenPolymorphism.SubClass2.serializer())
            }
        }
    }

    private val jsonSchemaGenerator = JsonSchemaGenerator(json, JsonSchemaGenerator.SchemaFormat.JsonSchema, 10)
    private val simpleSchemaGenerator = JsonSchemaGenerator(json, JsonSchemaGenerator.SchemaFormat.Simple, 10)


    @Serializable
    @SerialName("TestClass")
    @LLMDescription("A test class")
    data class TestClass(
        @property:LLMDescription("A string property")
        val stringProperty: String,
        val intProperty: Int,
        val booleanProperty: Boolean,
        val nullableProperty: String? = null,
        val listProperty: List<String> = emptyList(),
        val mapProperty: Map<String, Int> = emptyMap()
    )

    @Serializable
    @SerialName("NestedTestClass")
    @LLMDescription("Nested test class")
    data class NestedTestClass(
        @LLMDescription("The name")
        val name: String,
        val nested: NestedProperty,
        val nestedList: List<NestedProperty> = emptyList(),
        val nestedMap: Map<String, NestedProperty> = emptyMap()
    ) {
        @Serializable
        @SerialName("NestedProperty")
        @LLMDescription("Nested property class")
        data class NestedProperty(
            @property:LLMDescription("Nested foo property")
            val foo: String,
            val bar: Int
        )
    }

    @Serializable
    @SerialName("TestClosedPolymorphism")
    sealed class TestClosedPolymorphism {
        abstract val id: String

        @Suppress("unused")
        @Serializable
        @SerialName("ClosedSubclass1")
        data class SubClass1(
            override val id: String,
            val property1: String
        ) : TestClosedPolymorphism()

        @Suppress("unused")
        @Serializable
        @SerialName("ClosedSubclass2")
        data class SubClass2(
            override val id: String,
            val property2: Int,
            val recursiveTypeProperty: TestClosedPolymorphism,
        ) : TestClosedPolymorphism()
    }

    @Serializable
    @SerialName("TestOpenPolymorphism")
    abstract class TestOpenPolymorphism {
        abstract val id: String

        @Suppress("unused")
        @Serializable
        @SerialName("OpenSubclass1")
        data class SubClass1(
            override val id: String,
            val property1: String
        ) : TestOpenPolymorphism()

        @Suppress("unused")
        @Serializable
        @SerialName("OpenSubclass2")
        data class SubClass2(
            override val id: String,
            val property2: Int,
            val recursiveTypeProperty: TestOpenPolymorphism,
        ) : TestOpenPolymorphism()
    }


    @Test
    fun testGenerateJsonSchema() {
        val schema = json.encodeToString(jsonSchemaGenerator.generate("TestClass", serializer<TestClass>()))

        val expectedSchema = """
            {
              "${"$"}schema": "http://json-schema.org/draft-07/schema#",
              "${"$"}id": "TestClass",
              "${"$"}defs": {
                "TestClass": {
                  "type": "object",
                  "description": "A test class",
                  "properties": {
                    "stringProperty": {
                      "type": "string",
                      "description": "A string property"
                    },
                    "intProperty": {
                      "type": "integer"
                    },
                    "booleanProperty": {
                      "type": "boolean"
                    },
                    "nullableProperty": {
                      "type": "string",
                      "nullable": true
                    },
                    "listProperty": {
                      "type": "array",
                      "items": {
                        "type": "string"
                      }
                    },
                    "mapProperty": {
                      "type": "object",
                      "additionalProperties": {
                        "type": "integer"
                      }
                    }
                  },
                  "required": [
                    "stringProperty",
                    "intProperty",
                    "booleanProperty"
                  ]
                }
              },
              "${"$"}ref": "#/defs/TestClass",
              "type": "object"
            }
        """.trimIndent()

        assertEquals(expectedSchema, schema)
    }

    @Test
    fun testGenerateSimpleSchema() {
        val schema = json.encodeToString(simpleSchemaGenerator.generate("TestClass", serializer<TestClass>()))

        val expectedSchema = """
            {
              "type": "object",
              "description": "A test class",
              "properties": {
                "stringProperty": {
                  "type": "string",
                  "description": "A string property"
                },
                "intProperty": {
                  "type": "integer"
                },
                "booleanProperty": {
                  "type": "boolean"
                },
                "nullableProperty": {
                  "type": "string",
                  "nullable": true
                },
                "listProperty": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  }
                },
                "mapProperty": {
                  "type": "object",
                  "additionalProperties": {
                    "type": "integer"
                  }
                }
              },
              "required": [
                "stringProperty",
                "intProperty",
                "booleanProperty"
              ]
            }
        """.trimIndent()

        assertEquals(expectedSchema, schema)
    }

    @Test
    fun testGenerateJsonSchemaWithDescriptions() {
        val descriptions = mapOf(
            "TestClass" to "A test class (override)",
            "TestClass.intProperty" to "An integer property"
        )

        val schema = json.encodeToString(jsonSchemaGenerator.generate("TestClass", serializer<TestClass>(), descriptions))

        val expectedSchema = """
            {
              "${"$"}schema": "http://json-schema.org/draft-07/schema#",
              "${"$"}id": "TestClass",
              "${"$"}defs": {
                "TestClass": {
                  "type": "object",
                  "description": "A test class (override)",
                  "properties": {
                    "stringProperty": {
                      "type": "string",
                      "description": "A string property"
                    },
                    "intProperty": {
                      "type": "integer",
                      "description": "An integer property"
                    },
                    "booleanProperty": {
                      "type": "boolean"
                    },
                    "nullableProperty": {
                      "type": "string",
                      "nullable": true
                    },
                    "listProperty": {
                      "type": "array",
                      "items": {
                        "type": "string"
                      }
                    },
                    "mapProperty": {
                      "type": "object",
                      "additionalProperties": {
                        "type": "integer"
                      }
                    }
                  },
                  "required": [
                    "stringProperty",
                    "intProperty",
                    "booleanProperty"
                  ]
                }
              },
              "${"$"}ref": "#/defs/TestClass",
              "type": "object"
            }
        """.trimIndent()

        assertEquals(expectedSchema, schema)
    }

    @Test
    fun testGenerateSimpleSchemaWithDescriptions() {
        val descriptions = mapOf(
            "TestClass" to "A test class (override)",
            "TestClass.intProperty" to "An integer property"
        )

        val schema = json.encodeToString(simpleSchemaGenerator.generate("TestClass", serializer<TestClass>(), descriptions))

        val expectedSchema = """
            {
              "type": "object",
              "description": "A test class (override)",
              "properties": {
                "stringProperty": {
                  "type": "string",
                  "description": "A string property"
                },
                "intProperty": {
                  "type": "integer",
                  "description": "An integer property"
                },
                "booleanProperty": {
                  "type": "boolean"
                },
                "nullableProperty": {
                  "type": "string",
                  "nullable": true
                },
                "listProperty": {
                  "type": "array",
                  "items": {
                    "type": "string"
                  }
                },
                "mapProperty": {
                  "type": "object",
                  "additionalProperties": {
                    "type": "integer"
                  }
                }
              },
              "required": [
                "stringProperty",
                "intProperty",
                "booleanProperty"
              ]
            }
        """.trimIndent()

        assertEquals(expectedSchema, schema)
    }

    @Test
    fun testJsonSchemaNestedDescriptions() {
        val descriptions = mapOf(
            "NestedTestClass.name" to "The name (override)",
            "NestedTestClass.nestedList" to "List of nested properties",
            "NestedTestClass.nestedMap" to "Map of nested properties",

            "NestedProperty.bar" to "Nested bar property",
        )

        val schema = json.encodeToString(jsonSchemaGenerator.generate("NestedTestClass", serializer<NestedTestClass>(), descriptions))

        val expectedDotSchema = """
            {
              "${"$"}schema": "http://json-schema.org/draft-07/schema#",
              "${"$"}id": "NestedTestClass",
              "${"$"}defs": {
                "NestedProperty": {
                  "type": "object",
                  "description": "Nested property class",
                  "properties": {
                    "foo": {
                      "type": "string",
                      "description": "Nested foo property"
                    },
                    "bar": {
                      "type": "integer",
                      "description": "Nested bar property"
                    }
                  },
                  "required": [
                    "foo",
                    "bar"
                  ]
                },
                "NestedTestClass": {
                  "type": "object",
                  "description": "Nested test class",
                  "properties": {
                    "name": {
                      "type": "string",
                      "description": "The name (override)"
                    },
                    "nested": {
                      "${"$"}ref": "#/defs/NestedProperty"
                    },
                    "nestedList": {
                      "type": "array",
                      "items": {
                        "${"$"}ref": "#/defs/NestedProperty"
                      },
                      "description": "List of nested properties"
                    },
                    "nestedMap": {
                      "type": "object",
                      "additionalProperties": {
                        "${"$"}ref": "#/defs/NestedProperty"
                      },
                      "description": "Map of nested properties"
                    }
                  },
                  "required": [
                    "name",
                    "nested"
                  ]
                }
              },
              "${"$"}ref": "#/defs/NestedTestClass",
              "type": "object"
            }
        """.trimIndent()

        assertEquals(expectedDotSchema, schema)
    }

    @Test
    fun testSimpleSchemaNestedDescriptions() {
        val descriptions = mapOf(
            "NestedTestClass.name" to "The name (override)",
            "NestedTestClass.nestedList" to "List of nested properties",
            "NestedTestClass.nestedMap" to "Map of nested properties",

            "NestedProperty.bar" to "Nested bar property",
        )


        val schema = json.encodeToString(simpleSchemaGenerator.generate("NestedTestClass", serializer<NestedTestClass>(), descriptions))

        val expectedDotSchema = """
            {
              "type": "object",
              "description": "Nested test class",
              "properties": {
                "name": {
                  "type": "string",
                  "description": "The name (override)"
                },
                "nested": {
                  "type": "object",
                  "description": "Nested property class",
                  "properties": {
                    "foo": {
                      "type": "string",
                      "description": "Nested foo property"
                    },
                    "bar": {
                      "type": "integer",
                      "description": "Nested bar property"
                    }
                  },
                  "required": [
                    "foo",
                    "bar"
                  ]
                },
                "nestedList": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "description": "Nested property class",
                    "properties": {
                      "foo": {
                        "type": "string",
                        "description": "Nested foo property"
                      },
                      "bar": {
                        "type": "integer",
                        "description": "Nested bar property"
                      }
                    },
                    "required": [
                      "foo",
                      "bar"
                    ]
                  },
                  "description": "List of nested properties"
                },
                "nestedMap": {
                  "type": "object",
                  "additionalProperties": {
                    "type": "object",
                    "description": "Nested property class",
                    "properties": {
                      "foo": {
                        "type": "string",
                        "description": "Nested foo property"
                      },
                      "bar": {
                        "type": "integer",
                        "description": "Nested bar property"
                      }
                    },
                    "required": [
                      "foo",
                      "bar"
                    ]
                  },
                  "description": "Map of nested properties"
                }
              },
              "required": [
                "name",
                "nested"
              ]
            }
        """.trimIndent()

        assertEquals(expectedDotSchema, schema)
    }

    @Test
    fun testJsonSchemaClosedPolymorphic() {
        val descriptions = mapOf(
            "ClosedSubclass1.id" to "ID for subclass 1",
            "ClosedSubclass1.property1" to "Property 1 for subclass 1",

            "ClosedSubclass2.id" to "ID for subclass 2",
            "ClosedSubclass2.property2" to "Property 2 for subclass 2",
        )

        val schema = json.encodeToString(jsonSchemaGenerator.generate("TestClosedPolymorphism", serializer<TestClosedPolymorphism>(), descriptions))

        val expectedSchema = """
            {
              "${"$"}schema": "http://json-schema.org/draft-07/schema#",
              "${"$"}id": "TestClosedPolymorphism",
              "${"$"}defs": {
                "ClosedSubclass1": {
                  "type": "object",
                  "properties": {
                    "kind": {
                      "const": "ClosedSubclass1"
                    },
                    "id": {
                      "type": "string",
                      "description": "ID for subclass 1"
                    },
                    "property1": {
                      "type": "string",
                      "description": "Property 1 for subclass 1"
                    }
                  },
                  "required": [
                    "kind",
                    "id",
                    "property1"
                  ]
                },
                "ClosedSubclass2": {
                  "type": "object",
                  "properties": {
                    "kind": {
                      "const": "ClosedSubclass2"
                    },
                    "id": {
                      "type": "string",
                      "description": "ID for subclass 2"
                    },
                    "property2": {
                      "type": "integer",
                      "description": "Property 2 for subclass 2"
                    },
                    "recursiveTypeProperty": {
                      "oneOf": [
                        {
                          "${"$"}ref": "#/defs/ClosedSubclass1"
                        },
                        {
                          "${"$"}ref": "#/defs/ClosedSubclass2"
                        }
                      ]
                    }
                  },
                  "required": [
                    "kind",
                    "id",
                    "property2",
                    "recursiveTypeProperty"
                  ]
                }
              },
              "oneOf": [
                {
                  "${"$"}ref": "#/defs/ClosedSubclass1"
                },
                {
                  "${"$"}ref": "#/defs/ClosedSubclass2"
                }
              ],
              "type": "object"
            }
        """.trimIndent()

        assertEquals(expectedSchema, schema)
    }

    @Serializable
    @SerialName("NonRecursivePolymorphism")
    sealed class NonRecursivePolymorphism {
        abstract val id: String

        @Suppress("unused")
        @Serializable
        @SerialName("NonRecursiveSubclass1")
        data class SubClass1(
            override val id: String,
            val property1: String
        ) : NonRecursivePolymorphism()

        @Suppress("unused")
        @Serializable
        @SerialName("NonRecursiveSubclass2")
        data class SubClass2(
            override val id: String,
            val property2: Int
        ) : NonRecursivePolymorphism()
    }

    @Test
    fun testSimpleSchemaClosedPolymorphic() {
        val descriptions = mapOf(
            "NonRecursiveSubclass1.id" to "ID for subclass 1",
            "NonRecursiveSubclass1.property1" to "Property 1 for subclass 1",

            "NonRecursiveSubclass2.id" to "ID for subclass 2",
            "NonRecursiveSubclass2.property2" to "Property 2 for subclass 2",
        )

        val schema = json.encodeToString(simpleSchemaGenerator.generate("NonRecursivePolymorphism", serializer<NonRecursivePolymorphism>(), descriptions))

        val expectedSchema = """
            {
              "oneOf": [
                {
                  "type": "object",
                  "properties": {
                    "kind": {
                      "const": "NonRecursiveSubclass1"
                    },
                    "id": {
                      "type": "string",
                      "description": "ID for subclass 1"
                    },
                    "property1": {
                      "type": "string",
                      "description": "Property 1 for subclass 1"
                    }
                  },
                  "required": [
                    "kind",
                    "id",
                    "property1"
                  ]
                },
                {
                  "type": "object",
                  "properties": {
                    "kind": {
                      "const": "NonRecursiveSubclass2"
                    },
                    "id": {
                      "type": "string",
                      "description": "ID for subclass 2"
                    },
                    "property2": {
                      "type": "integer",
                      "description": "Property 2 for subclass 2"
                    }
                  },
                  "required": [
                    "kind",
                    "id",
                    "property2"
                  ]
                }
              ]
            }
        """.trimIndent()

        assertEquals(expectedSchema, schema)
    }

    @Test
    fun testJsonSchemaOpenPolymorphic() {
        val descriptions = mapOf(
            "OpenSubclass1.id" to "ID for subclass 1",
            "OpenSubclass1.property1" to "Property 1 for subclass 1",

            "OpenSubclass2.id" to "ID for subclass 2",
            "OpenSubclass2.property2" to "Property 2 for subclass 2",
        )

        val schema = json.encodeToString(jsonSchemaGenerator.generate("TestOpenPolymorphism", serializer<TestOpenPolymorphism>(), descriptions))

        val expectedSchema = """
            {
              "${"$"}schema": "http://json-schema.org/draft-07/schema#",
              "${"$"}id": "TestOpenPolymorphism",
              "${"$"}defs": {
                "OpenSubclass1": {
                  "type": "object",
                  "properties": {
                    "kind": {
                      "const": "OpenSubclass1"
                    },
                    "id": {
                      "type": "string",
                      "description": "ID for subclass 1"
                    },
                    "property1": {
                      "type": "string",
                      "description": "Property 1 for subclass 1"
                    }
                  },
                  "required": [
                    "kind",
                    "id",
                    "property1"
                  ]
                },
                "OpenSubclass2": {
                  "type": "object",
                  "properties": {
                    "kind": {
                      "const": "OpenSubclass2"
                    },
                    "id": {
                      "type": "string",
                      "description": "ID for subclass 2"
                    },
                    "property2": {
                      "type": "integer",
                      "description": "Property 2 for subclass 2"
                    },
                    "recursiveTypeProperty": {
                      "oneOf": [
                        {
                          "${"$"}ref": "#/defs/OpenSubclass1"
                        },
                        {
                          "${"$"}ref": "#/defs/OpenSubclass2"
                        }
                      ]
                    }
                  },
                  "required": [
                    "kind",
                    "id",
                    "property2",
                    "recursiveTypeProperty"
                  ]
                }
              },
              "oneOf": [
                {
                  "${"$"}ref": "#/defs/OpenSubclass1"
                },
                {
                  "${"$"}ref": "#/defs/OpenSubclass2"
                }
              ],
              "type": "object"
            }
        """.trimIndent()

        assertEquals(expectedSchema, schema)
    }

    @Serializable
    @SerialName("NonRecursiveOpenPolymorphism")
    abstract class NonRecursiveOpenPolymorphism {
        abstract val id: String

        @Suppress("unused")
        @Serializable
        @SerialName("NonRecursiveOpenSubclass1")
        data class SubClass1(
            override val id: String,
            val property1: String
        ) : NonRecursiveOpenPolymorphism()

        @Suppress("unused")
        @Serializable
        @SerialName("NonRecursiveOpenSubclass2")
        data class SubClass2(
            override val id: String,
            val property2: Int
        ) : NonRecursiveOpenPolymorphism()
    }

    @Test
    fun testSimpleSchemaOpenPolymorphic() {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
            coerceInputValues = true
            classDiscriminator = "kind"
            prettyPrint = true
            prettyPrintIndent = "  "

            serializersModule = SerializersModule {
                polymorphic(NonRecursiveOpenPolymorphism::class) {
                    subclass(NonRecursiveOpenPolymorphism.SubClass1::class, NonRecursiveOpenPolymorphism.SubClass1.serializer())
                    subclass(NonRecursiveOpenPolymorphism.SubClass2::class, NonRecursiveOpenPolymorphism.SubClass2.serializer())
                }
            }
        }

        val simpleSchemaGenerator = JsonSchemaGenerator(json, JsonSchemaGenerator.SchemaFormat.Simple, 10)

        val descriptions = mapOf(
            "NonRecursiveOpenSubclass1.id" to "ID for subclass 1",
            "NonRecursiveOpenSubclass1.property1" to "Property 1 for subclass 1",

            "NonRecursiveOpenSubclass2.id" to "ID for subclass 2",
            "NonRecursiveOpenSubclass2.property2" to "Property 2 for subclass 2",
        )

        val schema = json.encodeToString(simpleSchemaGenerator.generate("NonRecursiveOpenPolymorphism", serializer<NonRecursiveOpenPolymorphism>(), descriptions))

        val expectedSchema = """
            {
              "oneOf": [
                {
                  "type": "object",
                  "properties": {
                    "kind": {
                      "const": "NonRecursiveOpenSubclass1"
                    },
                    "id": {
                      "type": "string",
                      "description": "ID for subclass 1"
                    },
                    "property1": {
                      "type": "string",
                      "description": "Property 1 for subclass 1"
                    }
                  },
                  "required": [
                    "kind",
                    "id",
                    "property1"
                  ]
                },
                {
                  "type": "object",
                  "properties": {
                    "kind": {
                      "const": "NonRecursiveOpenSubclass2"
                    },
                    "id": {
                      "type": "string",
                      "description": "ID for subclass 2"
                    },
                    "property2": {
                      "type": "integer",
                      "description": "Property 2 for subclass 2"
                    }
                  },
                  "required": [
                    "kind",
                    "id",
                    "property2"
                  ]
                }
              ]
            }
        """.trimIndent()

        assertEquals(expectedSchema, schema)
    }
}

package ai.koog.prompt.structure.json.generator

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.ClassDiscriminatorMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsonSchemaGeneratorTest {
    private val json = Json {
        prettyPrint = true
        explicitNulls = false
        isLenient = true
        ignoreUnknownKeys = true
        prettyPrintIndent = "  "
        classDiscriminator = "kind"
        classDiscriminatorMode = ClassDiscriminatorMode.POLYMORPHIC

        serializersModule = SerializersModule {
            polymorphic(TestOpenPolymorphism::class) {
                subclass(TestOpenPolymorphism.SubClass1::class, TestOpenPolymorphism.SubClass1.serializer())
                subclass(TestOpenPolymorphism.SubClass2::class, TestOpenPolymorphism.SubClass2.serializer())
            }
        }
    }

    private val basicGenerator = BasicJsonSchemaGenerator
    private val standardGenerator = StandardJsonSchemaGenerator

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

    @Serializable
    @SerialName("RecursiveTestClass")
    data class RecursiveTestClass(
        val recursiveProperty: RecursiveTestClass?
    )

    @Serializable
    @SerialName("EventData")
    data class EventData(
        @property:LLMDescription("Any valid JSON value.")
        val value: JsonElement
    )

    @Serializable
    @SerialName("NullableEventData")
    data class NullableEventData(
        @property:LLMDescription("Any valid JSON value, or null.")
        val value: JsonElement? = null
    )

    @Test
    fun testGenerateStandardSchema() {
        val result = standardGenerator.generate(json, "TestClass", serializer<TestClass>(), emptyMap())
        val schema = json.encodeToString(result.schema)

        val expectedSchema = """
            {
              "${"$"}id": "TestClass",
              "${"$"}defs": {
                "TestClass": {
                  "type": "object",
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
                      "type": [
                        "string",
                        "null"
                      ]
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
                  ],
                  "additionalProperties": false,
                  "description": "A test class"
                }
              },
              "${"$"}ref": "#/${"$"}defs/TestClass"
            }
        """.trimIndent()

        assertEquals(expectedSchema, schema)
    }

    @Test
    fun testGenerateBasicSchema() {
        val result = basicGenerator.generate(json, "TestClass", serializer<TestClass>(), emptyMap())
        val schema = json.encodeToString(result.schema)

        val expectedSchema = """
            {
              "type": "object",
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
              ],
              "additionalProperties": false
            }
        """.trimIndent()

        assertEquals(expectedSchema, schema)
    }

    @Test
    fun testGenerateBasicSchemaExcludingProperties() {
        val result = basicGenerator.generate(json, "TestClass", serializer<TestClass>(), emptyMap(), setOf("TestClass.nullableProperty"))
        val schema = json.encodeToString(result.schema)

        val expectedSchema = """
            {
              "type": "object",
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
              ],
              "additionalProperties": false
            }
        """.trimIndent()

        assertEquals(expectedSchema, schema)
    }

    @Test
    fun testGenerateBasicSchemaExcludingRequiredProperties() {
        val exception = assertFailsWith<IllegalArgumentException> {
            basicGenerator.generate(
                json,
                "TestClass",
                serializer<TestClass>(),
                emptyMap(),
                setOf("TestClass.stringProperty")
            )
        }
        assertEquals("Property 'TestClass.stringProperty' is marked as excluded, but it is required in the schema.", exception.message)
    }

    @Test
    fun testGenerateStandardSchemaWithDescriptions() {
        val descriptions = mapOf(
            "TestClass" to "A test class (override)",
            "TestClass.intProperty" to "An integer property"
        )

        val result = standardGenerator.generate(json, "TestClass", serializer<TestClass>(), descriptions)
        val schema = json.encodeToString(result.schema)

        val expectedSchema = """
            {
              "${"$"}id": "TestClass",
              "${"$"}defs": {
                "TestClass": {
                  "type": "object",
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
                      "type": [
                        "string",
                        "null"
                      ]
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
                  ],
                  "additionalProperties": false,
                  "description": "A test class (override)"
                }
              },
              "${"$"}ref": "#/${"$"}defs/TestClass"
            }
        """.trimIndent()

        assertEquals(expectedSchema, schema)
    }

    @Test
    fun testGenerateBasicSchemaWithDescriptions() {
        val descriptions = mapOf(
            "TestClass" to "A test class (override)",
            "TestClass.intProperty" to "An integer property"
        )

        val result = basicGenerator.generate(json, "TestClass", serializer<TestClass>(), descriptions)
        val schema = json.encodeToString(result.schema)

        val expectedSchema = """
            {
              "type": "object",
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
              ],
              "additionalProperties": false
            }
        """.trimIndent()

        assertEquals(expectedSchema, schema)
    }

    @Test
    fun testStandardSchemaNestedDescriptions() {
        val descriptions = mapOf(
            "NestedTestClass.name" to "The name (override)",
            "NestedTestClass.nestedList" to "List of nested properties",
            "NestedTestClass.nestedMap" to "Map of nested properties",

            "NestedProperty.bar" to "Nested bar property",
        )

        val result = standardGenerator.generate(json, "NestedTestClass", serializer<NestedTestClass>(), descriptions)
        val schema = json.encodeToString(result.schema)

        val expectedDotSchema = """
            {
              "${"$"}id": "NestedTestClass",
              "${"$"}defs": {
                "NestedProperty": {
                  "type": "object",
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
                  ],
                  "additionalProperties": false,
                  "description": "Nested property class"
                },
                "NestedTestClass": {
                  "type": "object",
                  "properties": {
                    "name": {
                      "type": "string",
                      "description": "The name (override)"
                    },
                    "nested": {
                      "${"$"}ref": "#/${"$"}defs/NestedProperty",
                      "description": "Nested property class"
                    },
                    "nestedList": {
                      "type": "array",
                      "items": {
                        "${"$"}ref": "#/${"$"}defs/NestedProperty"
                      },
                      "description": "List of nested properties"
                    },
                    "nestedMap": {
                      "type": "object",
                      "additionalProperties": {
                        "${"$"}ref": "#/${"$"}defs/NestedProperty"
                      },
                      "description": "Map of nested properties"
                    }
                  },
                  "required": [
                    "name",
                    "nested"
                  ],
                  "additionalProperties": false,
                  "description": "Nested test class"
                }
              },
              "${"$"}ref": "#/${"$"}defs/NestedTestClass"
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

        val result = basicGenerator.generate(json, "NestedTestClass", serializer<NestedTestClass>(), descriptions)
        val schema = json.encodeToString(result.schema)

        val expectedDotSchema = """
            {
              "type": "object",
              "properties": {
                "name": {
                  "type": "string",
                  "description": "The name (override)"
                },
                "nested": {
                  "type": "object",
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
                  ],
                  "additionalProperties": false,
                  "description": "Nested property class"
                },
                "nestedList": {
                  "type": "array",
                  "items": {
                    "type": "object",
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
                    ],
                    "additionalProperties": false
                  },
                  "description": "List of nested properties"
                },
                "nestedMap": {
                  "type": "object",
                  "additionalProperties": {
                    "type": "object",
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
                    ],
                    "additionalProperties": false
                  },
                  "description": "Map of nested properties"
                }
              },
              "required": [
                "name",
                "nested"
              ],
              "additionalProperties": false
            }
        """.trimIndent()

        assertEquals(expectedDotSchema, schema)
    }

    @Test
    fun testStandardSchemaClosedPolymorphic() {
        val descriptions = mapOf(
            "ClosedSubclass1.id" to "ID for subclass 1",
            "ClosedSubclass1.property1" to "Property 1 for subclass 1",

            "ClosedSubclass2.id" to "ID for subclass 2",
            "ClosedSubclass2.property2" to "Property 2 for subclass 2",
        )

        val result = standardGenerator.generate(
            json,
            "TestClosedPolymorphism",
            serializer<TestClosedPolymorphism>(),
            descriptions
        )
        val schema = json.encodeToString(result.schema)

        val expectedSchema = """
            {
              "${"$"}id": "TestClosedPolymorphism",
              "${"$"}defs": {
                "ClosedSubclass1": {
                  "type": "object",
                  "properties": {
                    "id": {
                      "type": "string",
                      "description": "ID for subclass 1"
                    },
                    "property1": {
                      "type": "string",
                      "description": "Property 1 for subclass 1"
                    },
                    "kind": {
                      "const": "ClosedSubclass1"
                    }
                  },
                  "required": [
                    "id",
                    "property1",
                    "kind"
                  ],
                  "additionalProperties": false
                },
                "ClosedSubclass2": {
                  "type": "object",
                  "properties": {
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
                          "${"$"}ref": "#/${"$"}defs/ClosedSubclass1"
                        },
                        {
                          "${"$"}ref": "#/${"$"}defs/ClosedSubclass2"
                        }
                      ]
                    },
                    "kind": {
                      "const": "ClosedSubclass2"
                    }
                  },
                  "required": [
                    "id",
                    "property2",
                    "recursiveTypeProperty",
                    "kind"
                  ],
                  "additionalProperties": false
                }
              },
              "oneOf": [
                {
                  "${"$"}ref": "#/${"$"}defs/ClosedSubclass1"
                },
                {
                  "${"$"}ref": "#/${"$"}defs/ClosedSubclass2"
                }
              ]
            }
        """.trimIndent()

        assertEquals(expectedSchema, schema)
    }

    @Test
    fun testStandardSchemaOpenPolymorphic() {
        val descriptions = mapOf(
            "OpenSubclass1.id" to "ID for subclass 1",
            "OpenSubclass1.property1" to "Property 1 for subclass 1",

            "OpenSubclass2.id" to "ID for subclass 2",
            "OpenSubclass2.property2" to "Property 2 for subclass 2",
        )

        val result = standardGenerator.generate(
            json,
            "TestOpenPolymorphism",
            serializer<TestOpenPolymorphism>(),
            descriptions
        )
        val schema = json.encodeToString(result.schema)

        val expectedSchema = """
            {
              "${"$"}id": "TestOpenPolymorphism",
              "${"$"}defs": {
                "OpenSubclass1": {
                  "type": "object",
                  "properties": {
                    "id": {
                      "type": "string",
                      "description": "ID for subclass 1"
                    },
                    "property1": {
                      "type": "string",
                      "description": "Property 1 for subclass 1"
                    },
                    "kind": {
                      "const": "OpenSubclass1"
                    }
                  },
                  "required": [
                    "id",
                    "property1",
                    "kind"
                  ],
                  "additionalProperties": false
                },
                "OpenSubclass2": {
                  "type": "object",
                  "properties": {
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
                          "${"$"}ref": "#/${"$"}defs/OpenSubclass1"
                        },
                        {
                          "${"$"}ref": "#/${"$"}defs/OpenSubclass2"
                        }
                      ]
                    },
                    "kind": {
                      "const": "OpenSubclass2"
                    }
                  },
                  "required": [
                    "id",
                    "property2",
                    "recursiveTypeProperty",
                    "kind"
                  ],
                  "additionalProperties": false
                }
              },
              "oneOf": [
                {
                  "${"$"}ref": "#/${"$"}defs/OpenSubclass1"
                },
                {
                  "${"$"}ref": "#/${"$"}defs/OpenSubclass2"
                }
              ]
            }
        """.trimIndent()

        assertEquals(expectedSchema, schema)
    }

    @Test
    fun testSimpleSchemaFailsOnTypeRecursion() {
        assertFailsWith<IllegalStateException> {
            basicGenerator.generate(json, "RecursiveTestClass", serializer<RecursiveTestClass>(), emptyMap())
        }
    }

    @Test
    fun testStandardSchemaWithJsonElementProperty() {
        val result = standardGenerator.generate(json, "EventData", serializer<EventData>(), emptyMap())
        val schema = json.encodeToString(result.schema)

        val expectedSchema = """
            {
              "${"$"}id": "EventData",
              "${"$"}defs": {
                "EventData": {
                  "type": "object",
                  "properties": {
                    "value": {
                      "description": "Any valid JSON value."
                    }
                  },
                  "required": [
                    "value"
                  ],
                  "additionalProperties": false
                }
              },
              "${"$"}ref": "#/${"$"}defs/EventData"
            }
        """.trimIndent()

        assertEquals(expectedSchema, schema)
    }

    @Test
    fun testStandardSchemaWithNullableJsonElementProperty() {
        val result = standardGenerator.generate(json, "NullableEventData", serializer<NullableEventData>(), emptyMap())
        val schema = json.encodeToString(result.schema)

        val expectedSchema = """
            {
              "${"$"}id": "NullableEventData",
              "${"$"}defs": {
                "NullableEventData": {
                  "type": "object",
                  "properties": {
                    "value": {
                      "oneOf": [
                        {},
                        {
                          "type": "null"
                        }
                      ],
                      "description": "Any valid JSON value, or null."
                    }
                  },
                  "required": [],
                  "additionalProperties": false
                }
              },
              "${"$"}ref": "#/${"$"}defs/NullableEventData"
            }
        """.trimIndent()

        assertEquals(expectedSchema, schema)
    }
}

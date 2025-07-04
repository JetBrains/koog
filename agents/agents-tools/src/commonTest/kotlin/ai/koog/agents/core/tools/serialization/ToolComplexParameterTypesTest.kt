package ai.koog.agents.core.tools.serialization

import ai.koog.agents.core.tools.*
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalAgentToolsApi::class)
object ToolParameterTypeTestEnabler : DirectToolCallsEnabler

@OptIn(InternalAgentToolsApi::class)
class ToolParameterTypeTest {

    @Test
    fun testObjectToolParameter() = runTest {
        val result = ObjectTool.execute(
            ObjectTool.decodeArgs(
                buildJsonObject {
                    putJsonObject("person") {
                        put("name", "John")
                        put("age", 30)
                        putJsonObject("address") {
                            put("street", "123 Main St")
                            put("city", "Anytown")
                        }
                    }
                }
            ),
            ToolParameterTypeTestEnabler
        )

        assertEquals("Person: John, 30, Address: 123 Main St, Anytown", result.toStringDefault())
    }

    @Test
    fun testObjectWithAdditionalPropertiesToolParameter() = runTest {
        val result = ObjectWithAdditionalPropertiesTool.execute(
            ObjectWithAdditionalPropertiesTool.decodeArgs(
                buildJsonObject {
                    putJsonObject("config") {
                        put("name", "MyConfig")
                        put("custom1", "value1")
                        put("custom2", "value2")
                    }
                }
            ),
            ToolParameterTypeTestEnabler
        )

        assertEquals("Config: MyConfig, Additional: {custom1=value1, custom2=value2}", result.toStringDefault())
    }

    @Test
    fun testListOfEnumsToolParameter() = runTest {
        val result = ListOfEnumsTool.execute(
            ListOfEnumsTool.decodeArgs(
                buildJsonObject {
                    putJsonArray("colors") {
                        add("RED")
                        add("GREEN")
                        add("BLUE")
                    }
                }
            ),
            ToolParameterTypeTestEnabler
        )

        assertEquals("Colors: [RED, GREEN, BLUE]", result.toStringDefault())
    }

    @Test
    fun testListOfObjectsToolParameter() = runTest {
        val result = ListOfObjectsTool.execute(
            ListOfObjectsTool.decodeArgs(
                buildJsonObject {
                    putJsonArray("people") {
                        addJsonObject {
                            put("name", "John")
                            put("age", 30)
                        }
                        addJsonObject {
                            put("name", "Jane")
                            put("age", 25)
                        }
                    }
                }
            ),
            ToolParameterTypeTestEnabler
        )

        assertEquals("People: [John (30), Jane (25)]", result.toStringDefault())
    }

    @Test
    fun testNestedListsToolParameter() = runTest {
        val result = NestedListsTool.execute(
            NestedListsTool.decodeArgs(
                buildJsonObject {
                    putJsonArray("nestedList") {
                        addJsonArray {
                            add(1)
                            add(2)
                        }
                        addJsonArray {
                            add(3)
                            add(4)
                        }
                    }
                }
            ),
            ToolParameterTypeTestEnabler
        )

        assertEquals("Nested list: [[1, 2], [3, 4]]", result.toStringDefault())
    }


    private object NestedListsTool : Tool<NestedListsTool.Args, NestedListsTool.Result>() {
        @Serializable
        data class Args(val nestedList: List<List<Int>>) : ToolArgs

        @Serializable
        data class Result(val nestedList: List<List<Int>>) : ToolResult {
            override fun toStringDefault(): String = "Nested list: $nestedList"
        }

        override val argsSerializer = Args.serializer()

        override val descriptor = ToolDescriptor(
            name = "nested_lists_tool",
            description = "Tool with nested lists parameter",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "nestedList",
                    description = "A nested list of integers",
                    type = ToolParameterType.List(
                        ToolParameterType.List(
                            ToolParameterType.Integer
                        )
                    )
                )
            )
        )

        override suspend fun execute(args: Args): Result = Result(args.nestedList)
    }

    private object ListOfEnumsTool : Tool<ListOfEnumsTool.Args, ListOfEnumsTool.Result>() {
        @Serializable
        enum class Color { RED, GREEN, BLUE }

        @Serializable
        data class Args(val colors: List<Color>) : ToolArgs

        @Serializable
        data class Result(val colors: List<Color>) : ToolResult {
            override fun toStringDefault(): String = "Colors: $colors"
        }

        override val argsSerializer = Args.serializer()

        override val descriptor = ToolDescriptor(
            name = "list_of_enums_tool",
            description = "Tool with list of enums parameter",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "colors",
                    description = "A list of colors",
                    type = ToolParameterType.List(
                        ToolParameterType.Enum(Color.entries)
                    )
                )
            )
        )

        override suspend fun execute(args: Args): Result = Result(args.colors)
    }

    private object ObjectTool : Tool<ObjectTool.Args, ObjectTool.Result>() {
        @Serializable
        data class Address(val street: String, val city: String)

        @Serializable
        data class Person(val name: String, val age: Int, val address: Address)

        @Serializable
        data class Args(val person: Person) : ToolArgs

        @Serializable
        data class Result(val person: Person) : ToolResult {
            override fun toStringDefault(): String =
                "Person: ${person.name}, ${person.age}, Address: ${person.address.street}, ${person.address.city}"
        }

        override val argsSerializer = Args.serializer()

        override val descriptor = ToolDescriptor(
            name = "object_tool",
            description = "Tool with object parameter",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "person",
                    description = "A person object",
                    type = ToolParameterType.Object(
                        properties = listOf(
                            ToolParameterDescriptor(
                                name = "name",
                                description = "Person's name",
                                type = ToolParameterType.String
                            ),
                            ToolParameterDescriptor(
                                name = "age",
                                description = "Person's age",
                                type = ToolParameterType.Integer
                            ),
                            ToolParameterDescriptor(
                                name = "address",
                                description = "Person's address",
                                type = ToolParameterType.Object(
                                    properties = listOf(
                                        ToolParameterDescriptor(
                                            name = "street",
                                            description = "Street address",
                                            type = ToolParameterType.String
                                        ),
                                        ToolParameterDescriptor(
                                            name = "city",
                                            description = "City",
                                            type = ToolParameterType.String
                                        )
                                    ),
                                    requiredProperties = listOf("street", "city")
                                )
                            )
                        ),
                        requiredProperties = listOf("name", "age", "address")
                    )
                )
            )
        )

        override suspend fun execute(args: Args): Result = Result(args.person)
    }

    private object ListOfObjectsTool : Tool<ListOfObjectsTool.Args, ListOfObjectsTool.Result>() {
        @Serializable
        data class Person(val name: String, val age: Int)

        @Serializable
        data class Args(val people: List<Person>) : ToolArgs

        @Serializable
        data class Result(val people: List<Person>) : ToolResult {
            override fun toStringDefault(): String =
                "People: [${people.joinToString(", ") { "${it.name} (${it.age})" }}]"
        }

        override val argsSerializer = Args.serializer()

        override val descriptor = ToolDescriptor(
            name = "list_of_objects_tool",
            description = "Tool with list of objects parameter",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "people",
                    description = "A list of people",
                    type = ToolParameterType.List(
                        ToolParameterType.Object(
                            properties = listOf(
                                ToolParameterDescriptor(
                                    name = "name",
                                    description = "Person's name",
                                    type = ToolParameterType.String
                                ),
                                ToolParameterDescriptor(
                                    name = "age",
                                    description = "Person's age",
                                    type = ToolParameterType.Integer
                                )
                            ),
                            requiredProperties = listOf("name", "age")
                        )
                    )
                )
            )
        )

        override suspend fun execute(args: Args): Result = Result(args.people)
    }

    private object ObjectWithAdditionalPropertiesTool :
        Tool<ObjectWithAdditionalPropertiesTool.Args, ObjectWithAdditionalPropertiesTool.Result>() {

        @Serializable
        data class Config(
            val name: String,
            val custom1: String? = null,
            val custom2: String? = null
        ) {
            fun getAdditionalProperties(): Map<String, String> {
                val result = mutableMapOf<String, String>()
                if (custom1 != null) result["custom1"] = custom1
                if (custom2 != null) result["custom2"] = custom2
                return result
            }
        }

        @Serializable
        data class Args(val config: Config) : ToolArgs

        @Serializable
        data class Result(val config: Config) : ToolResult {
            override fun toStringDefault(): String =
                "Config: ${config.name}, Additional: ${config.getAdditionalProperties()}"
        }

        override val argsSerializer = Args.serializer()

        override val descriptor = ToolDescriptor(
            name = "object_with_additional_properties_tool",
            description = "Tool with object with additional properties parameter",
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "config",
                    description = "A configuration object",
                    type = ToolParameterType.Object(
                        properties = listOf(
                            ToolParameterDescriptor(
                                name = "name",
                                description = "Config name",
                                type = ToolParameterType.String
                            )
                        ),
                        requiredProperties = listOf("name"),
                        additionalProperties = true,
                        additionalPropertiesType = ToolParameterType.String
                    )
                )
            )
        )

        override suspend fun execute(args: Args): Result = Result(args.config)
    }
}
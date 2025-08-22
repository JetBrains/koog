package ai.koog.agents.core.tools.serialization

import ai.koog.agents.core.tools.DirectToolCallsEnabler
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import ai.koog.agents.core.tools.ToolResult
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(InternalAgentToolsApi::class)
object ToolParameterTypeTestEnabler : DirectToolCallsEnabler

// Complex tool params = objects, lists of enums, nested lists.
@OptIn(InternalAgentToolsApi::class)
class ToolComplexParameterTypesTest {

    // Region: Object tool parameter cases
    @Test
    fun testObjectParameter() = runTest {
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

        assertEquals("John", result.person.name)
        assertEquals(30, result.person.age)
        assertEquals("123 Main St", result.person.address.street)
        assertEquals("Anytown", result.person.address.city)
    }

    @Test
    fun testNullObjectParameter() = runTest {
        assertFailsWith<IllegalArgumentException> {
            ObjectTool.decodeArgs(
                buildJsonObject {
                    putJsonObject("person") {
                        put("name", JsonNull)
                        put("age", 30)
                        putJsonObject("address") {
                            put("street", "123 Main St")
                            put("city", "Anytown")
                        }
                    }
                }
            )
        }
    }

    @Test
    fun testInvalidTypeInObjectParameter() = runTest {
        assertFailsWith<IllegalArgumentException> {
            ObjectTool.decodeArgs(
                buildJsonObject {
                    putJsonObject("person") {
                        put("name", "John")
                        put("age", "thirty")
                        putJsonObject("address") {
                            put("street", "123 Main St")
                            put("city", "Anytown")
                        }
                    }
                }
            )
        }
    }

    @Test
    fun testMissingParameterInObject() = runTest {
        assertFailsWith<IllegalArgumentException> {
            ObjectTool.decodeArgs(
                buildJsonObject {
                    putJsonObject("person") {
                        // name is missing
                        put("age", 30)
                        putJsonObject("address") {
                            put("street", "123 Main St")
                            put("city", "Anytown")
                        }
                    }
                }
            )
        }
    }

    @Test
    fun testMissingParameterInNestedObject() = runTest {
        assertFailsWith<IllegalArgumentException> {
            ObjectTool.decodeArgs(
                buildJsonObject {
                    putJsonObject("person") {
                        put("name", "John")
                        put("age", 30)
                        putJsonObject("address") {
                            // street is missing
                            put("city", "Anytown")
                        }
                    }
                }
            )
        }
    }

    @Test
    fun testObjectWithAdditionalProperties() = runTest {
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

        assertEquals("MyConfig", result.config.name)
        val additionalProperties = result.config.getAdditionalProperties()
        assertEquals("value1", additionalProperties["custom1"])
        assertEquals("value2", additionalProperties["custom2"])
        assertEquals(2, additionalProperties.size)
    }

    @Test
    fun testNullObjectWithAdditionalProperties() = runTest {
        assertFailsWith<IllegalArgumentException> {
            ObjectWithAdditionalPropertiesTool.decodeArgs(
                buildJsonObject {
                    putJsonObject("config") {
                        put("name", JsonNull)
                        put("custom1", "value1")
                        put("custom2", "value2")
                    }
                }
            )
        }
    }

    @Test
    fun testListOfObjects() = runTest {
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

        assertEquals(2, result.people.size)
        assertEquals("John", result.people[0].name)
        assertEquals(30, result.people[0].age)
        assertEquals("Jane", result.people[1].name)
        assertEquals(25, result.people[1].age)
    }

    @Test
    fun testEmptyListOfObjects() = runTest {
        val result = ListOfObjectsTool.execute(
            ListOfObjectsTool.decodeArgs(
                buildJsonObject {
                    putJsonArray("people") {}
                }
            ),
            ToolParameterTypeTestEnabler
        )

        assertEquals(0, result.people.size)
        assertTrue(result.people.isEmpty())
    }

    @Test
    fun testNullListOfObjects() = runTest {
        assertFailsWith<IllegalArgumentException> {
            ListOfObjectsTool.decodeArgs(
                buildJsonObject {
                    put("people", JsonNull)
                }
            )
        }
    }
    // endregion

    // Region: Lists of enums
    @Test
    fun testListOfEnumsParameter() = runTest {
        val result = ListOfEnumsTool.execute(
            ListOfEnumsTool.decodeArgs(
                buildJsonObject {
                    putJsonArray("colors") {
                        add("RED")
                        add("GREEN")
                        add("BLUE")
                    }
                    putJsonArray("names") {
                        add("JANE")
                        add("JOHN")
                    }
                    putJsonArray("optional") {
                        add("RED")
                    }
                }
            ),
            ToolParameterTypeTestEnabler
        )

        assertEquals(3, result.colors.size)
        assertEquals(2, result.names.size)
        assertEquals(1, result.optional!!.size)
        assertEquals(ListOfEnumsTool.Color.RED, result.colors[0])
        assertEquals(ListOfEnumsTool.Color.GREEN, result.colors[1])
        assertEquals(ListOfEnumsTool.Color.BLUE, result.colors[2])
        assertEquals(ListOfEnumsTool.Name.JANE, result.names[0])
        assertEquals(ListOfEnumsTool.Name.JOHN, result.names[1])
        assertEquals(ListOfEnumsTool.Color.RED, result.optional[0])
    }

    @Test
    fun testListOfEnumsMissingOptionalParameter() = runTest {
        val result = ListOfEnumsTool.execute(
            ListOfEnumsTool.decodeArgs(
                buildJsonObject {
                    putJsonArray("colors") {
                        add("RED")
                        add("GREEN")
                        add("BLUE")
                    }
                    putJsonArray("names") {
                        add("JANE")
                        add("JOHN")
                    }
                }
            ),
            ToolParameterTypeTestEnabler
        )

        assertEquals(3, result.colors.size)
        assertEquals(2, result.names.size)
        assertEquals(null, result.optional)
        assertEquals(ListOfEnumsTool.Color.RED, result.colors[0])
        assertEquals(ListOfEnumsTool.Color.GREEN, result.colors[1])
        assertEquals(ListOfEnumsTool.Color.BLUE, result.colors[2])
        assertEquals(ListOfEnumsTool.Name.JANE, result.names[0])
        assertEquals(ListOfEnumsTool.Name.JOHN, result.names[1])
    }

    @Test
    fun testListOfEnumsEmptyOptionalParameter() = runTest {
        val result = ListOfEnumsTool.execute(
            ListOfEnumsTool.decodeArgs(
                buildJsonObject {
                    putJsonArray("colors") {
                        add("RED")
                        add("GREEN")
                        add("BLUE")
                    }
                    putJsonArray("names") {
                        add("JANE")
                        add("JOHN")
                    }
                    putJsonArray("optional") {}
                }
            ),
            ToolParameterTypeTestEnabler
        )

        assertEquals(3, result.colors.size)
        assertEquals(2, result.names.size)
        assertEquals(0, result.optional?.size)
        assertEquals(ListOfEnumsTool.Color.RED, result.colors[0])
        assertEquals(ListOfEnumsTool.Color.GREEN, result.colors[1])
        assertEquals(ListOfEnumsTool.Color.BLUE, result.colors[2])
        assertEquals(ListOfEnumsTool.Name.JANE, result.names[0])
        assertEquals(ListOfEnumsTool.Name.JOHN, result.names[1])
    }

    @Test
    fun testListOfEnumsMissingRequiredParameter() = runTest {
        assertFailsWith<IllegalArgumentException> {
            ListOfEnumsTool.decodeArgs(
                buildJsonObject {
                    putJsonArray("colors") {
                        add("RED")
                        add("GREEN")
                        add("BLUE")
                    }
                }
            )
        }
    }

    @Test
    fun testListOfEnumsEmptyRequiredParameters() = runTest {
        val result = ListOfEnumsTool.execute(
            ListOfEnumsTool.decodeArgs(
                buildJsonObject {
                    putJsonArray("colors") {}
                    putJsonArray("names") {}
                }
            ),
            ToolParameterTypeTestEnabler
        )

        assertEquals(0, result.colors.size)
        assertTrue(result.colors.isEmpty())
        assertEquals(0, result.names.size)
        assertTrue(result.names.isEmpty())
        assertEquals(null, result.optional)
    }

    @Test
    fun testListOfEnumsNullRequiredParameter() = runTest {
        assertFailsWith<IllegalArgumentException> {
            ListOfEnumsTool.decodeArgs(
                buildJsonObject {
                    put("colors", JsonNull)
                    putJsonArray("names") {
                        add("JANE")
                    }
                }
            )
        }
    }

    @Test
    fun testInvalidEnumValueInListOfEnumsParameter() = runTest {
        assertFailsWith<IllegalArgumentException> {
            ListOfEnumsTool.decodeArgs(
                buildJsonObject {
                    putJsonArray("colors") {
                        add("RED")
                        add("BLUE")
                        add("INVALID_COLOR")
                    }
                    putJsonArray("names") {
                        add("JANE")
                        add("JOHN")
                    }
                    putJsonArray("optional") {
                        add("RED")
                    }
                }
            )
        }
    }
    // endregion

    // Region: Nested lists
    @Test
    fun testNestedListsParameter() = runTest {
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

        assertEquals(2, result.nestedList.size)
        assertEquals(2, result.nestedList[0].size)
        assertEquals(2, result.nestedList[1].size)

        assertEquals(1, result.nestedList[0][0])
        assertEquals(2, result.nestedList[0][1])

        assertEquals(3, result.nestedList[1][0])
        assertEquals(4, result.nestedList[1][1])
    }

    @Test
    fun testEmptyNestedListsParameter() = runTest {
        val result = NestedListsTool.execute(
            NestedListsTool.decodeArgs(
                buildJsonObject {
                    putJsonArray("nestedList") {}
                }
            ),
            ToolParameterTypeTestEnabler
        )

        assertEquals(0, result.nestedList.size)
        assertTrue(result.nestedList.isEmpty())
    }

    @Test
    fun testNullNestedListsParameter() = runTest {
        assertFailsWith<IllegalArgumentException> {
            NestedListsTool.decodeArgs(
                buildJsonObject {
                    put("nestedList", JsonNull)
                }
            )
        }
    }
    // endregion

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
        enum class Name { JANE, JOHN }

        @Serializable
        data class Args(val colors: List<Color>, val names: List<Name>, val optional: List<Color>?) : ToolArgs

        @Serializable
        data class Result(val colors: List<Color>, val names: List<Name>, val optional: List<Color>?) : ToolResult {
            override fun toStringDefault(): String = "Colors: $colors, names: $names"
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
                ),
                ToolParameterDescriptor(
                    name = "names",
                    description = "A list of names",
                    type = ToolParameterType.List(
                        ToolParameterType.Enum(Name.entries)
                    )
                )
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor(
                    name = "optional",
                    description = "An optional color parameter",
                    type = ToolParameterType.Enum(Color.entries)
                )
            )
        )

        override suspend fun execute(args: Args): Result = Result(args.colors, args.names, args.optional)
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

package ai.koog.agents.core.tools.serialization

import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
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
            )
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
            )
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
            )
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
            )
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
            )
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
            )
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
            )
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
            )
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
            )
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
            )
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
        data class Args(
            @property:LLMDescription("A nested list of integers")
            val nestedList: List<List<Int>>
        )

        @Serializable
        data class Result(val nestedList: List<List<Int>>)

        override val argsSerializer = Args.serializer()
        override val resultSerializer: KSerializer<Result> = Result.serializer()

        override val name = "nested_lists_tool"
        override val description: String = "Tool with nested lists parameter"

        override suspend fun execute(args: Args): Result = Result(args.nestedList)
    }

    private object ListOfEnumsTool : Tool<ListOfEnumsTool.Args, ListOfEnumsTool.Result>() {
        @Serializable
        enum class Color { RED, GREEN, BLUE }

        @Serializable
        enum class Name { JANE, JOHN }

        @Serializable
        data class Args(
            @property:LLMDescription("A list of colors")
            val colors: List<Color>,
            @property:LLMDescription("A list of names")
            val names: List<Name>,
            @property:LLMDescription("An optional color parameter")
            val optional: List<Color>?
        )

        @Serializable
        data class Result(val colors: List<Color>, val names: List<Name>, val optional: List<Color>?)

        override val argsSerializer = Args.serializer()
        override val resultSerializer: KSerializer<Result> = Result.serializer()

        override val name = "list_of_enums_tool"
        override val description: String = "Tool with list of enums parameter"

        override suspend fun execute(args: Args): Result = Result(args.colors, args.names, args.optional)
    }

    private object ObjectTool : Tool<ObjectTool.Args, ObjectTool.Result>() {
        @Serializable
        data class Address(
            @property:LLMDescription("Street address")
            val street: String,
            @property:LLMDescription("City")
            val city: String
        )

        @Serializable
        data class Person(
            @property:LLMDescription("Person's name")
            val name: String,
            @property:LLMDescription("Person's age")
            val age: Int,
            @property:LLMDescription("Person's address")
            val address: Address
        )

        @Serializable
        data class Args(
            @property:LLMDescription("A person object")
            val person: Person
        )

        @Serializable
        data class Result(val person: Person)

        override val argsSerializer = Args.serializer()
        override val resultSerializer: KSerializer<Result> = Result.serializer()

        override val name = "object_tool"
        override val description: String = "Tool with object parameter"

        override suspend fun execute(args: Args): Result = Result(args.person)
    }

    private object ListOfObjectsTool : Tool<ListOfObjectsTool.Args, ListOfObjectsTool.Result>() {
        @Serializable
        data class Person(
            @property:LLMDescription("Person's name")
            val name: String,
            @property:LLMDescription("Person's age")
            val age: Int
        )

        @Serializable
        data class Args(
            @property:LLMDescription("A list of people")
            val people: List<Person>
        )

        @Serializable
        data class Result(val people: List<Person>)

        override val argsSerializer = Args.serializer()
        override val resultSerializer: KSerializer<Result> = Result.serializer()

        override val name = "list_of_objects_tool"
        override val description: String = "Tool with list of objects parameter"

        override suspend fun execute(args: Args): Result = Result(args.people)
    }

    private object ObjectWithAdditionalPropertiesTool :
        Tool<ObjectWithAdditionalPropertiesTool.Args, ObjectWithAdditionalPropertiesTool.Result>() {

        @Serializable
        data class Config(
            @property:LLMDescription("Config name")
            val name: String,
            @property:LLMDescription("")
            val custom1: String? = null,
            @property:LLMDescription("")
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
        data class Args(
            @property:LLMDescription("A configuration object")
            val config: Config
        )

        @Serializable
        data class Result(val config: Config)

        override val argsSerializer = Args.serializer()
        override val resultSerializer: KSerializer<Result> = Result.serializer()

        override val name = "object_with_additional_properties_tool"
        override val description: String = "Tool with object with additional properties parameter"

        override suspend fun execute(args: Args): Result = Result(args.config)
    }
}

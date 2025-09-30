@file:Suppress("RedundantSuspendModifier")

package ai.koog.agents.core.tools.reflect

import ai.koog.agents.core.tools.DirectToolCallsEnabler
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.reflect.KFunction
import kotlin.test.Test
import kotlin.test.assertEquals

@Tool
@LLMDescription("Global tool description")
suspend fun globalTool(
    @LLMDescription("Count parameter") count: Int,
): String {
    return "Global tool called: $count"
}

interface Tools {
    @Tool
    suspend fun tool1Async(
        @LLMDescription("int argument") arg: Int
    ): String
}

interface ToolSet1Mixin {
    @Tool
    @LLMDescription("Mixin tool 1")
    fun toolMixin1(
        @LLMDescription("float argument")
        arg: Float
    ): Int
}

interface ToolSet1BaseInterface {
    @Tool
    @LLMDescription("Base tool 1")
    fun toolBase1(): String

    @Tool
    @LLMDescription("Base tool 2 description")
    fun toolBase2OverriddenInInterface(
        @LLMDescription("int argument")
        intArg: Int
    ): String
}

interface ToolSet1 : ToolSet1BaseInterface, ToolSet {
    @LLMDescription("The best tool number 1")
    @Tool
    fun tool1(
        @LLMDescription("int argument")
        arg: Int
    ): String

    fun tool2(
        @LLMDescription("int argument")
        arg: Int
    ): String

    fun tool3(
        @LLMDescription("int argument")
        arg: Int
    ): String

    override fun toolBase1(): String

    @Tool
    @LLMDescription("Base tool 2 description overridden")
    override fun toolBase2OverriddenInInterface(
        @LLMDescription("int argument overridden")
        intArg: Int
    ): String
}

open class ToolSet1Impl : ToolSet1 {
    override fun tool1(arg: Int): String {
        return "tool1 called: $arg"
    }

    @LLMDescription("Wonderful tool number 2")
    @Tool
    override fun tool2(arg: Int): String {
        return "tool2 called: $arg"
    }

    // should not be listed
    override fun tool3(arg: Int): String {
        return "tool3 called"
    }

    @LLMDescription("Perfect tool 4")
    @Tool
    fun tool4(
        @LLMDescription("int argument")
        arg: Int
    ): String {
        return "tool4 called: $arg"
    }

    override fun toolBase1(): String {
        return "toolBase1 called"
    }

    override fun toolBase2OverriddenInInterface(intArg: Int): String {
        return "toolBase2OverriddenInInterface called: $intArg"
    }
}

class DerivedToolSet1Impl : ToolSet1Impl() {
    @Tool
    @LLMDescription("Derived tool 5")
    fun derivedTool5(arg: Int): String {
        return "derivedTool5 called: $arg"
    }
}

class MyTools : Tools {

    @Serializable
    data class ComplexType(val field1: Int, val field2: String)

    data class NonSerializableType(val i: Int)

    @Tool
    @LLMDescription("The best tool number 1")
    override suspend fun tool1Async(
        @LLMDescription("int arg") arg: Int
    ): String {
        return "tool1 called: $arg"
    }

    @Tool
    @LLMDescription("Wonderful tool number 2")
    fun tool2(
        @LLMDescription("int arg") arg: Int
    ): String {
        return "tool2 called: $arg"
    }

    @Tool
    @LLMDescription("Perfect tool 3 with void result")
    suspend fun tool3(
        @LLMDescription("int arg") arg: Int
    ) {
        println("tool3 called: arg")
    }

    @Tool
    @LLMDescription("Brilliant tool 4 with int result and multiple parameters")
    suspend fun tool4(
        @LLMDescription("int arg") argInt: Int,
        @LLMDescription("string arg") argString: String
    ): Int {
        return argInt + Integer.parseInt(argString)
    }

    @Tool
    @LLMDescription("Crazy tool 5")
    suspend fun tool5(
        @LLMDescription("Int arg") argInt: Int,
        @LLMDescription("String arg") argString: String
    ): ComplexType {
        return ComplexType(1, argString)
    }

    @Tool
    @LLMDescription("Non serializable tool 6")
    suspend fun tool6(
        @LLMDescription("Non serializable arg") argNotSerializable: NonSerializableType,
    ) {
        println("tool6 called")
    }

    @Tool
    @LLMDescription("Default args tool 7")
    suspend fun tool7(
        @LLMDescription("arg Int") argInt: Int,
        @LLMDescription("arg Bool default") argBool: Boolean = true,
    ): String {
        return "tool 7 called"
    }

    @Tool
    @LLMDescription("Default args tool 8")
    suspend fun too8(
        @LLMDescription("Non serializable arg") argInt: Int,
        @LLMDescription("Non serializable arg") argBool: Boolean = true,
    ): String {
        return "tool 7 called"
    }
}

@OptIn(InternalAgentToolsApi::class)
class ToolsFromCallableTest {
    companion object {
        object ToolsEnabler : DirectToolCallsEnabler

        val tools = MyTools()
        val json = Json

        @JvmStatic
        fun testVariants(): Array<Arguments> {
            return arrayOf(
                Arguments.of(
                    ::globalTool,
                    buildJsonObject { put("count", JsonPrimitive(5)) },
                    "\"Global tool called: 5\""
                ),
                Arguments.of(
                    tools::tool1Async,
                    buildJsonObject { put("arg", JsonPrimitive(1)) },
                    "\"tool1 called: 1\""
                ),
                Arguments.of(
                    tools::tool2,
                    buildJsonObject { put("arg", JsonPrimitive(1)) },
                    "\"tool2 called: 1\""
                ),
                Arguments.of(
                    tools::tool3,
                    buildJsonObject { put("arg", JsonPrimitive(1)) },
                    "{}"
                ),
                Arguments.of(
                    tools::tool4,
                    buildJsonObject {
                        put("argInt", JsonPrimitive(1))
                        put("argString", JsonPrimitive("10"))
                    },
                    "11"
                ),
                Arguments.of(
                    tools::tool5,
                    buildJsonObject {
                        put("argInt", JsonPrimitive(1))
                        put("argString", JsonPrimitive("10"))
                    },
                    """{"field1":1,"field2":"10"}"""
                ),
                Arguments.of(
                    tools::tool7,
                    buildJsonObject {
                        put("argInt", JsonPrimitive(1))
                        put("argString", JsonPrimitive("10"))
                    },
                    """"tool 7 called""""
                ),
                Arguments.of(
                    tools::tool7,
                    buildJsonObject {
                        put("wrongArg", JsonPrimitive("Wrong"))
                        put("argInt", JsonPrimitive(1))
                    },
                    """"tool 7 called""""
                ),
            )
        }

        @JvmStatic
        fun descriptionTestVariants(): Array<Arguments> {
            return arrayOf(
                Arguments.of(
                    ToolSet1Impl().asTools(json),
                    """
#0: ToolDescriptor(name=tool1, description=The best tool number 1, requiredParameters=[ToolParameterDescriptor(name=arg, description=int argument, type=Integer)], optionalParameters=[])
#1: ToolDescriptor(name=tool2, description=Wonderful tool number 2, requiredParameters=[ToolParameterDescriptor(name=arg, description=arg, type=Integer)], optionalParameters=[])
#2: ToolDescriptor(name=tool4, description=Perfect tool 4, requiredParameters=[ToolParameterDescriptor(name=arg, description=int argument, type=Integer)], optionalParameters=[])
#3: ToolDescriptor(name=toolBase1, description=Base tool 1, requiredParameters=[], optionalParameters=[])
#4: ToolDescriptor(name=toolBase2OverriddenInInterface, description=Base tool 2 description overridden, requiredParameters=[ToolParameterDescriptor(name=intArg, description=int argument overridden, type=Integer)], optionalParameters=[])
""".trim()
                ),
                Arguments.of(
                    DerivedToolSet1Impl().asTools(json),
                    """
#0: ToolDescriptor(name=derivedTool5, description=Derived tool 5, requiredParameters=[ToolParameterDescriptor(name=arg, description=arg, type=Integer)], optionalParameters=[])
#1: ToolDescriptor(name=tool1, description=The best tool number 1, requiredParameters=[ToolParameterDescriptor(name=arg, description=int argument, type=Integer)], optionalParameters=[])
#2: ToolDescriptor(name=tool2, description=Wonderful tool number 2, requiredParameters=[ToolParameterDescriptor(name=arg, description=arg, type=Integer)], optionalParameters=[])
#3: ToolDescriptor(name=tool4, description=Perfect tool 4, requiredParameters=[ToolParameterDescriptor(name=arg, description=int argument, type=Integer)], optionalParameters=[])
#4: ToolDescriptor(name=toolBase1, description=Base tool 1, requiredParameters=[], optionalParameters=[])
#5: ToolDescriptor(name=toolBase2OverriddenInInterface, description=Base tool 2 description overridden, requiredParameters=[ToolParameterDescriptor(name=intArg, description=int argument overridden, type=Integer)], optionalParameters=[])
""".trim()
                ),
                Arguments.of(
                    ToolSet1Impl().asToolsByInterface<ToolSet1>(json),
                    """
#0: ToolDescriptor(name=tool1, description=The best tool number 1, requiredParameters=[ToolParameterDescriptor(name=arg, description=int argument, type=Integer)], optionalParameters=[])
#1: ToolDescriptor(name=toolBase1, description=Base tool 1, requiredParameters=[], optionalParameters=[])
#2: ToolDescriptor(name=toolBase2OverriddenInInterface, description=Base tool 2 description overridden, requiredParameters=[ToolParameterDescriptor(name=intArg, description=int argument overridden, type=Integer)], optionalParameters=[])
""".trim()
                ),
            )
        }
    }

    @ParameterizedTest
    @MethodSource("testVariants")
    fun testJsonBridge(callable: KFunction<*>, argumentJson: JsonObject, expectedResult: String) {
        val tool = callable.asTool(json)
        val args = tool.decodeArgs(argumentJson)
        val result = runBlocking {
            tool.execute(args, ToolsEnabler)
        }
        assertEquals(
            expectedResult,
            tool.encodeResultToStringUnsafe(result),
            "Incorrect result for $callable with argument $argumentJson"
        )
    }

    @Test
    fun testNonSerializable() {
        assertThrows<IllegalArgumentException> { tools::tool6.asTool() }
    }

    @ParameterizedTest
    @MethodSource("descriptionTestVariants")
    fun testOnClasses(tools: List<ToolFromCallable>, expectedDescription: String) {
        val rendered = buildString {
            for ((i, tool) in tools.withIndex()) {
                appendLine("#$i: ${tool.descriptor}")
            }
        }.trim()
        assertEquals(expectedDescription, rendered)
    }
}

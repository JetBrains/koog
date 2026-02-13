package ai.koog.agents.tools;

import ai.koog.agents.core.tools.Tool;
import ai.koog.agents.core.tools.ToolDescriptor;
import ai.koog.agents.core.tools.ToolParameterType;
import ai.koog.agents.core.tools.reflect.java.ToolFromJavaMethod;
import ai.koog.agents.tools.test.*;
import ai.koog.agents.tools.test.utils.ToolUtils;
import kotlin.coroutines.Continuation;
import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonObject;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.*;

import static ai.koog.agents.core.tools.reflect.java.JavaIUtilsKt.asTool;
import static org.junit.jupiter.api.Assertions.*;

public class JavaMethodToolsTest {

    @FunctionalInterface
    private interface BlockingBody<R> {
        Object run(Continuation<? super R> cont);
    }

    private static JsonObject jsonObject(String json) {
        JsonElement el = Json.Default.parseToJsonElement(json);
        if (!(el instanceof JsonObject)) throw new IllegalArgumentException("Not a JsonObject: " + json);
        return (JsonObject) el;
    }

    private static Tool<ToolFromJavaMethod.VarArgs, Object> toolFrom(Method m, Object thisRef) {
        // call internal top-level function from Kotlin file javaIUtils.kt
        return asTool(m, Json.Default, thisRef, null, null);
    }

    @Test
    public void testPrimitives() {
        Method m;
        try {
            m = JavaToolbox.class.getDeclaredMethod("add", int.class, int.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        Tool<ToolFromJavaMethod.VarArgs, Object> tool = toolFrom(m, null);
        JsonObject json = jsonObject("{\"a\":2,\"b\":3}");
        ToolFromJavaMethod.VarArgs args = tool.decodeArgs(json);
        var result = ToolUtils.executeToolBlocking(tool, args);
        assertEquals(5, (int) result);
    }

    @Test
    public void testEmpty() {
        Method m;
        try {
            m = JavaToolbox.class.getDeclaredMethod("ping");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        Tool<ToolFromJavaMethod.VarArgs, Object> tool = toolFrom(m, null);
        JsonObject json = jsonObject("{}");
        ToolFromJavaMethod.VarArgs args = tool.decodeArgs(json);
        var result = ToolUtils.executeToolBlocking(tool, args);
        assertEquals("pong", result);
    }

    @Test
    public void testSerializableDataClass() {
        Method m;
        try {
            m = JavaToolbox.class.getDeclaredMethod("echo", Payload.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        Tool<ToolFromJavaMethod.VarArgs, Object> tool = toolFrom(m, null);
        JsonObject json = jsonObject("{\"p\":{\"id\":7,\"name\":\"x\"}}");
        ToolFromJavaMethod.VarArgs args = tool.decodeArgs(json);
        Payload result = (Payload) ToolUtils.executeToolBlocking(tool, args);
        assertEquals(7, result.getId());
        assertEquals("x", result.getName());
    }

    @Test
    public void testInstanceMethod() {
        JavaToolbox inst = new JavaToolbox();
        Method m;
        try {
            m = JavaToolbox.class.getDeclaredMethod("inc", int.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        Tool<ToolFromJavaMethod.VarArgs, Object> tool = toolFrom(m, inst);
        JsonObject json = jsonObject("{\"x\":41}");
        ToolFromJavaMethod.VarArgs args = tool.decodeArgs(json);
        var result = ToolUtils.executeToolBlocking(tool, args);
        assertEquals(42, (int) result);
    }

    @Test
    public void testStrings() {
        Method m;
        try {
            m = JavaToolbox.class.getDeclaredMethod("concat", String.class, String.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        Tool<ToolFromJavaMethod.VarArgs, Object> tool = toolFrom(m, null);
        JsonObject json = jsonObject("{\"a\":\"hello \",\"b\":\"world\"}");
        ToolFromJavaMethod.VarArgs args = tool.decodeArgs(json);
        var result = ToolUtils.executeToolBlocking(tool, args);
        assertEquals("hello world", result);
    }

    @Test
    public void testEnums() {
        Method m;
        try {
            m = JavaToolbox.class.getDeclaredMethod("colorName", JavaToolbox.Color.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        Tool<ToolFromJavaMethod.VarArgs, Object> tool = toolFrom(m, null);
        JsonObject json = jsonObject("{\"color\":\"GREEN\"}");
        ToolFromJavaMethod.VarArgs args = tool.decodeArgs(json);
        var result = ToolUtils.executeToolBlocking(tool, args);
        assertEquals("GREEN", result);
    }

    @Test
    public void testComplexObject() {
        Method m;
        try {
            m = JavaToolbox.class.getDeclaredMethod("complexInfo", Complex.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        Tool<ToolFromJavaMethod.VarArgs, Object> tool = toolFrom(m, null);
        JsonObject json = jsonObject("{\"c\":{\"payload\":{\"id\":123,\"name\":\"nested\"},\"meta\":\"test\"}}");
        ToolFromJavaMethod.VarArgs args = tool.decodeArgs(json);
        var result = ToolUtils.executeToolBlocking(tool, args);
        assertEquals("test:nested", result);
    }

    @Test
    public void testLLMDescription() {
        Method m;
        try {
            m = JavaToolbox.class.getDeclaredMethod("describedAdd", int.class, int.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        Tool<ToolFromJavaMethod.VarArgs, Object> tool = toolFrom(m, null);

        ToolDescriptor descriptor = tool.getDescriptor();
        assertEquals("describedAdd", descriptor.getName());
        assertEquals("Adds two numbers", descriptor.getDescription());

        var params = descriptor.getRequiredParameters();
        assertEquals(2, params.size());

        assertEquals("a", params.get(0).getName());
        assertEquals(ToolParameterType.Integer.INSTANCE, params.get(0).getType());

        assertEquals("b", params.get(1).getName());
        assertEquals(ToolParameterType.Integer.INSTANCE, params.get(1).getType());
    }

    @Test
    public void testNestedEnumInObject() {
        Method m;
        try {
            m = JavaToolbox.class.getDeclaredMethod("testNestedEnum", NestedEnumPayload.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        Tool<ToolFromJavaMethod.VarArgs, Object> tool = toolFrom(m, null);
        JsonObject json = jsonObject("{\"p\":{\"outer\":\"X\",\"inner\":\"A\"}}");
        ToolFromJavaMethod.VarArgs args = tool.decodeArgs(json);
        var result = ToolUtils.executeToolBlocking(tool, args);
        assertEquals("X:A", result);
    }

    @Test
    public void testEnumListInObject() {
        Method m;
        try {
            m = JavaToolbox.class.getDeclaredMethod("testEnumList", EnumListPayload.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        Tool<ToolFromJavaMethod.VarArgs, Object> tool = toolFrom(m, null);
        JsonObject json = jsonObject("{\"p\":{\"enums\":[\"A\",\"B\",\"C\"]}}");
        ToolFromJavaMethod.VarArgs args = tool.decodeArgs(json);
        var result = ToolUtils.executeToolBlocking(tool, args);
        assertEquals("A,B,C", result);
    }

    @Test
    public void testListOfLists() {
        Method m;
        try {
            m = JavaToolbox.class.getDeclaredMethod("testListOfLists", List.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        Tool<ToolFromJavaMethod.VarArgs, Object> tool = toolFrom(m, null);
        JsonObject json = jsonObject("{\"list\":[[\"a\",\"b\"],[\"c\",\"d\"]]}");
        ToolFromJavaMethod.VarArgs args = tool.decodeArgs(json);
        var result = ToolUtils.executeToolBlocking(tool, args);
        assertEquals("a-b|c-d", result);
    }
}

package ai.koog.agents.core.tools;

import ai.koog.agents.core.tools.ToolDescriptor;
import ai.koog.agents.core.tools.ToolParameterType;
import ai.koog.agents.core.tools.reflect.ToolFromCallable;
import ai.koog.agents.tools.test.Complex;
import ai.koog.agents.tools.test.EnumListPayload;
import ai.koog.agents.tools.test.NestedEnumPayload;
import ai.koog.agents.tools.test.Payload;
import ai.koog.agents.tools.test.utils.ToolUtils;
import ai.koog.serialization.JSONElement;
import ai.koog.serialization.JSONObject;
import ai.koog.serialization.JSONSerializer;
import ai.koog.serialization.kotlinx.KotlinxSerializer;
import kotlin.coroutines.Continuation;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static ai.koog.agents.core.tools.reflect.java.JavaToolFromCallableExtensionsKt.asTool;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("unused")
public class JavaMethodToolsTest {
    @FunctionalInterface
    private interface BlockingBody<R> {
        Object run(Continuation<? super R> cont);
    }

    private final static JSONSerializer serializer = new KotlinxSerializer();

    private static JSONObject jsonObject(@Language("JSON") String json) {
        JSONElement el = serializer.decodeJSONElementFromString(json);
        if (!(el instanceof JSONObject)) throw new IllegalArgumentException("Not a JsonObject: " + json);
        return (JSONObject) el;
    }

    private static ToolFromCallable<?> toolFrom(Method m, Object thisRef) {
        return asTool(m, thisRef, null, null);
    }

    @Test
    public void testPrimitives() {
        Method m;
        try {
            m = JavaToolbox.class.getDeclaredMethod("add", int.class, int.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        ToolFromCallable<?> tool = toolFrom(m, null);
        ToolFromCallable.Args decoded = tool.decodeArgs(jsonObject("{\"a\":2,\"b\":3}"), serializer);
        var result = ToolUtils.executeToolBlocking(tool, decoded);
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
        ToolFromCallable<?> tool = toolFrom(m, null);
        ToolFromCallable.Args decoded = tool.decodeArgs(jsonObject("{}"), serializer);
        var result = ToolUtils.executeToolBlocking(tool, decoded);
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
        ToolFromCallable<?> tool = toolFrom(m, null);
        ToolFromCallable.Args decoded = tool.decodeArgs(jsonObject("{\"p\":{\"id\":7,\"name\":\"x\"}}"), serializer);
        Payload result = (Payload) ToolUtils.executeToolBlocking(tool, decoded);
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
        ToolFromCallable<?> tool = toolFrom(m, inst);
        ToolFromCallable.Args decoded = tool.decodeArgs(jsonObject("{\"x\":41}"), serializer);
        var result = ToolUtils.executeToolBlocking(tool, decoded);
        assertEquals(42, (int) result);
    }


    @Test
    public void testEnums() {
        Method m;
        try {
            m = JavaToolbox.class.getDeclaredMethod("colorName", JavaToolbox.Color.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        ToolFromCallable<?> tool = toolFrom(m, null);
        ToolFromCallable.Args decoded = tool.decodeArgs(jsonObject("{\"color\":\"GREEN\"}"), serializer);
        var result = ToolUtils.executeToolBlocking(tool, decoded);
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
        ToolFromCallable<?> tool = toolFrom(m, null);
        ToolFromCallable.Args decoded = tool.decodeArgs(jsonObject("{\"c\":{\"payload\":{\"id\":123,\"name\":\"nested\"},\"meta\":\"test\"}}"), serializer);
        var result = ToolUtils.executeToolBlocking(tool, decoded);
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
        ToolFromCallable<?> tool = toolFrom(m, null);

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
        ToolFromCallable<?> tool = toolFrom(m, null);
        ToolFromCallable.Args decoded = tool.decodeArgs(jsonObject("{\"p\":{\"outer\":\"X\",\"inner\":\"A\"}}"), serializer);
        var result = ToolUtils.executeToolBlocking(tool, decoded);
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
        ToolFromCallable<?> tool = toolFrom(m, null);
        ToolFromCallable.Args decoded = tool.decodeArgs(jsonObject("{\"p\":{\"enums\":[\"A\",\"B\",\"C\"]}}"), serializer);
        var result = ToolUtils.executeToolBlocking(tool, decoded);
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
        ToolFromCallable<?> tool = toolFrom(m, null);
        ToolFromCallable.Args decoded = tool.decodeArgs(jsonObject("{\"list\":[[\"a\",\"b\"],[\"c\",\"d\"]]}"), serializer);
        var result = ToolUtils.executeToolBlocking(tool, decoded);
        assertEquals("a-b|c-d", result);
    }
}

package ai.koog.agents.tools;

import ai.koog.agents.core.tools.Tool;
import ai.koog.agents.core.tools.ToolDescriptor;
import ai.koog.agents.core.tools.ToolParameterType;
import ai.koog.agents.core.tools.reflect.java.ToolFromJavaMethod;
import ai.koog.agents.tools.test.Complex;
import ai.koog.agents.tools.test.EnumListPayload;
import ai.koog.agents.tools.test.NestedEnumPayload;
import ai.koog.agents.tools.test.Payload;
import ai.koog.agents.tools.test.utils.ToolUtils;
import kotlin.coroutines.Continuation;
import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonObject;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.*;

import static ai.koog.agents.core.tools.reflect.java.JavaIUtilsKt.asTool;
import static org.assertj.core.api.Assertions.assertThat;

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

    private static Tool<ToolFromJavaMethod.VarArgs, Object> getTool(String methodName, Object thisRef, Class<?>... parameterTypes) {
        try {
            Method m = JavaToolbox.class.getDeclaredMethod(methodName, parameterTypes);
            return toolFrom(m, thisRef);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static Object execute(String methodName, String jsonInput, Class<?>... parameterTypes) {
        return execute(methodName, null, jsonInput, parameterTypes);
    }

    private static Object execute(String methodName, Object thisRef, String jsonInput, Class<?>... parameterTypes) {
        Tool<ToolFromJavaMethod.VarArgs, Object> tool = getTool(methodName, thisRef, parameterTypes);
        JsonObject json = jsonObject(jsonInput);
        ToolFromJavaMethod.VarArgs args = tool.decodeArgs(json);
        return ToolUtils.executeToolBlocking(tool, args);
    }

    @Test
    public void testPrimitives() {
        var result = execute("add", "{\"a\":2,\"b\":3}", int.class, int.class);
        assertThat((int) result).isNotNull().isEqualTo(5);
    }

    @Test
    public void testEmpty() {
        var result = execute("ping", "{}");
        assertThat(result).isNotNull().isEqualTo("pong");
    }

    @Test
    public void testSerializableDataClass() {
        Payload result = (Payload) execute("echo", "{\"p\":{\"id\":7,\"name\":\"x\"}}", Payload.class);
        assertThat(result.getId()).isNotNull().isEqualTo(7);
        assertThat(result.getName()).isNotNull().isEqualTo("x");
    }

    @Test
    public void testInstanceMethod() {
        JavaToolbox inst = new JavaToolbox();
        var result = execute("inc", inst, "{\"x\":41}", int.class);
        assertThat((int) result).isNotNull().isEqualTo(42);
    }

    @Test
    public void testStrings() {
        var result = execute("concat", "{\"a\":\"hello \",\"b\":\"world\"}", String.class, String.class);
        assertThat(result).isNotNull().isEqualTo("hello world");
    }

    @Test
    public void testEnums() {
        var result = execute("colorName", "{\"color\":\"GREEN\"}", JavaToolbox.Color.class);
        assertThat(result).isNotNull().isEqualTo("GREEN");
    }

    @Test
    public void testComplexObject() {
        var result = execute("complexInfo", "{\"c\":{\"payload\":{\"id\":123,\"name\":\"nested\"},\"meta\":\"test\"}}", Complex.class);
        assertThat(result).isNotNull().isEqualTo("test:nested");
    }

    @Test
    public void testLLMDescription() {
        Tool<ToolFromJavaMethod.VarArgs, Object> tool = getTool("describedAdd", null, int.class, int.class);

        ToolDescriptor descriptor = tool.getDescriptor();
        assertThat(descriptor.getName()).isNotNull().isEqualTo("describedAdd");
        assertThat(descriptor.getDescription()).isNotNull().isEqualTo("Adds two numbers");

        var params = descriptor.getRequiredParameters();
        assertThat(params.size()).isNotNull().isEqualTo(2);

        assertThat(params.get(0).getName()).isNotNull().isEqualTo("a");
        assertThat(params.get(0).getType()).isNotNull().isEqualTo(ToolParameterType.Integer.INSTANCE);

        assertThat(params.get(1).getName()).isNotNull().isEqualTo("b");
        assertThat(params.get(1).getType()).isNotNull().isEqualTo(ToolParameterType.Integer.INSTANCE);
    }

    @Test
    public void testNestedEnumInObject() {
        var result = execute("testNestedEnum", "{\"p\":{\"outer\":\"X\",\"inner\":\"A\"}}", NestedEnumPayload.class);
        assertThat(result).isNotNull().isEqualTo("X:A");
    }

    @Test
    public void testEnumListInObject() {
        var result = execute("testEnumList", "{\"p\":{\"enums\":[\"A\",\"B\",\"C\"]}}", EnumListPayload.class);
        assertThat(result).isNotNull().isEqualTo("A,B,C");
    }

    @Test
    public void testListOfLists() {
        var result = execute("testListOfLists", "{\"list\":[[\"a\",\"b\"],[\"c\",\"d\"]]}", List.class);
        assertThat(result).isNotNull().isEqualTo("a-b|c-d");
    }
}

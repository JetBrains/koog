package ai.koog.agents.tools;

import ai.koog.agents.core.tools.Tool;
import ai.koog.agents.core.tools.reflect.ToolFromCallable;
import ai.koog.agents.core.tools.reflect.java.ToolFromJavaMethod;
import ai.koog.agents.tools.test.Payload;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;
import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonObject;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static ai.koog.agents.core.tools.reflect.UtilKt.asTool;
import static org.junit.Assert.*;

public class JavaMethodToolsTest {

    private static <R> R runBlocking(BlockingBody<R> body) {
        return BuildersKt.runBlocking(Dispatchers.getDefault(), (CoroutineScope scope, Continuation<? super R> cont) -> body.run(cont));
    }

    @FunctionalInterface
    private interface BlockingBody<R> {
        Object run(Continuation<? super R> cont);
    }

    private static JsonObject jsonObject(String json) {
        JsonElement el = Json.Default.parseToJsonElement(json);
        if (!(el instanceof JsonObject)) throw new IllegalArgumentException("Not a JsonObject: " + json);
        return (JsonObject) el;
    }

    private static Tool<ToolFromCallable.VarArgs, Object> toolFrom(Method m, Object thisRef) {
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
        Tool<ToolFromCallable.VarArgs, Object> tool = toolFrom(m, null);
        ToolFromCallable.VarArgs decoded = tool.decodeArgs(jsonObject("{\"arg0\":2,\"arg1\":3}"));
        Integer result = runBlocking(cont -> tool.execute(decoded, cont));
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
        Tool<ToolFromCallable.VarArgs, Object> tool = toolFrom(m, null);
        ToolFromCallable.VarArgs decoded = tool.decodeArgs(jsonObject("{}"));
        String result = runBlocking(cont -> tool.execute(decoded, cont));
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
        Tool<ToolFromCallable.VarArgs, Object> tool = toolFrom(m, null);
        ToolFromCallable.VarArgs decoded = tool.decodeArgs(jsonObject("{\"p\":{\"id\":7,\"name\":\"x\"}}"));
        Payload result = runBlocking(cont -> tool.execute(decoded, cont));
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
        Tool<ToolFromCallable.VarArgs, Object> tool = toolFrom(m, inst);
        ToolFromCallable.VarArgs decoded = tool.decodeArgs(jsonObject("{\"x\":41}"));
        Integer result = runBlocking(cont -> tool.execute(decoded, cont));
        assertEquals(42, (int) result);
    }
}

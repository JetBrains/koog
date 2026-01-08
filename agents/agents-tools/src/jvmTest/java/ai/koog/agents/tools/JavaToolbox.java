package ai.koog.agents.tools;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.tools.test.Payload;

// TODO: Remove @LLMDescription, and fix koog/agents/agents-tools/src/jvmMain/kotlin/ai/koog/agents/core/tools/reflect/java/javaIUtils.kt so that it detects parameter names always (currently: arg0, arg1, arg2, etc. -- see /Users/Vadim.Briliantov/koog/agents/agents-tools/src/jvmTest/java/ai/koog/agents/tools/JavaMethodToolsTest.java)
public class JavaToolbox {

    // primitives
    public static int add(int a, int b) {
        return a + b;
    }

    // boxed types and strings
    public static String concat(String a, String b) {
        return (a == null ? "" : a) + (b == null ? "" : b);
    }

    // no args
    public static String ping() {
        return "pong";
    }

    // serializable data class
    public static Payload echo(Payload p) {
        return p;
    }

    // instance method with primitive
    public int inc(int x) {
        return x + 1;
    }
}

package ai.koog.agents.tools;

import ai.koog.agents.tools.test.Payload;

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

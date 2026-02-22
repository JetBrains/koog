package ai.koog.agents.tools;

import ai.koog.agents.core.tools.annotations.LLMDescription;
import ai.koog.agents.tools.test.Complex;
import ai.koog.agents.tools.test.EnumListPayload;
import ai.koog.agents.tools.test.NestedEnumPayload;
import ai.koog.agents.tools.test.Payload;

import java.util.List;
import java.util.stream.Collectors;

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

    public enum Color {
        RED, GREEN, BLUE
    }

    public static String colorName(Color color) {
        return color.name();
    }


    public static String complexInfo(Complex c) {
        return c.getMeta() + ":" + c.getPayload().getName();
    }

    @LLMDescription(description = "Adds two numbers")
    public static int describedAdd(int a, int b) {
        return a + b;
    }

    public static String testNestedEnum(NestedEnumPayload p) {
        return p.getOuter().name() + ":" + p.getInner().name();
    }

    public static String testEnumList(EnumListPayload p) {
        return p.getEnums().stream().map(Enum::name).collect(Collectors.joining(","));
    }

    public static String testListOfLists(List<List<String>> list) {
        return list.stream()
            .map(inner -> String.join("-", inner))
            .collect(Collectors.joining("|"));
    }
}

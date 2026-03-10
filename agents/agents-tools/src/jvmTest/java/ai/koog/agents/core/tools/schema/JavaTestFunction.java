package ai.koog.agents.core.tools.schema;

import ai.koog.agents.core.tools.annotations.LLMDescription;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Used to test schema generation from Java functions.
 */
public class JavaTestFunction {
    @LLMDescription(description = "A test class")
    public static class TestClass {
        public TestClass(
            @LLMDescription(description = "A string property")
            String stringProperty,
            int intProperty,
            long longProperty,
            double doubleProperty,
            float floatProperty,
            Boolean booleanNullableProperty,
            String nullableProperty,
            List<String> listProperty,
            Map<String, Integer> mapProperty,
            @LLMDescription(description = "A custom nested property")
            NestedProperty nestedProperty,
            List<NestedProperty> nestedListProperty,
            Map<String, NestedProperty> nestedMapProperty,
            TestEnum enumProperty
        ) {

        }
    }

    @LLMDescription(description = "Nested property class")
    public static class NestedProperty {
        public NestedProperty(
            @LLMDescription(description = "Nested foo property")
            String foo,
            int bar
        ) {

        }
    }

    public enum TestEnum {
        One,
        Two
    }

    @LLMDescription(description = "Sample function")
    public static String sampleFunction(
        @LLMDescription(description = "Sample parameter")
        String a,
        @LLMDescription(description = "Another sample parameter")
        TestClass b
    ) {
        return "";
    }

    /**
     * Java reflection method representing sampleFunction.
     * Used for schema generation.
     */
    public static Method FUNCTION;

    static {
        try {
            FUNCTION = JavaTestFunction.class.getDeclaredMethod("sampleFunction", String.class, TestClass.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}

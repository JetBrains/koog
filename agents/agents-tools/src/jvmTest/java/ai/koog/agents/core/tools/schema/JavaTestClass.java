package ai.koog.agents.core.tools.schema;

import ai.koog.agents.core.tools.annotations.LLMDescription;

import java.util.List;
import java.util.Map;

/**
 * Used to test schema generation from Java classes.
 */
public class JavaTestClass {
    @LLMDescription("A test class")
    public static class TestClass {
        public TestClass(
            @LLMDescription("A string property")
            String stringProperty,
            int intProperty,
            long longProperty,
            double doubleProperty,
            float floatProperty,
            Boolean booleanNullableProperty,
            String nullableProperty,
            List<String> listProperty,
            Map<String, Integer> mapProperty,
            @LLMDescription("A custom nested property")
            NestedProperty nestedProperty,
            List<NestedProperty> nestedListProperty,
            Map<String, NestedProperty> nestedMapProperty,
            TestEnum enumProperty
        ) {

        }
    }

    @LLMDescription("Nested property class")
    public static class NestedProperty {
        public NestedProperty(
            @LLMDescription("Nested foo property")
            String foo,
            int bar
        ) {

        }
    }

    public enum TestEnum {
        One,
        Two
    }

    /**
     * Java reflection class representing this class.
     * Used for schema generation.
     */
    public static final Class<TestClass> TEST_CLASS = TestClass.class;
}

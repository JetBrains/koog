package ai.koog.serialization;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class JavaTypeTokenTest {
    static class Person {
        public final String name;
        public final int age;

        public Person(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    static class GenericClass<T> {
        public final T value;

        public GenericClass(T value) {
            this.value = value;
        }
    }

    @Test
    public void testJavaTypeTokenCreatedSuccessfully() throws NoSuchFieldException {
        JavaClassToken typeToken = TypeToken.of(Person.class);

        Class<?> clazz = typeToken.getKlass();
        Field[] fields = clazz.getFields();

        assertEquals(2, fields.length);

        Field nameField = clazz.getField("name");
        assertEquals(String.class, nameField.getType());

        Field ageField = clazz.getField("age");
        assertEquals(int.class, ageField.getType());
    }

    @Test
    public void testJavaTypeTokenCapturesGenericsSuccessfully() throws NoSuchFieldException {
        JavaTypeToken typeToken = TypeToken.of(new TypeCapture<GenericClass<String>>() {});

        ParameterizedType parameterizedType = (ParameterizedType) typeToken.getType();
        Class<?> clazz = (Class<?>) parameterizedType.getRawType();

        // Only a single generic parameter of type String is captured
        Type[] typeArguments = parameterizedType.getActualTypeArguments();
        assertEquals(1, typeArguments.length);
        assertEquals(String.class, typeArguments[0]);

        // Generic class parameter and generic field type name match
        Field valueField = clazz.getField("value");
        assertEquals("T", clazz.getTypeParameters()[0].getName());
        assertEquals("T", valueField.getGenericType().getTypeName());
    }
}

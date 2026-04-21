package ai.koog.serialization.jackson;

import ai.koog.serialization.TypeCapture;
import ai.koog.serialization.TypeToken;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JavaJacksonSerializerTest {
    private final JacksonSerializer serializer = new JacksonSerializer();

    public static class Person<T> {
        @JsonProperty("no_name") final String name;
        @JsonProperty("__age__") final int age;
        @JsonProperty final List<T> hobbies;

        @JsonCreator
        public Person(
            @JsonProperty("no_name") String name,
            @JsonProperty("__age__") int age,
            @JsonProperty("hobbies") List<T> hobbies
        ) {
            this.name = name;
            this.age = age;
            this.hobbies = hobbies;
        }

        @Override
        public String toString() {
            return "Person{" +
                "name='" + name + '\'' +
                ", age=" + age +
                ", hobbies=" + hobbies +
                '}';
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Person)) return false;
            Person<?> person = (Person<?>) o;
            return age == person.age && Objects.equals(name, person.name) && Objects.equals(hobbies, person.hobbies);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, age, hobbies);
        }
    }

    public static class Hobby {
        @JsonProperty("name") final String name;

        @JsonCreator
        public Hobby(@JsonProperty("name") String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "Hobby{" +
                "name='" + name + '\'' +
                '}';
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Hobby)) return false;
            Hobby hobby = (Hobby) o;
            return Objects.equals(name, hobby.name);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }
    }

    @Test
    void testPrimitiveSerialization() {
        var typeToken = TypeToken.of(String.class);

        assertEquals(
            "\"test\"",
            serializer.encodeToString("test", typeToken)
        );
    }

    @Test
    void testObjectSerialization() {
        var typeToken = TypeToken.of(new TypeCapture<Person<Hobby>>() {});
        var person = new Person<>("John", 30, List.of(new Hobby("Reading"), new Hobby("Coding")));
        // language=JSON
        var personJson = "{\"no_name\":\"John\",\"__age__\":30,\"hobbies\":[{\"name\":\"Reading\"},{\"name\":\"Coding\"}]}";

        var actualPersonJson = serializer.encodeToString(person, typeToken);
        assertEquals(personJson, actualPersonJson);

        var actualPerson = serializer.decodeFromString(personJson, typeToken);
        assertEquals(person, actualPerson);
    }
}

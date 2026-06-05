---
search:
exclude: true
---

# --8<-- [start:prerequisites]
Ensure your environment and project meet the following requirements:

- JDK 17+
- Kotlin 2.2.0+
- Gradle 8.0+ or Maven 3.8+
# --8<-- [end:prerequisites]

# --8<-- [start:dependencies]
Add the [Koog package](https://central.sonatype.com/artifact/ai.koog/koog-agents/) as a dependency:

=== "Gradle (Kotlin)"

    ``` kotlin title="build.gradle.kts"
    dependencies {
        // Stable
        implementation("ai.koog:koog-agents:1.0.0")

        // Beta
        implementation("ai.koog:koog-agents-additions:1.0.0-beta")
    }
    ```

=== "Gradle (Groovy)"

    ``` groovy title="build.gradle"
    dependencies {
        // Stable
        implementation 'ai.koog:koog-agents:1.0.0'

        // Beta
        implementation 'ai.koog:koog-agents-additions:1.0.0-beta'
    }
    ```

=== "Maven"

    ```xml title="pom.xml"
    <dependency>
        <!-- Stable -->
        <dependency>
            <groupId>ai.koog</groupId>
            <artifactId>koog-agents-jvm</artifactId>
            <version>1.0.0</version>
        </dependency>

        <!-- Beta -->
        <dependency>
            <groupId>ai.koog</groupId>
            <artifactId>koog-agents-additions-jvm</artifactId>
            <version>1.0.0-beta</version>
        </dependency>
    </dependency>
    ```
# --8<-- [end:dependencies]

# --8<-- [start:api-key]
Get an API key from an LLM provider or run a local LLM via Ollama.
For more information, see [Quickstart](../quickstart.md).
# --8<-- [end:api-key]


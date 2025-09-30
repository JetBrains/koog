# Koog Documentation

This module contains documentation for the Koog framework, including user guides, API references, prompting guidelines, and other static files.

## Module Structure

The docs module is organized as follows:

| Folder         | Description                                                                          |
|----------------|--------------------------------------------------------------------------------------|
| **docs/**      | Contains Markdown files with user documentation.                                     |
| **overrides/** | Contains custom overrides for the MkDocs theme.                                      |
| **prompt/**    | Prompting guidelines with extensions for popular modules.                            |
| **src/**       | Knit generated source code from documentation code snippets, should not be commited. |


## Local Development

To run the documentation website locally, you need to have [uv](https://docs.astral.sh/uv/getting-started/installation/) installed.

1. Sync the project (this will create proper .venv and install dependencies, no manual Python setup required):
   ```bash
   uv sync --frozen --all-extras
   ```

2. Start the local documentation server:
   ```bash
   uv run mkdocs serve
   ```

The documentation will be available at the URL printed in the output and will automatically reload when you make changes to the documentation files.

## Documentation System

### MkDocs

The documentation is built using [MkDocs](https://www.mkdocs.org/) with the Material theme. The configuration is defined in `mkdocs.yml`, which specifies:

- Navigation structure
- Theme configuration
- Markdown extensions
- Repository links

The documentation is available at [https://docs.koog.ai/](https://docs.koog.ai/).

### Docs Code Snippets Verification

To ensure code snippets in documentation are compilable and up-to-date with the latest framework version, the [kotlinx-knit](https://github.com/Kotlin/kotlinx-knit) library is used.

Knit provides a Gradle plugin that extracts specially annotated Kotlin code snippets from Markdown files and generates Kotlin source files.

#### How to fix docs?

1. Run `:docs:knitAssemble` task to clean old knit-generated files, extract fresh code snippets to **/src/main/kotlin**, and assemble the docs project:
    ```
    ./gradlew :docs:knitAssemble
    ```
2. Navigate to the file with the compilation error `example-[md-file-name]-[index].kt`.
3. Fix the error in this file.
4. Navigate to the code snippet in Markdown `md-file-name.md` by searching `<!--- KNIT example-[md-file-name]-[index].kt` -->`.
5. Update the code snippet to reflect the changes in ***.kt** file:
   * Update dependencies (usually they are provided in the `<!--- INCLUDE -->` section).
   * Edit code (do not forget about tabulation when you just copy and paste from the ***.kt** file).

#### How to annotate docs?

To annotate new Kotlin code snippets in Markdown and make them compilable:

1. Put an example annotation comment (`<!--- KNIT example-[md-file-name]-01.kt -->`) after every code block. 
You do not need to put correct indexes, just set the `01` for each example,
   and they will be updated automatically after the first knit run.
    ```
        ```kotlin
        val agent = AIAgent(...)
        ```
        <!--- KNIT example-[md-file-name]-01.kt -->
    ```
2. If you need some imports, add the include comment `<!--- INCLIDE ... -->` before the code block:
    ```
        <!--- INCLUDE
        import ai.koog.agents.core.agent.AIAgent
        -->
        ```kotlin
        val agent = AIAgent(...)
        ```
        <!--- KNIT example-[md-file-name]-01.kt -->
    ```
3. If you need to wrap your code with `main` or other functions, 
use the include comment `<!--- INCLIDE ... -->` for prefix, and the suffix comment `<!--- SUFFIX ... -->` for suffix:
    ```
        <!--- INCLUDE
        import ai.koog.agents.core.agent.AIAgent
        fun main() {
        -->
        <!--- SUFFIX
        }
        -->
        ```kotlin
        val agent = AIAgent(...)
        ```
        <!--- KNIT example-[md-file-name]-01.kt -->
    ```

For more information, follow the examples in the [kotlinx-knit](https://github.com/Kotlin/kotlinx-knit) repository or refer to already annotated code snippets in the documentation.

> [!NOTE]
> If your documentation contains instructions with code snippets,
> use manual numbering (for example, `1) 2) 3)`) instead of Markdown built-in numbered lists.
> This ensures compatibility with the KNIT tool, as KNIT annotations must remain unindented (starting at column 0) and cannot be nested within numbered Markdown lists.
>
> Here is an example:
> ``````markdown
> 1) Step description for the first acti
> on:
> 
> <!--- INCLUDE
> import ai.koog.agents.core.agent.AIAgent -->
> ```kotlin
> // Code snippet
> ```
> <!--- KNIT example-[md-file-name]-01.kt -->
> 
> 2) Step description for the second action:
> 
> <!--- INCLUDE
> import ai.koog.agents.core.agent.AIAgent
> fun main() {
> -->
> <!--- SUFFIX
> }
> -->
> ```kotlin
> // Another code snippet
> ```
> <!--- KNIT example-[md-file-name]-02.kt -->
> ``````

### API Documentation

API reference documentation is generated using [Dokka](https://github.com/Kotlin/dokka), a documentation engine for Kotlin. The API documentation is built with:

```
./gradlew dokkaGenerate
```

The generated API documentation is deployed to [https://api.koog.ai/](https://api.koog.ai/).

## Prompts

In the [prompt](./prompt) directory, prompting guidelines with extensions for popular modules are stored. These guidelines help users create effective prompts for different LLM models and use cases.

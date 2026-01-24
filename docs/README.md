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

### Content Reuse

The Koog documentation sources include content that is reused in multiple places in published documentation. For
example, in the **Provider-specific parameters** section in `llm-parameters.md`, each table row for each provider is a
reusable content snippet that looks similar to the following one: 

```text
--8<--
llm-parameters-snippets.md:heading
llm-parameters-snippets.md:topP
llm-parameters-snippets.md:topK
llm-parameters-snippets.md:thinkingConfig
--8<--
```

To create or update reusable content, follow the steps below:

1. Create a new Markdown file in the `docs/snippets` directory. For updates to existing files, navigate to the file
that includes the content snippet you want to update.
2. Add or edit the content snippet. A snippet that includes a block of content has the following structure:
    ```text
    # --8<-- [start:snippetName]
    Snippet content. Can also include **Markdown formatting**.
    # --8<-- [end:snippetName]
    ```

   > [!NOTE]
   > As this type of content is usually reused in multiple places, note that updates to existing snippets will be
   > reflected in all places where they are used. Before making updates, ensure that the changes are appropriate for
   > all uses of the snippet.
       
3. Reference the content snippet in the documentation source file where you want to use it:
    ```text
    --8<--
    snippet-file-name.md:snippetName
    snippet-file-name.md:otherSnippetName
    --8<--
    ```
   
    When including a single snippet, you can also use the single-line syntax:

    ```text
    --8<-- "snippet-file-name.md:snippetName"
    ```

The content reuse feature is powered by the `pymdownx.snippets` extension, which is enabled and configured in
`mkdocs.yml` under `plugins`. For more information, see the extension [documentation](https://facelessuser.github.io/pymdown-extensions/extensions/snippets/).

### Docs Code Samples Verification

To ensure code samples in documentation are compilable and up-to-date with the latest framework version, the [kotlinx-knit](https://github.com/Kotlin/kotlinx-knit) library is used.

Knit provides a Gradle plugin that extracts specially annotated Kotlin code samples from Markdown files and generates Kotlin source files.

#### How to fix code samples in the docs?

1. Run `:docs:knitAssemble` task to clean old knit-generated files, extract fresh code samples to **/src/main/kotlin**, and assemble the docs project:
    ```
    ./gradlew :docs:knitAssemble
    ```
2. Navigate to the file with the compilation error `example-[md-file-name]-[index].kt`.
3. Fix the error in this file.
4. Navigate to the code sample in Markdown `md-file-name.md` by searching `<!--- KNIT example-[md-file-name]-[index].kt` -->`.
5. Update the code sample to reflect the changes in ***.kt** file:
   * Update dependencies (usually they are provided in the `<!--- INCLUDE -->` section).
   * Edit code (do not forget about tabulation when you just copy and paste from the ***.kt** file).

#### How to annotate code samples in the docs?

To annotate new Kotlin code samples in Markdown and make them compilable:

1. Put an example annotation comment (`<!--- KNIT example-[md-file-name]-01.kt -->`) after every code block. 
You do not need to put correct indexes, just set the `01` for each example,
   and they will be updated automatically after the first knit run.
    ```
        ```kotlin
        val agent = AIAgent(...)
        ```
        <!--- KNIT example-[md-file-name]-01.kt -->
    ```
2. If you need some imports, add the include comment `<!--- INCLUDE ... -->` before the code block:
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
use the include comment `<!--- INCLUDE ... -->` for prefix, and the suffix comment `<!--- SUFFIX ... -->` for suffix:
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

For more information, follow the examples in the [kotlinx-knit](https://github.com/Kotlin/kotlinx-knit) repository or refer to already annotated code samples in the documentation.

> [!NOTE]
> If your documentation contains instructions with code samples,
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

# Koog Documentation

This module contains documentation for the Koog framework, including user guides, API references, prompting guidelines, and other static files.

## Module Structure

The docs module is organized as follows:

- **docs/** - Contains markdown files with user documentation
- **overrides/** - Custom overrides for the MkDocs theme
- **prompt/** - Prompting guidelines with extensions for popular modules
- **src/** - Knit generated source code from documentation code snippets, should not be commited

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

Knit provides a Gradle plugin that extracts specially annotated Kotlin code snippets from markdown files and generates Kotlin source files.

**How to fix docs**:
1. Run knit to extract code snippets to /src/main/kotlin:
```
./gradlew :docs:knit
```
2. Run assemble to get compilation arrows:
```
./gradlew :docs:assemble
```
3. Navigate to the file with the compilation error `example-[md-file-name]-[index].kt`
4. Fix the error in this file
5. Navigate to the code snippet in Markdown `md-file-name.md` by searing `<!--- KNIT example-[md-file-name]-[index].kt` -->`
6. Update code snippet to reflect the changes in kt file
   * Update dependencies (usually they are provided in `<!--- INCLUDE -->` section)
   * Edit code (don't forget about tabulation when you just copy paste from kt)

#### Knit Configuration

The knit configuration is defined in:

1. **build.gradle.kts** - Configures the knit plugin, specifying which files to process
2. **knit.properties** - Defines the package name (`ai.koog.agents.example`) and output directory (`src/main/kotlin`) for generated code

To annotate Kotlin code snippets in markdown, follow the examples in the [kotlinx-knit](https://github.com/Kotlin/kotlinx-knit) repository or refer to already annotated code snippets in the documentation.

### API Documentation

API reference documentation is generated using [Dokka](https://github.com/Kotlin/dokka), a documentation engine for Kotlin. The API documentation is built with:

```
./gradlew dokkaGenerate
```

The generated API documentation is deployed to [https://api.koog.ai/](https://api.koog.ai/).

## Prompts

In the [prompt](./prompt) directory, prompting guidelines with extensions for popular modules are stored. These guidelines help users create effective prompts for different LLM models and use cases.

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
To extract code snippets, run:
```
./gradlew :docs:knit
```

To verify that all extracted files are compilable, run:
```
./gradlew :docs:build
```

**NB**: Before every release follow these actions:
1. Build pre-release koog version x.x.x-demo
2. Update koog version in [libs.versions.toml](gradle/libs.versions.toml) (add local repo if needed)
3. Run knit locally and fix code snippets if compilation fails
4. Commit fixed documentation

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

package ai.grazie.code.agents.example.memory.tools

import ai.grazie.code.agents.core.tools.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Abstract base class for code analysis tools.
 * This tool provides capabilities for:
 * - Parsing build files (Gradle, Maven, NPM, etc.)
 * - Analyzing code style configurations
 * - Extracting project structure information
 */
abstract class CodeAnalysisTool : SimpleTool<CodeAnalysisTool.Args>() {
    @Serializable
    data class Args(
        @SerialName("analysis_type")
        val analysisType: String,
        @SerialName("file_path")
        val filePath: String,
        @SerialName("base_dir")
        val baseDir: String = "."
    ) : Tool.Args

    override val argsSerializer = Args.serializer()

    override val descriptor = ToolDescriptor(
        name = "code-analysis",
        description = """
            Analyzes project code structure, dependencies, and style.
            Analysis types:
            - dependencies (Parse build files for dependencies)
            - style (Analyze code style configurations)
            - structure (Extract project structure information)

            The tool supports:
            - Gradle build files (build.gradle.kts)
            - Maven POM files (pom.xml)
            - NPM package.json files
            - Python requirements.txt/pyproject.toml
            - Cargo.toml (Rust)
            - CMakeLists.txt (C/C++)
            - EditorConfig files (.editorconfig)
            - IDE style configurations (.idea/codeStyles/*.xml)
        """.trimIndent(),
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "analysis_type",
                description = "Type of analysis to perform",
                type = ToolParameterType.String
            ),
            ToolParameterDescriptor(
                name = "file_path",
                description = "Path to the file to analyze",
                type = ToolParameterType.String
            ),
            ToolParameterDescriptor(
                name = "base_dir",
                description = "Base directory for analysis",
                type = ToolParameterType.String
            )
        )
    )
}

/**
 * Implementation of CodeAnalysisTool that analyzes project code structure, dependencies, and style.
 * Supports multiple build systems and languages including:
 * - JVM: Gradle, Maven
 * - JavaScript/TypeScript: NPM, Yarn
 * - Python: pip, Poetry
 * - Rust: Cargo
 * - C/C++: CMake
 */
class CodeAnalysisToolImpl : CodeAnalysisTool() {
    override suspend fun doExecute(args: Args): String {
        val baseDir = Path.of(args.baseDir).toAbsolutePath().normalize()
        val filePath = baseDir.resolve(args.filePath).normalize()

        // Validate path is within base directory
        if (!filePath.startsWith(baseDir)) {
            return "Error: File path must be within base directory"
        }

        if (!filePath.exists()) {
            return "Error: File does not exist: $filePath"
        }

        return when (args.analysisType) {
            "dependencies" -> analyzeDependencies(filePath)
            "style" -> analyzeCodeStyle(filePath)
            "structure" -> analyzeProjectStructure(filePath)
            else -> "Error: Unknown analysis type: ${args.analysisType}"
        }
    }

    private fun analyzeDependencies(filePath: Path): String {
        val content = filePath.readText()
        return when {
            filePath.toString().endsWith(".gradle.kts") || filePath.toString().endsWith(".gradle") ->
                analyzeGradleDependencies(content)
            filePath.toString().endsWith(".xml") && filePath.toString().contains("pom") ->
                analyzeMavenDependencies(content)
            filePath.toString().endsWith("package.json") ->
                analyzeNpmDependencies(content)
            filePath.toString().endsWith("requirements.txt") || filePath.toString().endsWith("pyproject.toml") ->
                analyzePythonDependencies(content)
            filePath.toString().endsWith("Cargo.toml") ->
                analyzeRustDependencies(content)
            filePath.toString().endsWith("CMakeLists.txt") ->
                analyzeCMakeDependencies(content)
            else -> "Error: Unsupported build file type: $filePath"
        }
    }

    private fun analyzeGradleDependencies(content: String): String {
        // Simple regex-based analysis for demonstration
        val dependencies = content.lines()
            .filter { it.contains("implementation") || it.contains("api") || it.contains("testImplementation") }
            .joinToString("\n") { it.trim() }

        return if (dependencies.isNotEmpty()) {
            "Found dependencies in Gradle file:\n$dependencies"
        } else {
            "No dependencies found in Gradle file"
        }
    }

    private fun analyzeMavenDependencies(content: String): String {
        // Simple regex-based analysis for demonstration
        val dependencies = content.lines()
            .filter { it.contains("<dependency>") || it.contains("<artifactId>") || it.contains("<version>") }
            .joinToString("\n") { it.trim() }

        return if (dependencies.isNotEmpty()) {
            "Found dependencies in Maven file:\n$dependencies"
        } else {
            "No dependencies found in Maven file"
        }
    }

    private fun analyzeNpmDependencies(content: String): String {
        // Simple check for dependencies and devDependencies sections
        val dependenciesPattern = "\"dependencies\"\\s*:\\s*\\{([^}]*)\\}".toRegex()
        val devDependenciesPattern = "\"devDependencies\"\\s*:\\s*\\{([^}]*)\\}".toRegex()

        val dependencies = dependenciesPattern.find(content)?.groupValues?.getOrNull(1)
        val devDependencies = devDependenciesPattern.find(content)?.groupValues?.getOrNull(1)

        val result = StringBuilder()
        if (!dependencies.isNullOrBlank()) {
            result.append("Found dependencies in package.json:\n$dependencies\n")
        }
        if (!devDependencies.isNullOrBlank()) {
            result.append("Found dev dependencies in package.json:\n$devDependencies")
        }

        return if (result.isNotEmpty()) {
            result.toString()
        } else {
            "No dependencies found in package.json"
        }
    }

    private fun analyzePythonDependencies(content: String): String {
        // Handle both requirements.txt and pyproject.toml
        return if (content.contains("[tool.poetry]") || content.contains("[project]")) {
            // pyproject.toml format
            val dependencies = content.lines()
                .filter { it.contains("=") && !it.contains("[") }
                .joinToString("\n") { it.trim() }

            if (dependencies.isNotEmpty()) {
                "Found dependencies in pyproject.toml:\n$dependencies"
            } else {
                "No dependencies found in pyproject.toml"
            }
        } else {
            // requirements.txt format
            val dependencies = content.lines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .joinToString("\n") { it.trim() }

            if (dependencies.isNotEmpty()) {
                "Found dependencies in requirements.txt:\n$dependencies"
            } else {
                "No dependencies found in requirements.txt"
            }
        }
    }

    private fun analyzeRustDependencies(content: String): String {
        // Simple check for [dependencies] section in Cargo.toml
        val dependenciesSection = content.indexOf("[dependencies]")
        if (dependenciesSection >= 0) {
            val startIndex = dependenciesSection + "[dependencies]".length
            val nextSection = content.indexOf('[', startIndex)
            val endIndex = if (nextSection > 0) nextSection else content.length

            val dependencies = content.substring(startIndex, endIndex).trim()
            return if (dependencies.isNotEmpty()) {
                "Found dependencies in Cargo.toml:\n$dependencies"
            } else {
                "No dependencies found in Cargo.toml"
            }
        }
        return "No dependencies section found in Cargo.toml"
    }

    private fun analyzeCMakeDependencies(content: String): String {
        // Look for find_package, target_link_libraries, etc.
        val dependencies = content.lines()
            .filter {
                it.contains("find_package", ignoreCase = true) ||
                        it.contains("target_link_libraries", ignoreCase = true) ||
                        it.contains("include_directories", ignoreCase = true)
            }
            .joinToString("\n") { it.trim() }

        return if (dependencies.isNotEmpty()) {
            "Found dependencies in CMakeLists.txt:\n$dependencies"
        } else {
            "No dependencies found in CMakeLists.txt"
        }
    }

    private fun analyzeCodeStyle(filePath: Path): String {
        val content = filePath.readText()
        return when {
            filePath.toString().endsWith(".editorconfig") -> analyzeEditorConfig(content)
            filePath.toString().endsWith(".xml") -> analyzeIdeaCodeStyle(content)
            filePath.toString().contains(".eslintrc") -> analyzeEslintConfig(content)
            filePath.toString().contains(".prettierrc") -> analyzePrettierConfig(content)
            filePath.toString().contains("pyproject.toml") && content.contains("[tool.black]") ->
                analyzeBlackConfig(content)
            else -> "Error: Unsupported code style file type: $filePath"
        }
    }

    private fun analyzeEditorConfig(content: String): String {
        val rules = content.lines()
            .filter { it.contains("=") || it.startsWith("[") }.joinToString("\n") { it.trim() }

        return if (rules.isNotEmpty()) {
            "Found code style rules in EditorConfig:\n$rules"
        } else {
            "No code style rules found in EditorConfig"
        }
    }

    private fun analyzeIdeaCodeStyle(content: String): String {
        val rules = content.lines()
            .filter { it.contains("option name") || it.contains("value=") }.joinToString("\n") { it.trim() }

        return if (rules.isNotEmpty()) {
            "Found IDE code style rules:\n$rules"
        } else {
            "No code style rules found in IDE configuration"
        }
    }

    private fun analyzeEslintConfig(content: String): String {
        val rules = if (content.contains("\"rules\"")) {
            val rulesStart = content.indexOf("\"rules\"")
            val rulesSection = content.substring(rulesStart, content.length)
            rulesSection.substring(0, rulesSection.indexOf('}') + 1)
        } else {
            ""
        }

        return if (rules.isNotEmpty()) {
            "Found ESLint rules:\n$rules"
        } else {
            "No ESLint rules found"
        }
    }

    private fun analyzePrettierConfig(content: String): String {
        return "Found Prettier configuration:\n$content"
    }

    private fun analyzeBlackConfig(content: String): String {
        val blackSection = content.indexOf("[tool.black]")
        if (blackSection >= 0) {
            val startIndex = blackSection + "[tool.black]".length
            val nextSection = content.indexOf('[', startIndex)
            val endIndex = if (nextSection > 0) nextSection else content.length

            val config = content.substring(startIndex, endIndex).trim()
            return "Found Black formatter configuration:\n$config"
        }
        return "No Black formatter configuration found"
    }

    private fun analyzeProjectStructure(filePath: Path): String {
        val content = filePath.readText()
        return when {
            filePath.toString().endsWith(".gradle.kts") || filePath.toString().endsWith(".gradle") ->
                analyzeGradleStructure(content)
            filePath.toString().endsWith(".xml") && filePath.toString().contains("pom") ->
                analyzeMavenStructure(content)
            filePath.toString().endsWith("package.json") ->
                analyzeNpmStructure(content)
            filePath.toString().endsWith("pyproject.toml") ->
                analyzePythonStructure(content)
            filePath.toString().endsWith("Cargo.toml") ->
                analyzeRustStructure(content)
            filePath.toString().endsWith("CMakeLists.txt") ->
                analyzeCMakeStructure(content)
            else -> "Error: Unsupported project file type: $filePath"
        }
    }

    private fun analyzeGradleStructure(content: String): String {
        val structure = content.lines()
            .filter { it.contains("project") || it.contains("subprojects") || it.contains("allprojects") }
            .joinToString("\n") { it.trim() }

        return if (structure.isNotEmpty()) {
            "Found project structure in Gradle file:\n$structure"
        } else {
            "No project structure information found in Gradle file"
        }
    }

    private fun analyzeMavenStructure(content: String): String {
        val structure = content.lines()
            .filter { it.contains("<module>") || it.contains("<parent>") }.joinToString("\n") { it.trim() }

        return if (structure.isNotEmpty()) {
            "Found project structure in Maven file:\n$structure"
        } else {
            "No project structure information found in Maven file"
        }
    }

    private fun analyzeNpmStructure(content: String): String {
        // Extract name, version, scripts, etc.
        val namePattern = "\"name\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val versionPattern = "\"version\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val scriptsPattern = "\"scripts\"\\s*:\\s*\\{([^}]*)\\}".toRegex()

        val name = namePattern.find(content)?.groupValues?.getOrNull(1) ?: "Unknown"
        val version = versionPattern.find(content)?.groupValues?.getOrNull(1) ?: "Unknown"
        val scripts = scriptsPattern.find(content)?.groupValues?.getOrNull(1) ?: ""

        return "Project structure in package.json:\nName: $name\nVersion: $version\nScripts: $scripts"
    }

    private fun analyzePythonStructure(content: String): String {
        // Extract project info from pyproject.toml
        val projectSection = content.indexOf("[project]")
        val poetrySection = content.indexOf("[tool.poetry]")

        val startIndex = if (projectSection >= 0) projectSection else poetrySection
        if (startIndex >= 0) {
            val sectionName = if (projectSection >= 0) "[project]" else "[tool.poetry]"
            val nextSection = content.indexOf('[', startIndex + sectionName.length)
            val endIndex = if (nextSection > 0) nextSection else content.length

            val projectInfo = content.substring(startIndex, endIndex).trim()
            return "Found Python project structure:\n$projectInfo"
        }
        return "No project structure information found in Python configuration"
    }

    private fun analyzeRustStructure(content: String): String {
        // Extract package info from Cargo.toml
        val packageSection = content.indexOf("[package]")
        if (packageSection >= 0) {
            val startIndex = packageSection + "[package]".length
            val nextSection = content.indexOf('[', startIndex)
            val endIndex = if (nextSection > 0) nextSection else content.length

            val packageInfo = content.substring(startIndex, endIndex).trim()
            return "Found Rust project structure:\n$packageInfo"
        }
        return "No project structure information found in Cargo.toml"
    }

    private fun analyzeCMakeStructure(content: String): String {
        // Look for project name, version, etc.
        val projectInfo = content.lines()
            .filter {
                it.contains("project(", ignoreCase = true) ||
                        it.contains("cmake_minimum_required", ignoreCase = true) ||
                        it.contains("add_subdirectory", ignoreCase = true)
            }
            .joinToString("\n") { it.trim() }

        return if (projectInfo.isNotEmpty()) {
            "Found C/C++ project structure:\n$projectInfo"
        } else {
            "No project structure information found in CMakeLists.txt"
        }
    }
}

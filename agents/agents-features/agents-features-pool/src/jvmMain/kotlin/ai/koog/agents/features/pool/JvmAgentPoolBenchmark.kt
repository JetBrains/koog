package ai.koog.agents.features.pool

import ai.koog.agents.features.pool.benchmark.AgentPoolBenchmark
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * JVM-specific implementation of memory tracking and Python graph export for benchmarks
 */
public object JvmAgentPoolBenchmark {

    /**
     * Print detailed memory statistics on JVM
     */
    public fun printMemoryStats(label: String) {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val maxMemory = runtime.maxMemory()

        println("💾 Memory Stats - $label:")
        println("  Used: ${usedMemory / 1024 / 1024} MB")
        println("  Free: ${freeMemory / 1024 / 1024} MB")
        println("  Total: ${totalMemory / 1024 / 1024} MB")
        println("  Max: ${maxMemory / 1024 / 1024} MB")
        println()
    }

    /**
     * Force garbage collection and print stats
     */
    public fun forceGCAndPrintStats(label: String) {
        System.gc()
        Thread.sleep(100) // Give GC time to run
        printMemoryStats(label)
    }

    /**
     * Run Python script to generate graphs from benchmark data using uv
     */
    public fun generateGraphs(jsonFilePath: String, pythonScript: String = "scripts/plot_benchmarks.py", outputDir: String = ""): Boolean {
        return try {
            println("🐍 Generating graphs via Python with uv...")

            // Ensure the output goes to the correct directory
            val targetOutputDir = if (outputDir.isNotEmpty()) outputDir else "src/main/kotlin/ai/koog/agents/example/pool/benchmarks"
            
            // Find the correct script path relative to current working directory
            val scriptPath = if (File("scripts/$pythonScript").exists()) {
                "scripts/$pythonScript"
            } else if (File("../$pythonScript").exists()) {
                "../$pythonScript"
            } else {
                pythonScript
            }
            
            // Find the project root (where the scripts directory is)
            val projectRoot = File(System.getProperty("user.dir")).let { workingDir ->
                if (File(workingDir, "scripts").exists()) workingDir
                else workingDir.parentFile  // Go up one level if in examples directory
            }
            
            val commands = listOf(
                // Try uv first - run from scripts directory to access pyproject.toml
                listOf("bash", "-c", "cd ${projectRoot.absolutePath}/scripts && uv run python plot_benchmarks.py ${File(jsonFilePath).absolutePath} --output-dir ${File(targetOutputDir).absolutePath}"),
                // Fallback to python3
                listOf("python3", scriptPath, jsonFilePath, "--output-dir", targetOutputDir)
            )

            for ((index, command) in commands.withIndex()) {
                try {
                    println("   Command: ${command.joinToString(" ")}")

                    val process = ProcessBuilder(command)
                        .redirectErrorStream(false)
                        .start()

                    // Capture output and errors
                    val stdout = StringBuilder()
                    val stderr = StringBuilder()
                    
                    // Read stdout
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { 
                            println("   $it")
                            stdout.appendLine(it)
                        }
                    }
                    
                    // Read stderr after stdout is complete
                    process.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { 
                            println("   ERROR: $it")
                            stderr.appendLine(it)
                        }
                    }

                    val exitCode = process.waitFor()
                    if (exitCode == 0) {
                        println("✅ Graph generation completed successfully")
                        return true
                    } else {
                        println("❌ Command failed with exit code: $exitCode")
                        if (stderr.isNotEmpty()) {
                            println("   Error output: ${stderr.toString().trim()}")
                        }
                        if (index < commands.size - 1) {
                            println("   Trying fallback command...")
                        }
                    }
                } catch (e: IOException) {
                    println("❌ Failed to run command: ${e.message}")
                    if (index < commands.size - 1) {
                        println("   Trying fallback command...")
                    }
                }
            }

            println("❌ All graph generation attempts failed")
            println("   Setup: ./scripts/setup_python_graphs.sh")
            false

        } catch (e: Exception) {
            println("❌ Unexpected error during graph generation: ${e.message}")
            false
        }
    }

    /**
     * Complete export workflow: JSON export + Python graph generation
     */
    public fun exportWithGraphs(
        results: List<AgentPoolBenchmark.BenchmarkResult>,
        suffix: String = "",
        outputDir: String = "src/main/kotlin/ai/koog/agents/example/pool/benchmarks",
        pythonScript: String = "scripts/plot_benchmarks.py"
    ): Boolean {
        println("📁 Exporting via JVM auto-export...")
        return try {
            // Create a minimal JSON structure for compatibility
            val timestamp = System.currentTimeMillis() / 1000
            val filenameSuffix = if (suffix.isNotEmpty()) "_$suffix" else ""
            
            // Convert to the Python-compatible export format
            val convertedResults = results.map { result ->
                AgentPoolBenchmark.PythonBenchmarkResult(
                    name = result.implementation,
                    totalRuns = result.totalRequests,
                    successCount = result.successfulRequests,
                    avgLatencyMs = result.avgLatencyMs,
                    p95LatencyMs = result.p95LatencyMs,
                    throughput = result.throughputOpsPerSec,
                    concurrency = 3,
                    peakMemoryMB = result.peakMemoryMB,
                    avgMemoryMB = result.avgMemoryMB,
                    gcCollections = result.gcCollections,
                    cpuUtilizationPercent = result.cpuUtilizationPercent
                )
            }
            
            val exportData = AgentPoolBenchmark.BenchmarkExportData(
                timestamp = timestamp,
                results = convertedResults,
                metadata = AgentPoolBenchmark.BenchmarkMetadata(
                    platform = "jvm",
                    kotlinVersion = "1.9.22", 
                    benchmarkType = "agentpool-max-throughput", // Always max throughput now
                    poolSize = 3,
                    durationSeconds = 15,
                    targetRps = -1  // -1 indicates unconstrained max throughput
                ),
            )
            
            val json = Json { prettyPrint = true }
            val jsonContent = json.encodeToString(exportData)
            
            // Save and visualize
            val outputDirFile = File(outputDir)
            outputDirFile.mkdirs()
            val jsonFile = File(outputDirFile, "agentpool_benchmark${filenameSuffix}_$timestamp.json")
            jsonFile.writeText(jsonContent)
            
            println("📁 Saved: ${jsonFile.absolutePath}")
            generateGraphs(jsonFile.absolutePath, pythonScript, outputDir)
        } catch (e: Exception) {
            println("❌ Export failed: ${e.message}")
            false
        }
    }
}

/**
 * Extension functions to make JVM memory tracking accessible from common code
 */
public fun AgentPoolBenchmark.printMemoryStatsJvm(label: String) {
    JvmAgentPoolBenchmark.printMemoryStats(label)
}

public fun AgentPoolBenchmark.forceGCAndPrintStatsJvm(label: String) {
    JvmAgentPoolBenchmark.forceGCAndPrintStats(label)
}

/**
 * JVM-specific auto-export and visualization
 */
public fun AgentPoolBenchmark.tryAutoExportAndVisualizeJvm(jsonContent: String, fileName: String) {
    try {
        // Create output directory
        val outputDir = File("src/main/kotlin/ai/koog/agents/example/pool/benchmarks")
        outputDir.mkdirs()
        
        // Write JSON file
        val jsonFile = File(outputDir, fileName)
        jsonFile.writeText(jsonContent)
        println("📁 Saved: ${jsonFile.absolutePath}")
        
        // Automatically call Python script
        val success = JvmAgentPoolBenchmark.generateGraphs(
            jsonFilePath = jsonFile.absolutePath,
            outputDir = outputDir.path
        )
        
        if (success) {
            println("✅ Graphs and reports generated automatically!")
            println("📝 View report: ${outputDir.path}/benchmark_report.md")
        } else {
            println("⚠️  Auto-graph generation failed. JSON file saved for manual processing.")
            println("🐍 Manual command: uv run scripts/plot_benchmarks.py ${jsonFile.absolutePath}")
        }
        
    } catch (e: Exception) {
        println("⚠️  Auto-export failed: ${e.message}")
        println("📄 JSON Content:")
        println(jsonContent)
    }
}
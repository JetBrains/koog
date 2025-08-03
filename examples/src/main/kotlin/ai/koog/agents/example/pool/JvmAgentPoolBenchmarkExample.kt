package ai.koog.agents.example.pool

import ai.koog.agents.features.pool.benchmark.AgentPoolBenchmark
import ai.koog.agents.features.pool.JvmAgentPoolBenchmark
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.Json

/**
 * JVM-specific AgentPool benchmark with integrated Python visualization.
 * 
 * This benchmark:
 * - Runs unconstrained max throughput test to show real performance differences
 * - Demonstrates significant throughput improvements with agent pooling
 * - Uses full production-grade agent simulation (45s duration, pool size 20)
 * - Uses ONLY mock executors - no real API keys needed
 * - Automatically saves JSON results to file
 * - Calls Python script to generate graphs and reports
 * - Includes enterprise cost analysis
 * 
 * Run: ./gradlew :examples:runAgentPoolBenchmark
 */
fun main() = runBlocking {
    println("🚀 JVM AgentPool Max Throughput Benchmark - Integrated Visualization")
    println("=" * 80)
    println("Working directory: ${System.getProperty("user.dir")}")
    println("Running unconstrained max throughput test to show real performance differences")
    println("Fully automated: benchmark → JSON export → Python graphs → reports")
    println()
    
    // Run max throughput benchmark with production preset for comprehensive metrics
    println("Using PRODUCTION preset for comprehensive, enterprise-grade metrics...")
    val results = AgentPoolBenchmark.runBenchmark(
        config = AgentPoolBenchmark.BenchmarkConfig.production()
    )
    
    // Alternative presets available:
    // AgentPoolBenchmark.BenchmarkConfig.fast() - Quick development testing (4s)
    // AgentPoolBenchmark.BenchmarkConfig.realistic() - Balanced testing (8s)
    
    println("\n✅ Max throughput AgentPool benchmark completed!")
    
    // Export results
    println("🎨 Automatically generating visualizations...") 
    val success = JvmAgentPoolBenchmark.exportWithGraphs(results)
    
    if (success) {
        println("✅ Complete! All files generated:")
        println("📊 Graphs: examples/src/main/kotlin/ai/koog/agents/example/pool/benchmarks/")
        println("📝 Report: examples/src/main/kotlin/ai/koog/agents/example/pool/benchmarks/benchmark_report.md")
        println("💰 Cost analysis charts included!")
        println("\n🚀 Key finding: Pooled agents should show significantly higher throughput than cold agents!")
    } else {
        println("⚠️ Graph generation failed - check Python/uv setup")
        println("📄 JSON data exported for manual processing")
    }
}

private operator fun String.times(n: Int): String = this.repeat(n)
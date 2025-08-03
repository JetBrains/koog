# AgentPool Benchmark Scripts

This directory contains Python scripts for generating performance graphs from AgentPool benchmarks.

## Files

- `plot_benchmarks.py` - Main graph generation script
- `setup_python_graphs.sh` - Setup script for installing dependencies
- `pyproject.toml` - Python project configuration (managed by uv)
- `uv.lock` - Lock file for reproducible dependencies

## Quick Start

1. **Setup** (one-time):
   ```bash
   ./scripts/setup_python_graphs.sh
   ```

2. **Run benchmark**:
   ```bash
   ./gradlew :examples:runExampleAgentPoolBenchmark
   ```

The benchmark will automatically:
- Run real OpenAI agent performance tests
- Export results to `agents/agents-features/agents-features-pool/benchmarks/`
- Generate graphs using uv-managed Python environment

## Manual Usage

Generate graphs from an existing JSON file:
```bash
uv run python scripts/plot_benchmarks.py path/to/benchmark_results.json
```

## Technology Stack

- **uv**: Fast Python package manager and virtual environment handler
- **matplotlib**: Primary graphing library
- **seaborn**: Statistical visualization
- **pandas**: Data manipulation

## Benefits of uv

- **Fast**: Much faster than pip for dependency resolution
- **Reliable**: Reproducible builds with lock files
- **Isolated**: Automatic virtual environment management
- **Modern**: Rust-based toolchain following latest Python standards
#!/bin/bash

# Setup script for Python graph generation using uv
echo "🐍 Setting up Python environment for AgentPool benchmark graphs..."

# Check if uv is available
if ! command -v uv &> /dev/null; then
    echo "📦 uv not found. Installing uv..."
    if command -v brew &> /dev/null; then
        echo "   Using Homebrew to install uv..."
        brew install uv
    elif command -v curl &> /dev/null; then
        echo "   Using curl installer..."
        curl -LsSf https://astral.sh/uv/install.sh | sh
        export PATH="$HOME/.local/bin:$PATH"
    else
        echo "❌ Could not install uv. Please install it manually:"
        echo "   https://docs.astral.sh/uv/getting-started/installation/"
        exit 1
    fi
fi

echo "✅ uv found: $(uv --version)"

# Create project with dependencies (uv will handle virtual environment)
echo "📦 Setting up Python dependencies with uv..."
cd scripts
uv init --no-readme --name benchmark-graphs 2>/dev/null || true
uv add matplotlib seaborn pandas

echo "✅ Setup complete! You can now run:"
echo "   ./gradlew :examples:runAgentPoolBenchmark"
echo ""
echo "🎨 Graphs will be generated automatically using uv!"

# Make the script executable
chmod +x plot_benchmarks.py

cd ..
echo "🚀 Python environment ready with uv!"
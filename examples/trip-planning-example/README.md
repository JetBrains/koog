# Koog trip planning agent example

An example trip planning agent.
This example illustrates how to build more advanced AI agents that can interact with multiple APIs and handle user interactions.

## Overview

This project demonstrates the capabilities of the Koog framework by implementing a trip planning agent that:
- Interacts with users through natural language conversations
- Integrates with multiple external services (Google Maps, weather API)
- Provides iterative plan suggestions based on user feedback

## Schema
![](./assets/agent_schema.svg)

### Core Components

- **Agent**: The main intelligent agent that orchestrates trip planning using various tools and strategies
- **Tools**: Specialized functions that provide specific capabilities (weather, dates, messaging, planning)
- **API Clients**: Integration layer for external services (Open Meteo for weather data)
- **MCP Integration**: Model Context Protocol integration for Google Maps functionality
- **Multi-LLM Executor**: Supports multiple language model providers

## Prerequisites

- **Docker**: Required for Google Maps MCP server
- **API Keys**: You'll need API keys for:
  - OpenAI API
  - Anthropic API  
  - Google AI (Gemini) API
  - Google Maps API

## Setup and Installation

1. **Clone the repository**
2. **Set up environment variables**:<br>
  For example, from the terminal:
   ```bash
   export OPENAI_API_KEY="your-openai-api-key"
   export ANTHROPIC_API_KEY="your-anthropic-api-key"
   export GOOGLE_AI_API_KEY="your-gemini-api-key"
   export GOOGLE_MAPS_API_KEY="your-google-maps-api-key"
   ```

3. **Ensure Docker is running** (required for Google Maps integration)

4. **Build and run the application**:
   ```bash
   ./gradlew run
   ```

# Koog x ACP: Connect your agent to the IDE and more

This repository contains the example on how to create ACP-compatible agent on Koog and connect it to IDE.

## Project Structure

This project is structured as follows:

- [src/main/kotlin/ai/coding/agent](src/main/kotlin/ai/coding/agent): Core ACP agent implementation using the Koog framework:
  - `AcpAgent.kt`: Entry point for the ACP-compatible agent that initializes stdio transport and connects to IntelliJ IDE or other ACP clients.
  - `KoogAgentSupport.kt`: Implements `AgentSupport` interface to handle agent initialization, session management, and prompt execution with LLM integration.
  - `KoogTools.kt`: Defines available tools (file operations, directory listing) that the agent can use during task execution.

- [src/main/kotlin/ai/coding/app](src/main/kotlin/ai/coding/app): Standalone applications demonstrating different ACP client-agent communication patterns:
  - `PipeApp.kt`: Terminal-based client using pipe transport for bidirectional communication with the agent.
  - `ProcessApp.kt`: Terminal-based client that spawns the agent as a subprocess and communicates via stdio.

- [src/main/kotlin/ai/coding/client](src/main/kotlin/ai/coding/client): Terminal client implementation for testing and demonstration:
  - `TerminalClientSupport.kt`: Implements ACP client operations, handles user input from terminal, and displays agent responses.

## What the Agent Can Do

The agent is equipped with the following tools (defined in `KoogTools.kt`):

**1. List Directory (`listDirectory`)**
- Lists files and subdirectories in a given path
- Example: "Show me all Kotlin files in the src directory"

**2. Read File (`readFile`)**
- Reads and displays file contents
- Example: "Read the AcpAgent.kt file and explain what it does"

**3. Edit File (`editFile`)**
- Modifies existing files with precise edits
- Example: "Add a new function to handle user authentication in UserService.kt"


## How to Run

### Run Pipe Application

The PipeApp sets up a connection using the `Pipe` class:
```bash
OPENAI_API_KEY=your_api_key ./gradlew run --args='ai.coding.app.PipeApp'
```

### Run Process Application

Ensure the `AGENT_PATH` is set to point to the agent executable:
```bash
OPENAI_API_KEY=your_api_key AGENT_PATH=path_to_agent_executable ./gradlew run --args='ai.coding.app.ProcessApp'
```

## Connecting ACP Agent to IntelliJ IDE

### What is ACP?

The [Agent Communication Protocol (ACP)](https://www.jetbrains.com/help/idea/acp.html) is a standard protocol that enables seamless integration of AI agents with development environments. It defines a standardized way for agents to communicate with IDEs, providing:

- **Bidirectional communication** via JSON-RPC over stdio
- **Tool execution capabilities** for file operations, code analysis, and more
- **Session management** for maintaining conversation context
- **Event notifications** for real-time updates and progress tracking

### Prerequisites

- **IntelliJ IDEA**: EAP version 2024.3 or newer with ACP support enabled

### Step 1: Build the Application Distribution

To connect the agent to IntelliJ, package it as an executable application:

```bash
./gradlew clean installDist
```

This creates a self-contained distribution in `build/install/acp-agent/` with:
```
build/install/acp-agent/
├── bin/
│   ├── acp-agent         # Unix/Linux/macOS executable
│   └── acp-agent.bat      # Windows executable
└── lib/
    └── *.jar                  # All dependencies
```

**Verify the build:**
```bash
# Unix/Linux/macOS
ls -lh build/install/acp-agent/bin/acp-agent

# Windows
dir build\install\acp-agent\bin\acp-agent.bat
```

### Step 2: Configure ACP Agent in IntelliJ

#### Locate Configuration File

IntelliJ stores ACP agent configurations in a JSON file. To configure your agent:

1. Open **IntelliJ IDEA EAP**
2. Navigate to **AI Chat > Options > Configure ACP Agents** (or **AI Assistant > Settings > ACP Agents**)
<img width="490" height="746" alt="img" src="https://github.com/user-attachments/assets/5a26c5b2-c7a2-486a-956b-7f3e09f0c938" />
  
3. This opens the ACP configuration interface

#### Add Agent Configuration

Add the following JSON configuration:

```json
{
    "agent_servers": {
        "Koog Agent": {
            "command": "/absolute/path/to/acp-agent/build/install/acp-agent/bin/acp-agent",
            "args": [],
            "env": {
                "OPENAI_API_KEY": "sk-your-api-key-here"
            }
        }
    }
}
```

**Configuration Parameters:**
- `agent_servers`: Object containing one or more agent configurations
- `"Koog Agent"`: Display name shown in IntelliJ's agent selector
- `command`: Absolute path to the agent executable
- `args`: Command-line arguments (empty for this agent)
- `env`: Environment variables passed to the agent process
  - `OPENAI_API_KEY`: Your OpenAI API key (required)
  - You can add other variables like `OPENAI_BASE_URL` for custom endpoints

**Platform-Specific Examples:**

**macOS:**
```json
{
    "agent_servers": {
        "Koog Agent": {
            "command": "/Users/username/IdeaProjects/acp-agent/build/install/acp-agent/bin/acp-agent",
            "args": [],
            "env": {
                "OPENAI_API_KEY": "sk-proj-..."
            }
        }
    }
}
```

**Windows:**
```json
{
    "agent_servers": {
        "Koog Agent": {
            "command": "C:\\Users\\username\\IdeaProjects\\acp-agent\\build\\install\\acp-agent\\bin\\acp-agent.bat",
            "args": [],
            "env": {
                "OPENAI_API_KEY": "sk-proj-..."
            }
        }
    }
}
```

### Step 3: Use the Agent in AI Chat

#### Starting a Session

1. Open IntelliJ IDEA
2. Open **AI Chat** (View > Tool Windows > AI Chat or use the toolbar icon)
3. Click the agent selector dropdown
4. Select **"Koog Agent"** from the list
<img width="489" height="749" alt="img_1" src="https://github.com/user-attachments/assets/2b3fbea1-d00f-45d0-8f37-3a392d18357e" />
   
5. A new agent instance will be spawned and connected


#### Example Interactions

**Code Review:**
```
You: Review the KoogAgentSupport.kt file and suggest improvements
Agent: [Reads file, analyzes code, provides suggestions]
```

**Refactoring:**
```
You: Extract the agent initialization logic into a separate function
Agent: [Reads relevant files, makes changes, explains modifications]
```

**Bug Fixing:**
```
You: There's a null pointer exception in the session creation. Can you fix it?
Agent: [Analyzes code, identifies issue, applies fix]
```

**Documentation:**
```
You: Add KDoc comments to all public functions in KoogTools.kt
Agent: [Reads file, adds documentation]
```

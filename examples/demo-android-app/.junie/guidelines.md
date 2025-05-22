# Koog Demo App

## Project Overview
This is a demo android app with Jetpack Compose that demonstrates capabilities of the Kotlin AI agentic framework (Koog).

## Practices
Follow library versions if they are specified in the user prompt, do not downgrade/upgrade by itself.

## Project structure

### Architecture - MVVM Pattern
The project follows the MVVM (Model-View-ViewModel) architecture pattern:

#### ViewModels
- Co-located with their corresponding screens in feature-specific packages within the `screens` directory
- Extend `ViewModel` from Android Architecture Components
- Manage UI state using `StateFlow`
- Expose read-only state to the UI via `StateFlow.asStateFlow()`
- Provide methods to modify the state
- Example: `StartScreenViewModel` in the `screens/start` package manages a counter state and provides a method to increment it

#### Screens (View layer)
- Located in the `screens` package
- Follow a consistent pattern with two main composable functions per screen:
  1. Main screen composable (e.g., `StartScreen`):
     - Connects to the ViewModel
     - Collects state from the ViewModel
     - Passes state and callbacks to the content composable
  2. Content composable (e.g., `StartScreenContent`):
     - Takes data and callbacks as parameters
     - Responsible for the UI layout
     - Doesn't know about the ViewModel directly
     - Can be easily previewed using `@Preview`

This separation allows for better testability, reusability, and a clear separation of concerns.

### Theme
There's a `theme` package that contains all constants and configurations related to styling.
Avoid hardcoding colors/styles/fonts in place, refer to these existing constants and try to use them.

#### Dimensions
Always use values from `AppDimension` instead of hard-coded sizes for:
- Spacing
- Padding
- Margin
- Border radius
- Icon button sizes

The `AppDimension` object provides a consistent size grid system based on Material 3 guidelines:
- Base spacing units (from 4dp to 56dp)
- Specific spacing for common use cases
- Elevation values
- Border radius values
- Icon button sizes (small, medium, large, extra large)

Example usage:

```
// Use this
Modifier
    .padding(AppDimension.spacingMedium)
    .size(AppDimension.iconButtonSizeLarge)
    .clip(RoundedCornerShape(AppDimension.radiusMedium))

// Instead of this
Modifier
    .padding(16.dp)
    .size(48.dp)
    .clip(RoundedCornerShape(8.dp))
```

### Agents
The `agents` package contains agent implementations along with their tools:

#### Agent Structure
- Each agent is organized in its own subdirectory (e.g., `calculator`)
- Agent implementations typically consist of:
  1. Agent provider file (e.g., `CalculatorAgentProvider.kt`):
     - Implements the `AgentProvider` interface
     - Provides factory methods for creating agent instances
     - Defines the agent's title and welcome message
     - Configures the agent with appropriate tools, strategy, and behavior
     - Sets up event handling for agent actions
  2. Tools implementation file (e.g., `CalculatorTools.kt`):
     - Defines the tools that the agent can use
     - Implements the execution logic for each tool
     - Specifies tool parameters and return types

#### AgentProvider Interface
The `AgentProvider` interface serves as a factory for creating agent instances:
- Defines a `provideAgent()` method that creates and configures an agent
- Specifies a `title` property for the agent demo screen
- Specifies a `description` property for the agent description
- Handles events like tool calls, errors, and agent-to-user communication

#### Agent Demo Screen
The project includes a generalized `AgentDemoScreen` for all agent demos:
- Located in the `screens/agentdemo` package
- Provides a consistent UI and interaction pattern for all agent demos
- Displays messages from different sources (user, agent, system, error, tool call, result)
- Handles user input and agent responses
- Works with any agent that is created through an `AgentProvider`

This architecture allows for:
- Clear separation between different agent types and their associated tools
- Consistent UI and interaction patterns across all agent demos
- Easy addition of new agent types without duplicating UI code
- Standardized agent creation and configuration process

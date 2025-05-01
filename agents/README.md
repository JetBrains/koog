# Documentation: Implementing an AI Agent with IdeFormer

This documentation outlines the steps required to implement an arbitrary AI agent using the **IdeFormer** framework.
IdeFormer enables the creation of intelligent agents that can interact with tools,
handle complex workflows, and communicate with external services.

1. [What is IdeFormer](#what-is-ideformer)
2. [Prerequisites](#prerequisites)
3. [Step-by-Step Implementation](#step-by-step-implementation)
4. [Conclusion](#conclusion)

## What is IdeFormer

IdeFormer is a service that encapsulates the logic of agents built
with [LangGraph](https://langchain-ai.github.io/langgraph/) in Python.
Its source code can be found here:
[IdeFormer Source Code](https://jetbrains.team/p/ml-4-se-lab/repositories/ideformer/files/main/cloud/cloud_ideformer).
More detailed guidance on implementing custom agents using LangGraph can be found in
the [knowledge base documentation](https://youtrack.jetbrains.com/articles/JBAI-A-544/How-to-develop-a-new-Agent).

IdeFormer operates as a microservice providing a REST API that manages AI Agent graphs and enables custom definitions.
An AI Agent is represented as a state-machine graph where each node functions as a context-aware unit that takes inputs,
processes them, and modifies the context to produce outputs.  Based on the output, the execution transitions to other
nodes via conditional edges. The graph always begins with a single START node and ends at a FINISH node. Nodes can 
perform a variety of tasks, including arithmetic calculations, API calls, LLM queries, or IDE-specific operations. 
These operations may involve user interaction, such as displaying a UI selector dialog to gather input and passing 
the user's choice back to the LLM.

A wide range of computations, including AI Agents, can be modeled using this approach. IdeFormer also offers pre-defined
graphs for common use cases (and also is welcoming your contributions so that other products may benefit from them!). 
These can be configured with custom prompts and tools from your environment, enabling the intelligent orchestration 
of tasks. During execution, IdeFormer dynamically manages the state machine by alternating between tool usage and
LLM interactions, leveraging technologies like Grazie and OpenAI.

Designed to act as middleware, IdeFormer is provided as a standalone, self-contained executable that can be bundled or
downloaded during runtime. Built in Python, it takes full advantage of LangGraph’s cutting-edge advancements.

## Prerequisites

Before staring, please check out
the [IdeFormer setup documentation](code-agents-ideformer/README.md).

To implement an AI agent with IdeFormer, you'll need:

- Working IDE with Kotlin support;
- Valid Grazie token for accessing IdeFormer services;
- Access to the **IdeFormer** library and related dependencies.

### Obtaining a Grazie Token

To authenticate with the IdeFormer service, you'll need to provide a JWT token.
You can get this token from [Grazie Platform](https://platform.jetbrains.ai/):

- Click on your "Profile Picture" in the top-right;
- Select the `Copy Development Token` option in the dropdown;
- Either click directly on the token text or press the giant blue `Copy token` button.

### Dependency

Our core libraries are hosted on
[grazie-platform-public](https://jetbrains.team/p/grazi/packages/maven/grazie-platform-public).
To start working with IdeFormer in your project, you will need:

- `ai.jetbrains.code.agents`
    - [agents-core](https://jetbrains.team/p/grazi/packages/maven/grazie-platform-public/ai.jetbrains.code.agents/agents-core)
    - [agents-core-tools](https://jetbrains.team/p/grazi/packages/maven/grazie-platform-public/ai.jetbrains.code.agents/agents-core-tools)
    - [agents-tools-registry](https://jetbrains.team/p/grazi/packages/maven/grazie-platform-public/ai.jetbrains.code.agents/agents-tools-registry)
    - [code-agents-ideformer-client](https://jetbrains.team/p/grazi/packages/maven/grazie-platform-public/ai.jetbrains.code.agents/code-agents-ideformer-client)
    - [code-agents-ideformer-daemon](https://jetbrains.team/p/grazi/packages/maven/grazie-platform-public/ai.jetbrains.code.agents/code-agents-ideformer-daemon)
    - [code-agents-ideformer-executable](https://jetbrains.team/p/grazi/packages/maven/grazie-platform-public/ai.jetbrains.code.agents/code-agents-ideformer-executable)
- `ai.jetbrains.code.files`
    - [code-files-jvm](https://jetbrains.team/p/grazi/packages/maven/grazie-platform-public/ai.jetbrains.code.files/code-files-jvm)
    - [code-files-model](https://jetbrains.team/p/grazi/packages/maven/grazie-platform-public/ai.jetbrains.code.files/code-files-model)
- `ai.grazie.model`[^1]
    - [model-llm](https://jetbrains.team/p/grazi/packages/maven/grazie-platform-public/ai.grazie.model/model-llm)

Since our project uses Ktor to facilitate HTTP communication, you may also need:

- Ktor client engines, such as [CIO](https://central.sonatype.com/artifact/io.ktor/ktor-client-cio/3.0.3);
- Ktor JSON serialization adapters
  for [kotlinx](https://central.sonatype.com/artifact/io.ktor/ktor-serialization-kotlinx-json/3.0.3).

## Step-by-Step Implementation

1. [Parse Tools Descriptor](#1-parse-tools-descriptor)
2. [Register Tools](#2-register-tools)
3. [Select the AI Agent](#3-select-the-ai-agent)
4. [Define the Agent Configuration](#4-define-the-agent-configuration)
5. [Subscribe to Events](#5-subscribe-to-events)
6. [Prepare IdeFormer Daemon](#6-prepare-ideformer-daemon)
7. [Configure the IdeFormer Client](#7-configure-the-ideformer-client)
8. [Set Up the Token Provider](#8-set-up-the-token-provider)
9. [Run the Client](#9-run-the-client)

### 1. Parse Tools Descriptor

The first step is to define the tools your agent will use.
Tools are functions that can be executed by the agent.
You need to provide a descriptor for these tools, which is what the `ToolDescriptorProvider` class is for.
Although there are ready-made descriptors, they can also be written by hand[^2]:

```kotlin
val toolDescriptorProvider = ToolDescriptorProvider.static(
    ToolDescriptor(
        name = "toolName",
        description = "Detailed description of its purpose and outputs",
        requiredParameters = listOf(
            ToolParameterDescriptor(
                name = "parameterName",
                description = "Detailed description of its purpose",
                type = ToolParameterType.String,
            ),
            // Other `ToolParameterDescriptor` instances go here...
        ),
        optionalParameters = listOf(
            // Any number of `ToolParameterDescriptor` definitions...
        ),
    ),
    // Other `ToolDescriptor` instances go here...
)
```

### 2. Register Tools

In this step, you register your tools.
Each tool is a function that accepts a JSON of arguments provided by the LLM (Large Language Model).
Feel free to parse these arguments and pass to some relevant APIs in your environment to produce a result.
Please note that tools are also suspending functions, meaning that some UI actions or "human in the loop" are allowed.
You can create registries with either a single or an arbitrary number of stages.
Regardless of which method you choose, you must always define implementations for described tools in each stage.
Runtime validations are used to enforce these preconditions and will throw errors on missing implementations.

#### Single-Stage Tools:

You can register tools that handle one operation in a single "default" stage.
Here's an example where an arbitrary tool called `someTool` is registered:

```kotlin
val toolRegistry = singleStageStatelessToolRegistry(toolsDescriptorProvider) {
    tool("someTool") { arguments: JsonObject ->
        TODO("Process $arguments and return some result")
    }
}
```

#### Multi-Stage Tools:

For more complex workflows, you can define multiple stages.
You can think of stages as "labels" used to categorize tools.
Tools must be unique across all stages they belong to.
Here's an example of defining a staged tool registry:

```kotlin
val initialStage: ToolListDescriptor = TODO("Define \"initialStage\" tool descriptors")
val finalStage: ToolListDescriptor = TODO("Define \"finalStage\" tool descriptors")
multiStageToolRegistry {
    statelessStage("initialStage", initialStage) {
        tool("firstTool") { arguments: JsonObject -> 
            TODO("Tool call result, based on $arguments")
        }
        // Implement other tools here...
    }

    statelessStage("finalStage", finalStage) {
        tool("secondTool") { arguments: JsonObject ->
            TODO("Tool call result, based on $arguments")
        }
        // Implement other tools here...
    }
}
```

An example of a multi-stage registry in action would be an automated development agent.
One stage would have tools dedicated to manipulating the code and project structure,
while the other would involve tools related to the version control system, and so on.

### 3. Select the AI Agent

IdeFormer supports multiple agents.
You can choose the agent that best fits your use case.
In this example, we are using the `IdeFormerAgent.Simple.Grazie.Chat` agent.

```kotlin
val agent = IdeFormerAgent.Simple.Grazie.Chat
```

### 4. Define the Agent Configuration

The agent configuration defines the behavior of the AI agent.
This includes the system prompt that guides the agent's responses[^3].
The configuration also specifies the LLM settings.

```kotlin
val agentConfig = IdeFormerAgent.GrazieDefault.Config(
    llmConfig = IdeFormerAgent.GrazieDefault.Config.LLM(),
    systemPrompt = AgentSystemPromptProvider.fromString("""
    You are a question-answering agent with access to various tools.
    Your task is to answer questions by utilizing the tools provided.
    Always be as concise as possible, and do not provide any unnecessary information.
    """),
)
```

The `GrazieDefault` class provides a base implementation shared across agents with similar requirements,
leveraging standardized configuration mechanisms.
If you have your own agent that shares the same set of configuration parameters with most Grazie agents,
you can define it simply like this:

```kotlin
object MyAgent: IdeFormerAgent.GrazieDefault("my-agent-id")
```

After that, it can be used with `GrazieDefault.Config` and `GrazieDefault.Config.LLM` default parameters.
Please keep in mind that if your custom agent's config structure is different from the one defined in the
`GrazieDefault.Config`, you will have to define your own implementations of `IdeFormerAgentConfig` and `LLMConfig`
classes for your agent!

### 5. Subscribe to Events

Event handlers allow you to manage different types of events, such as when a tool is called,
when a result is received, or when an error occurs. You can define custom logic to handle these events.

```kotlin
val eventHandler = EventHandler {
    
    // Define handlers for specific exception types...
    onException<IllegalArgumentException> { cause: Throwable ->
        cause.printStackTrace()
    }

    // Define a catch-all handler for everything else.
    unhandledException { cause: Throwable ->
        cause.printStackTrace()
        println("An unknown error has occurred!")
    }

    // Define what to do with the result.
    onResultReceived { value: Any? ->
        println("Result: $value")
    }

    // Define how to react to individual tool calls.
    onToolCalled { toolName: String, toolArgs: JsonObject, stage: String ->
        println("Tool \"$toolName\" was called with: $toolArgs, at stage: \"$stage\"")
    }
}
```

### 6. Prepare IdeFormer Daemon

If you want to run IdeFormer service locally, you can use the `IdeFormerDaemonManager`.

#### Download IdeFormer Executable

We host the IdeFormer binaries in the dedicated Space package repository:
[ideformer-local-executable](https://jetbrains.team/p/grazi/packages/files/ideformer-local-executable).
There are two ways of downloading them.

##### Manually

> [!TIP]  
> We do not recommend this approach.
> If you want to do this programmatically, then skip to the next part!

On the Space packages website, download the executable that best suits your operating system and architecture.
Make sure that the downloaded binary has the correct permissions set for executing it.
Place them in the directory of your choice, making sure that the relative path to the executable in this directory
follows the format: `ideformer-executable/<name of the downloaded file>`
(i.e. `ideformer-executable/ideformer-0.1.7-914cf0-mac_arm64`).

##### Using API

Although it is possible to download them manually, we already provide APIs that would select the executable you need.
If you already know the version that you'd like to use, then you can select the best-suited executable for your system:

```kotlin
val version = TODO("Specify a version. For example: \"0.1.7-914cf0\".")
val identifier = IdeFormerDaemonConfig.ideFormerIdentifier(version)
```

This creates a new `IdeFormerExecutableIdentifier`, used for resource identification when downloading.
To perform the actual download, you use the `IdeFormerExecutableDownloader`:

```kotlin
val resourcesDir = Path.of("resources/dir")
val executableDownloader = IdeFormerExecutableDownloader()
executableDownloader.download(resourcesDir, identifier)
```

You can further augment the download process with a custom `HttpClient` from `Ktor`:

```kotlin
val engine: HttpClientEngine = TODO("Select an engine that suits your needs")
IdeFormerExecutableDownloader(httpClient = HttpClient(engine) {
    // Customize as you see fit. Add timeouts for instance...
})
```

#### Configure Daemon Manager

`IdeFormerDaemonConfig` is used to pass required information to start or connect to (if it is already running)
the IdeFormer service (check `IdeFormerDaemonConfig` documentation for more info about parameters and their effect):

```kotlin
val daemonConfig = IdeFormerDaemonConfig(
    version = version,

    // Directory with the downloaded executables.
    // Preferred approach is to download them dynamically.
    resourcesDir = resourcesDir,

    // If you provide them bundled and want to load from the JAR resources,
    // then specify the directory to extract to
    // cacheDir = Paths.get("cache/dir"),

    workingDir = Path.get("working/dir"),
    healthCheckIntervalMillis = 5.minutes.inWholeMilliseconds,
    startTimeoutMillis = 5.minutes.inWholeMilliseconds,
    startCheckDelayMillis = 1.seconds.inWholeMilliseconds,
    logFile = Path.get("ideformer.log"),
)

val daemonManager = IdeFormerDaemonManager(daemonConfig)
```

- `version`: IdeFormer executable version. Must correspond to the version you downloaded.
- `resourcesDir`: Directory used for locating binary executables.
  If you are downloading programmatically, then pass the destination directory here.
  Otherwise, by placing the binaries manually in the project `resources` directory this argument can be omitted.
- `workingDir`: Optional working directory for the IdeFormer process.
  If not specified, it will run in the same directory as the parent JVM process.
- `healthCheckIntervalMillis`: Configure interval for the background healthcheck job.
  Checks if the IdeFormer service is operational and if not, restarts it.
  This job is performed every 5 minutes by default.
  Setting this parameter to 0 disables the job.
- `startTimeoutMillis`: Configure the time limit for starting up the IdeFormer process.
  Failure to start within the specified amount results in an exception being thrown.
  The allowed startup time is 5 minutes by default.
- `startCheckDelayMillis`: Configure the amount of time between successive process startup checks.
  The delay between checks is 1 second by default.
  Setting this parameter to 0 disables the delay.
- `logFile`: Optional path where the IdeFormer service logs will be stored.
  If not explicitly specified, the logs will be stored in the temporary directory.

#### Start IdeFormer and Get Connection Configuration

To start the service and get the connection configuration, invoke the following method:

```kotlin
val clientConfig = daemonManager.startServiceIfNotRunning()
```

Use this configuration in the next step to set up the client and connect to the service.
Also, remember to stop the service when you finished working with it:

```kotlin
daemonManager.stopService(gracefully = false)
```

### 7. Configure the IdeFormer Client

The IdeFormer client communicates with the IdeFormer service.
To properly communicate with said service, you must first configure
the client with communication details:

```kotlin
val clientConfig = IdeFormerClientConfig(
    connection = IdeFormerClientConnectionConfig(
        protocol = URLProtocol.HTTP,
        host = "<host>",
        port = 5137,
    ),
    auth = IdeFormerClientAuthConfig(
        version = AuthVersion.V5,
        type = AuthType.User,
    ),
    timeout = IdeFormerConnectionTimeoutConfig(
        requestTimeoutMillis = 5.minutes.inWholeMilliseconds,
        connectTimeoutMillis = 1.minutes.inWholeMilliseconds,
        socketTimeoutMillis = 30.seconds.inWholeMilliseconds,
    ),
    api = IdeFormerApi.V1,
    grazieEnvironment = GrazieEnvironment.Production,
)
```

- `connection`: Settings required to establish a connection to the `IdeFormer` server.
    - `protocol`: Protocol to use (HTTP or HTTPS).
    - `host`: Host where the IdeFormer service is running.
    - `port`: Port number.
- `auth`: Authorization details for connecting to `IdeFormer` services.
    - `version`: Authorization version, only `V5` is supported.
    - `type`: Depending on your token, you must specify:
        - `User`: If using personal tokens.
        - `Application`: If using application tokens for heavier tasks (i.e., running benchmarks on a dataset).
- `timeout`: Settings for communication protocol timeouts.
    - Request: Maximum time allowed to process an HTTP call end-to-end (from sending a request to receiving a response).
    - Connect: Maximum time allowed for an HTTP client to establish a connection with the server.
    - Socket: Maximum time of inactivity between two data packets when exchanging data with a server.
- `api`: IdeFormer service API version, only `V1` is supported.
- `grazieEnvironment`: Grazie platform environment that the service will communicate with. You must specify:
    - `GrazieEnvironment.Production`: If using tokens from [production](https://platform.jetbrains.ai).
    - `GrazieEnvironment.Staging`: If using tokens from [staging](https://platform.stgn.jetbrains.ai).

### 8. Set Up the Token Provider

You can then use the previously obtained Grazie token to implement an `AuthConfigurationProvider` :

```kotlin
val authSettingsProvider = AuthConfigurationProvider.fromGrazieToken(token = TODO("DON'T FORGET TO ADD YOUR JWT TOKEN!"))
```

Also, `AuthConfigurationProvider` interface supports AI Enterprise and Proxy. Please, feel free to implement corresponding methods:

```kotlin
class MyAuthConfigurationProvider : AuthConfigurationProvider {
    override fun getAuthHeaders(): Headers = receiveHeadersFromStation()

    override fun getProxyConfiguration(): ProxyConfiguration =
        ProxyConfiguration.ProxyAutoConfiguration(pacUrl = getPacUrlFromSettings())
}
```

### 9. Run the Client

Finally, you'll run the client to interact with the IdeFormer service.
The client sends requests to the service, which processes them asynchronously.

```kotlin
val client = IdeFormerClient(clientConfig, authSettingsProvider)
print("Enter your question: ")

IdeFormerAgentRunner(
    client = client,
    toolRegistry = toolRegistry,
    agentConfig = agentConfig,
    eventHandler = eventHandler,
    agent = agent,
).run(readln())
```

When running IdeFormer service locally via the `IdeFormerDaemonManger`,
make sure you pass the same `IdeFormerClientConnectionConfig` instance
instead of specifying it manually!

### Key Points

- **Agent**: Choose the appropriate agent based on your use case (e.g., `Grazie.Chat`).
- **Daemon**: When running IdeFormer service locally, ensure it is running by using the `IdeFormerDaemonManager`.
- **Client**: Sends requests to the service and receives responses asynchronously.

## Examples

Code examples of how to write your own agent from start to finish are contained in the
[agents-examples](agents-examples) module.
Remember to copy [env.template.properties](./agents-examples/env.template.properties) 
and specify required parameters.
Then use `runExample...` Gradle tasks to run them.

## Conclusion

By following the above steps, you can implement a fully functional AI agent using the IdeFormer framework.
The key steps involve configuring the tools, defining the agent's behavior, subscribing to events,
and ensuring proper communication with the IdeFormer service.

This framework is flexible and allows you to customize the tools and the agent's logic for any arbitrary use case.
Feel free to extend it to suit more complex scenarios, such as multi-stage processes, integrating additional tools,
or handling different types of events and exceptions.

[^1]:
    The required version for these dependencies is influenced by the version of `code-engine` you are using.
    For example, if you are using `1.0.0-beta.26+0.4.16`, then you will need either `0.4.16` or later patches.

[^2]:
    Defining tool descriptors in code is good for fast experiments, but for production purposes this method is not recommended.
    Please contribute to `GlobalAgentToolsRegistry.Tools` and then feel free to use the `fromRegistry` method instead.
    You can read more about contributing [here](https://github.com/JetBrains/koan-agents/tree/main/agents/agents-tools-registry#contributing-tools).

[^3]:
    Writing prompts in code is good for fast experiments, but for production purposes this method is not recommended.
    Please contribute to `GlobalAgentToolsRegistry.Prompts` and then feel free to use `fromRegistry` method.
    You can read more about contributing [here](https://github.com/JetBrains/koan-agents/tree/main/agents/agents-tools-registry#contributing-prompts).

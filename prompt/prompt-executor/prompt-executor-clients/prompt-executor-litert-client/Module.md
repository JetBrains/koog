## Local Android Models

Provides a way to use the LiteRT client on Android devices for a Koog agent.

### Prerequisites

- Android device connected via ADB
- A compatible `.litertlm` model file (e.g. from [HuggingFace litert-community](https://huggingface.co/litert-community))

---

### 1. Upload the model to the device
```bash
MODEL_PATH="/path/to/your/model.litertlm"
DEVICE_DIR="/data/local/tmp/llm"

echo "Cleaning $DEVICE_DIR on device..."
adb shell "rm -rf $DEVICE_DIR && mkdir -p $DEVICE_DIR"

echo "Pushing model to $DEVICE_DIR..."
adb push "$MODEL_PATH" "$DEVICE_DIR/"

echo "Done! Model pushed to $DEVICE_DIR/$(basename $MODEL_PATH)"
```

---

### 2. Define the model

A predefined model `LiteRTLLModels.FunctionGemma` is available out of the box (maps to `tiny_garden.litertlm`).

To use a custom model, create an `LLModel` instance:

```kotlin
val myModel: LLModel = LLModel(
    provider = LiteRTLLMProvider,
    // The id is the filename of the model on the device (basename of $MODEL_PATH)
    // e.g. for /data/local/tmp/llm/my_model.litertlm use "my_model.litertlm"
    id = "my_model.litertlm",
    capabilities = listOf(
        LLMCapability.Tools,
        LLMCapability.Completion
    ),
    contextLength = 200_000,
    maxOutputTokens = 4_096,
)

// Optionally register the custom model so it appears in LiteRTLLModels.models
LiteRTLLModels.addCustomModel(myModel)
```

---

### 3. Configure the client

```kotlin
val config = LiteRTClientConfig(
    // Path to the directory containing model files on the device ($DEVICE_DIR)
    modelsPath = "/data/local/tmp/llm",
    cacheDir = "/data/local/tmp/llm/cache",
    // Optionally set a default model (defaults to LiteRTLLModels.FunctionGemma)
    defaultModel = myModel,
)
```

---

### 4. Run the client

```kotlin
val client = LiteRTLLMClient(config)

val executor = MultiLLMPromptExecutor(client)

val agent = AIAgent(
    promptExecutor = executor,
    // ... your agent configuration
)

agent.run("Your prompt here")
```

> **Note:** If the message history, tools, or model settings change between calls, the LiteRT session will be recreated. This incurs additional initialization time, so minimize unnecessary changes to these parameters.

---

### Current limitation: only one tool call per model response is supported

LiteRT `ToolCall` does not expose a stable call id. Koog uses tool-call ids to correlate `Message.Tool.Result` with `Message.Tool.Call`. Therefore this client currently supports only a single tool call per LiteRT response. Multiple tool calls must either be rejected or handled by synthesizing stable ids and batching tool responses back to LiteRT in order. Until proper support exists, the converter fails fast with `UnsupportedOperationException` when more than one tool call is present in a single LiteRT response.

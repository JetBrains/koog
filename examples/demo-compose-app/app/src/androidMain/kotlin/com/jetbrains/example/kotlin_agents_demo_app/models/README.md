# Google QA
* How to supply models to the device and discover ()
* What is the status of the models with tool calling (support)
* Why we need delay (how many) delay(settings.closeSessionDelay)
* How to check is GPU busy or not, how many, how many models in parallel (tooling for it), model in GPU availability & resources, can we share state of the models across the apps

# Local models
LiteRT is TF for on-device model inferencing https://github.com/google-ai-edge/LiteRT (C++ runtime library).
To use the framework, the model should be processed to the LiteRT format.
Here is the HF repo of already transformed models https://huggingface.co/litert-community
The problem is the only one model which support tool calling is Hummer https://huggingface.co/litert-community/Hammer2.1-1.5b

Android has a library to work with LiteRT from java (using NJI)
* https://github.com/google-ai-edge/mediapipe
* https://github.com/google-ai-edge/mediapipe/tree/master/mediapipe/tasks/java/com/google/mediapipe/tasks/genai/llminference
  And the documentation how to use it
* https://ai.google.dev/edge/mediapipe/solutions/guide
* https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android

For tool calling local agents extension is used
https://github.com/google-ai-edge/ai-edge-apis/tree/main/local_agents
https://ai.google.dev/edge/mediapipe/solutions/genai/function_calling/android

# Model
Gemma is the Google on-device model https://huggingface.co/litert-community/Gemma3-1B-IT
Gemma3-1B-(IT)_(multi-prefill-seq)_(seq128)_(f32|q4|q8)_(ekv1280|ekv2048|ekv4096)_(block32|block128).task
* Gemma → Model family (Gemma).
* 3 → Likely version (v3).
* 1B → Parameter size = ~1 billion parameters.
* IT → Instruction-Tuned (fine-tuned for following instructions).
* multi-prefill → Likely optimized for multiple prefill sequences in parallel.
* seq128 → Maximum sequence length (128 tokens).
* quantization
    * f32 → 32-bit floating point precision (full precision).
    * q4 → Quantized to 4-bit integers.
    * q8 → Quantized to 8-bit integers.
    * block32, block128 → Quantization block size.
* ekv1280, ekv2048, ekv4096 → Likely means effective KV cache size (number of key/value slots in the attention cache).
* .task → Model or runtime task file (probably used by the training/inference pipeline).
* .litertlm → LiteRT Language Model format (optimized model for LiteRT runtime, smaller and faster).
* Chipset identifiers
    * mtXXXX → MediaTek processors.
    * smXXXX → Qualcomm Snapdragon processors.

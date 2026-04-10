package ai.koog.prompt.executor.clients.siliconflow

import ai.koog.prompt.executor.clients.LLModelDefinitions
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import kotlin.jvm.JvmField

/**
 * SiliconFlow model definitions.
 *
 * This object contains catalog-based chat, multimodal, generation, reranking, speech, and embedding models
 * available through SiliconFlow. Model descriptions were translated to English from the source catalog.
 */
public object SiliconFlowModels : LLModelDefinitions {

    /**
     * ascend-tribe/pangu-pro-moe.
     *
     * supports Text Generation. Context length is 128k.
     */
    @JvmField
    public val PanguProMoE: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "ascend-tribe/pangu-pro-moe",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
        ),
        contextLength = 128_000,
    )

    /**
     * BAAI/bge-reranker-v2-m3.
     *
     * supports Reranking. Context length is 8k.
     */
    @JvmField
    public val BgeRerankerV2M3: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "BAAI/bge-reranker-v2-m3",
        capabilities = listOf(
            //  koog does not have a separate ranking capability,
            //  but it can be represented as an embedding capability,
            //  where the model generates embeddings that can be used for ranking.
            LLMCapability.Embed
        ),
        contextLength = 8_000,
    )

    /**
     * baidu/ERNIE-4.5-300B-A47B.
     *
     * supports Text Generation, JSON Mode. Context length is 128k.
     */
    @JvmField
    public val ERNIE_4_5_300B_A47B: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "baidu/ERNIE-4.5-300B-A47B",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 128_000,
    )

    /**
     * ByteDance-Seed/Seed-OSS-36B-Instruct.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val SeedOSS_36B_Instruct: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "ByteDance-Seed/Seed-OSS-36B-Instruct",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * deepseek-ai/DeepSeek-OCR.
     *
     * supports Text Generation, Vision Understanding, JSON Mode. Context length is 8k.
     */
    @JvmField
    public val DeepSeekOCR: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "deepseek-ai/DeepSeek-OCR",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 8_000,
    )

    /**
     * deepseek-ai/DeepSeek-R1.
     *
     * supports Text Generation, Function Calling, FIM Completion, Prefix Completion, JSON Mode. Context length is 160k.
     */
    @JvmField
    public val DeepSeekR1: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "deepseek-ai/DeepSeek-R1",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 160_000,
    )

    /**
     * deepseek-ai/DeepSeek-R1-0528-Qwen3-8B.
     *
     * supports Text Generation, JSON Mode. Context length is 128k.
     */
    @JvmField
    public val DeepSeekR1_0528_Qwen3_8B: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "deepseek-ai/DeepSeek-R1-0528-Qwen3-8B",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 128_000,
    )

    /**
     * deepseek-ai/DeepSeek-R1-Distill-Qwen-14B.
     *
     * supports Text Generation, Function Calling, FIM Completion, Prefix Completion, JSON Mode. Context length is 128k.
     */
    @JvmField
    public val DeepSeekR1_Distill_Qwen_14B: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "deepseek-ai/DeepSeek-R1-Distill-Qwen-14B",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 128_000,
    )

    /**
     * deepseek-ai/DeepSeek-R1-Distill-Qwen-32B.
     *
     * supports Text Generation, Function Calling, FIM Completion, Prefix Completion, JSON Mode. Context length is 128k.
     */
    @JvmField
    public val DeepSeekR1_Distill_Qwen32B: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "deepseek-ai/DeepSeek-R1-Distill-Qwen-32B",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 128_000,
    )

    /**
     * deepseek-ai/DeepSeek-R1-Distill-Qwen-7B.
     *
     * supports Text Generation, Function Calling, FIM Completion, Prefix Completion, JSON Mode. Context length is 128k.
     */
    @JvmField
    public val DeepSeekR1_Distill_Qwen_7B: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "deepseek-ai/DeepSeek-R1-Distill-Qwen-7B",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 128_000,
    )

    /**
     * deepseek-ai/DeepSeek-V2.5.
     *
     * supports Text Generation, Function Calling, FIM Completion, Prefix Completion, JSON Mode. Context length is 32k.
     */
    @JvmField
    public val DeepSeekV2_5: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "deepseek-ai/DeepSeek-V2.5",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 32_000,
    )

    /**
     * deepseek-ai/DeepSeek-V3.
     *
     * supports Text Generation, Function Calling, FIM Completion, Prefix Completion, JSON Mode. Context length is 160k.
     */
    @JvmField
    public val DeepSeekV3: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "deepseek-ai/DeepSeek-V3",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 160_000,
    )

    /**
     * deepseek-ai/DeepSeek-V3.1-Terminus.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 160k.
     */
    @JvmField
    public val DeepSeekV3_1_Terminus: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "deepseek-ai/DeepSeek-V3.1-Terminus",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 160_000,
    )

    /**
     * deepseek-ai/DeepSeek-V3.2.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 160k.
     */
    @JvmField
    public val DeepSeekV3_2: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "deepseek-ai/DeepSeek-V3.2",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 160_000,
    )

    /**
     * fnlp/MOSS-TTSD-v0.5.
     *
     * supports Text-to-Speech. Context length is not provided in the SiliconFlow catalog.
     */
    @JvmField
    public val MOSS_TTSD_V0_5: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "fnlp/MOSS-TTSD-v0.5",
        capabilities = listOf(
            LLMCapability.Audio,
            LLMCapability.Completion,
        ),
    )

    /**
     * FunAudioLLM/CosyVoice2-0.5B.
     *
     * supports Text-to-Speech. Context length is not provided in the SiliconFlow catalog.
     */
    @JvmField
    public val CosyVoice2_0_5B: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "FunAudioLLM/CosyVoice2-0.5B",
        capabilities = listOf(
            LLMCapability.Audio,
            LLMCapability.Completion,
        ),
    )

    /**
     * FunAudioLLM/SenseVoiceSmall.
     *
     * supports Speech-to-Text. Context length is not provided in the SiliconFlow catalog.
     */
    @JvmField
    public val SenseVoiceSmall: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "FunAudioLLM/SenseVoiceSmall",
        capabilities = listOf(
            LLMCapability.Audio,
            LLMCapability.Completion,
        ),
    )

    /**
     * inclusionAI/Ling-flash-2.0.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 128k.
     */
    @JvmField
    public val LingFlash_2_0: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "inclusionAI/Ling-flash-2.0",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 128_000,
    )

    /**
     * inclusionAI/Ling-mini-2.0.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 128k.
     */
    @JvmField
    public val LingMini_2_0: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "inclusionAI/Ling-mini-2.0",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 128_000,
    )

    /**
     * inclusionAI/Ring-flash-2.0.
     *
     * supports Text Generation, Prefix Completion. Context length is 128k.
     */
    @JvmField
    public val RingFlash_2_0: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "inclusionAI/Ring-flash-2.0",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
        ),
        contextLength = 128_000,
    )

    /**
     * IndexTeam/IndexTTS-2.
     *
     * supports Text-to-Speech. Context length is not provided in the SiliconFlow catalog.
     */
    @JvmField
    public val IndexTTS_2: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "IndexTeam/IndexTTS-2",
        capabilities = listOf(
            LLMCapability.Audio,
            LLMCapability.Completion,
        ),
    )

    /**
     * internlm/internlm2_5-7b-chat.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 32k.
     */
    @JvmField
    public val Internlm2_5_7b_Chat: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "internlm/internlm2_5-7b-chat",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 32_000,
    )

    /**
     * Kwai-Kolors/Kolors.
     *
     * supports Text-to-Image. Context length is not provided in the SiliconFlow catalog.
     */
    @JvmField
    public val Kolors: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Kwai-Kolors/Kolors",
        capabilities = listOf(
            LLMCapability.Vision.Image,
            LLMCapability.Completion,
        ),
    )

    /**
     * Kwaipilot/KAT-Dev.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 128k.
     */
    @JvmField
    public val KAT_Dev: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Kwaipilot/KAT-Dev",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 128_000,
    )

    /**
     * moonshotai/Kimi-K2-Instruct-0905.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val Kimi_K2_Instruct_0905: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "moonshotai/Kimi-K2-Instruct-0905",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * moonshotai/Kimi-K2-Thinking.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val Kimi_K2_Thinking: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "moonshotai/Kimi-K2-Thinking",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * netease-youdao/bce-reranker-base_v1.
     *
     * supports Reranking. Context length is 512.
     */
    @JvmField
    public val BceRerankerBaseV1: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "netease-youdao/bce-reranker-base_v1",
        capabilities = listOf(
            LLMCapability.Embed,
        ),
        contextLength = 512,
    )

    /**
     * PaddlePaddle/PaddleOCR-VL.
     *
     * supports Text Generation, Vision Understanding, JSON Mode. Context length is not provided in the SiliconFlow catalog.
     */
    @JvmField
    public val PaddleOCR_VL: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "PaddlePaddle/PaddleOCR-VL",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
    )

    /**
     * PaddlePaddle/PaddleOCR-VL-1.5.
     *
     * supports Text Generation, Vision Understanding, JSON Mode. Context length is not provided in the SiliconFlow catalog.
     */
    @JvmField
    public val PaddleOCR_VL_1_5: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "PaddlePaddle/PaddleOCR-VL-1.5",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
    )

    /**
     * Pro/BAAI/bge-reranker-v2-m3.
     *
     * supports Reranking. Context length is 8k.
     */
    @JvmField
    public val ProBgeReranker_V2_M3: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Pro/BAAI/bge-reranker-v2-m3",
        capabilities = listOf(
            LLMCapability.Embed,
        ),
        contextLength = 8_000,
    )

    /**
     * Pro/deepseek-ai/DeepSeek-R1.
     *
     * supports Text Generation, Function Calling, FIM Completion, Prefix Completion, JSON Mode. Context length is 160k.
     */
    @JvmField
    public val ProDeepSeekR1: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Pro/deepseek-ai/DeepSeek-R1",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 160_000,
    )

    /**
     * Pro/deepseek-ai/DeepSeek-V3.
     *
     * supports Text Generation, Function Calling, FIM Completion, Prefix Completion, JSON Mode. Context length is 160k.
     */
    @JvmField
    public val ProDeepSeekV3: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Pro/deepseek-ai/DeepSeek-V3",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 160_000,
    )

    /**
     * Pro/deepseek-ai/DeepSeek-V3.1-Terminus.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 160k.
     */
    @JvmField
    public val ProDeepSeekV3_1_Terminus: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Pro/deepseek-ai/DeepSeek-V3.1-Terminus",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 160_000,
    )

    /**
     * Pro/deepseek-ai/DeepSeek-V3.2.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 160k.
     */
    @JvmField
    public val ProDeepSeekV3_2: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Pro/deepseek-ai/DeepSeek-V3.2",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 160_000,
    )

    /**
     * Pro/MiniMaxAI/MiniMax-M2.5.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 192k.
     */
    @JvmField
    public val ProMiniMax_M2_5: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Pro/MiniMaxAI/MiniMax-M2.5",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 192_000,
    )

    /**
     * Pro/moonshotai/Kimi-K2-Instruct-0905.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val ProKimi_K2_Instruct_0905: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Pro/moonshotai/Kimi-K2-Instruct-0905",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * Pro/moonshotai/Kimi-K2-Thinking.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val ProKimi_K2_Thinking: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Pro/moonshotai/Kimi-K2-Thinking",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * Pro/moonshotai/Kimi-K2.5.
     *
     * supports Text Generation, Vision Understanding, Function Calling, Prefix Completion, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val ProKimi_K2_5: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Pro/moonshotai/Kimi-K2.5",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * Pro/Qwen/Qwen2.5-7B-Instruct.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 32k.
     */
    @JvmField
    public val ProQwen2_5_7B_Instruct: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Pro/Qwen/Qwen2.5-7B-Instruct",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 32_000,
    )

    /**
     * Pro/zai-org/GLM-4.7.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 198k.
     */
    @JvmField
    public val ProGLM4_7: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Pro/zai-org/GLM-4.7",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 198_000,
    )

    /**
     * Pro/zai-org/GLM-5.
     *
     * supports Text Generation, Function Calling, FIM Completion, Prefix Completion, JSON Mode. Context length is 198k.
     */
    @JvmField
    public val ProGLM5: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Pro/zai-org/GLM-5",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 198_000,
    )

    /**
     * Pro/zai-org/GLM-5.1.
     *
     * supports Text Generation, Function Calling, FIM Completion, Prefix Completion, JSON Mode. Context length is 198k.
     */
    @JvmField
    public val ProGLM5_1: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Pro/zai-org/GLM-5.1",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 198_000,
    )

    /**
     * Qwen/Qwen-Image.
     *
     * supports Text-to-Image. Context length is not provided in the SiliconFlow catalog.
     */
    @JvmField
    public val QwenImage: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen-Image",
        capabilities = listOf(
            LLMCapability.Vision.Image,
            LLMCapability.Completion,
        ),
    )

    /**
     * Qwen/Qwen-Image-Edit.
     *
     * supports Image-to-Image. Context length is not provided in the SiliconFlow catalog.
     */
    @JvmField
    public val QwenImageEdit: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen-Image-Edit",
        capabilities = listOf(
            LLMCapability.Vision.Image,
            LLMCapability.Completion,
        ),
    )

    /**
     * Qwen/Qwen-Image-Edit-2509.
     *
     * supports Image-to-Image. Context length is not provided in the SiliconFlow catalog.
     */
    @JvmField
    public val QwenImageEdit_2509: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen-Image-Edit-2509",
        capabilities = listOf(
            LLMCapability.Vision.Image,
            LLMCapability.Completion,
        ),
    )

    /**
     * Qwen/Qwen2-VL-72B-Instruct.
     *
     * supports Text Generation, Vision Understanding, Prefix Completion. Context length is 32k.
     */
    @JvmField
    public val Qwen2_VL_72B_Instruct: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen2-VL-72B-Instruct",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
        ),
        contextLength = 32_000,
    )

    /**
     * Qwen/Qwen2.5-14B-Instruct.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 32k.
     */
    @JvmField
    public val Qwen2_5_14B_Instruct: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen2.5-14B-Instruct",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 32_000,
    )

    /**
     * Qwen/Qwen2.5-32B-Instruct.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 32k.
     */
    @JvmField
    public val Qwen2_5_32B_Instruct: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen2.5-32B-Instruct",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 32_000,
    )

    /**
     * Qwen/Qwen2.5-72B-Instruct.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 32k.
     */
    @JvmField
    public val Qwen2_5_72B_Instruct: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen2.5-72B-Instruct",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 32_000,
    )

    /**
     * Qwen/Qwen2.5-72B-Instruct-128K.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 128k.
     */
    @JvmField
    public val Qwen2_5_72B_Instruct_128K: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen2.5-72B-Instruct-128K",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 128_000,
    )

    /**
     * Qwen/Qwen2.5-7B-Instruct.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 32k.
     */
    @JvmField
    public val Qwen2_5_7B_Instruct: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen2.5-7B-Instruct",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 32_000,
    )

    /**
     * Qwen/Qwen2.5-Coder-32B-Instruct.
     *
     * supports Text Generation, Function Calling, FIM Completion, Prefix Completion. Context length is 32k.
     */
    @JvmField
    public val Qwen2_5_Coder_32B_Instruct: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen2.5-Coder-32B-Instruct",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
        ),
        contextLength = 32_000,
    )

    /**
     * Qwen/Qwen2.5-VL-32B-Instruct.
     *
     * supports Text Generation, Vision Understanding, Prefix Completion. Context length is 128k.
     */
    @JvmField
    public val Qwen2_5_VL_32B_Instruct: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen2.5-VL-32B-Instruct",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
        ),
        contextLength = 128_000,
    )

    /**
     * Qwen/Qwen2.5-VL-72B-Instruct.
     *
     * supports Text Generation, Vision Understanding, Prefix Completion. Context length is 128k.
     */
    @JvmField
    public val Qwen2_5_VL_72B_Instruct: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen2.5-VL-72B-Instruct",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
        ),
        contextLength = 128_000,
    )

    /**
     * Qwen/Qwen3-14B.
     *
     * supports Text Generation, Function Calling, JSON Mode. Context length is 128k.
     */
    @JvmField
    public val Qwen3_14B: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3-14B",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 128_000,
    )

    /**
     * Qwen/Qwen3-235B-A22B-Instruct-2507.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val Qwen3_235B_A22B_Instruct_2507: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3-235B-A22B-Instruct-2507",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * Qwen/Qwen3-235B-A22B-Thinking-2507.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val Qwen3_235B_A22B_Thinking_2507: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3-235B-A22B-Thinking-2507",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * Qwen/Qwen3-30B-A3B-Instruct-2507.
     *
     * supports Text Generation, Function Calling, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val Qwen3_30B_A3B_Instruct_2507: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3-30B-A3B-Instruct-2507",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * Qwen/Qwen3-30B-A3B-Thinking-2507.
     *
     * supports Text Generation, Function Calling, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val Qwen3_30B_A3B_Thinking_2507: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3-30B-A3B-Thinking-2507",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * Qwen/Qwen3-32B.
     *
     * supports Text Generation, Function Calling, JSON Mode. Context length is 128k.
     */
    @JvmField
    public val Qwen3_32B: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3-32B",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 128_000,
    )

    /**
     * Qwen/Qwen3-8B.
     *
     * supports Text Generation, Function Calling, JSON Mode. Context length is 128k.
     */
    @JvmField
    public val Qwen3_8B: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3-8B",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 128_000,
    )

    /**
     * Qwen/Qwen3-Coder-30B-A3B-Instruct.
     *
     * supports Text Generation, Function Calling, FIM Completion, Prefix Completion, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val Qwen3_Coder_30B_A3B_Instruct: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3-Coder-30B-A3B-Instruct",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * Qwen/Qwen3-Coder-480B-A35B-Instruct.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val Qwen3_Coder_480B_A35B_Instruct: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3-Coder-480B-A35B-Instruct",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * Qwen/Qwen3-Omni-30B-A3B-Captioner.
     *
     * supports Text Generation, Vision Understanding, Function Calling, FIM Completion, Prefix Completion, JSON Mode. Context length is 64k.
     */
    @JvmField
    public val Qwen3_Omni_30B_A3B_Captioner: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3-Omni-30B-A3B-Captioner",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 64_000,
    )

    /**
     * Qwen/Qwen3-Omni-30B-A3B-Instruct.
     *
     * supports Text Generation, Vision Understanding, Function Calling, FIM Completion, Prefix Completion, JSON Mode. Context length is 64k.
     */
    @JvmField
    public val Qwen3_Omni_30B_A3B_Instruct: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3-Omni-30B-A3B-Instruct",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 64_000,
    )

    /**
     * Qwen/Qwen3-Omni-30B-A3B-Thinking.
     *
     * supports Text Generation, Vision Understanding, Function Calling, FIM Completion, Prefix Completion, JSON Mode. Context length is 64k.
     */
    @JvmField
    public val Qwen3_Omni_30B_A3B_Thinking: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3-Omni-30B-A3B-Thinking",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 64_000,
    )

    /**
     * Qwen/Qwen3-Reranker-0.6B.
     *
     * supports Reranking. Context length is 32k.
     */
    @JvmField
    public val Qwen3_Reranker_0_6B: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3-Reranker-0.6B",
        capabilities = listOf(
            LLMCapability.Embed,
        ),
        contextLength = 32_000,
    )

    /**
     * Qwen/Qwen3-Reranker-4B.
     *
     * supports Reranking. Context length is 32k.
     */
    @JvmField
    public val Qwen3_Reranker_4B: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3-Reranker-4B",
        capabilities = listOf(
            LLMCapability.Embed,
        ),
        contextLength = 32_000,
    )

    /**
     * Qwen/Qwen3-Reranker-8B.
     *
     * supports Reranking. Context length is 32k.
     */
    @JvmField
    public val Qwen3_Reranker_8B: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3-Reranker-8B",
        capabilities = listOf(
            LLMCapability.Embed,
        ),
        contextLength = 32_000,
    )

    /**
     * Qwen/Qwen3-VL-235B-A22B-Instruct.
     *
     * supports Text Generation, Vision Understanding, Function Calling, FIM Completion, Prefix Completion, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val Qwen3_VL_235B_A22B_Instruct: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3-VL-235B-A22B-Instruct",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * Qwen/Qwen3-VL-235B-A22B-Thinking.
     *
     * supports Text Generation, Vision Understanding, Function Calling, FIM Completion, Prefix Completion, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val Qwen3_VL_235B_A22B_Thinking: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3-VL-235B-A22B-Thinking",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * Qwen/Qwen3-VL-30B-A3B-Instruct.
     *
     * supports Text Generation, Vision Understanding, Function Calling, FIM Completion, Prefix Completion, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val Qwen3_VL_30B_A3B_Instruct: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3-VL-30B-A3B-Instruct",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * Qwen/Qwen3-VL-30B-A3B-Thinking.
     *
     * supports Text Generation, Vision Understanding, Function Calling, FIM Completion, Prefix Completion, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val Qwen3_VL_30B_A3B_Thinking: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3-VL-30B-A3B-Thinking",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * Qwen/Qwen3-VL-32B-Instruct.
     *
     * supports Text Generation, Vision Understanding, Function Calling, Prefix Completion, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val Qwen3_VL_32B_Instruct: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3-VL-32B-Instruct",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * Qwen/Qwen3-VL-32B-Thinking.
     *
     * supports Text Generation, Vision Understanding, Function Calling, Prefix Completion, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val Qwen3_VL_32B_Thinking: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3-VL-32B-Thinking",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * Qwen/Qwen3-VL-8B-Instruct.
     *
     * supports Text Generation, Vision Understanding, Function Calling, Prefix Completion, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val Qwen3_VL_8B_Instruct: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3-VL-8B-Instruct",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * Qwen/Qwen3-VL-8B-Thinking.
     *
     * supports Text Generation, Vision Understanding, Function Calling, Prefix Completion, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val Qwen3_VL_8B_Thinking: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3-VL-8B-Thinking",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * Qwen/Qwen3.5-122B-A10B.
     *
     * supports Text Generation, Vision Understanding, Function Calling, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val Qwen3_5_122B_A10B: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3.5-122B-A10B",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * Qwen/Qwen3.5-27B.
     *
     * supports Text Generation, Vision Understanding, Function Calling, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val Qwen3_5_27B: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3.5-27B",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * Qwen/Qwen3.5-35B-A3B.
     *
     * supports Text Generation, Vision Understanding, Function Calling, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val Qwen3_5_35B_A3B: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3.5-35B-A3B",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * Qwen/Qwen3.5-397B-A17B.
     *
     * supports Text Generation, Vision Understanding, Function Calling, Prefix Completion, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val Qwen3_5_397B_A17B: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3.5-397B-A17B",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * Qwen/Qwen3.5-4B.
     *
     * supports Text Generation, Vision Understanding, Function Calling, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val Qwen3_5_4B: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3.5-4B",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * Qwen/Qwen3.5-9B.
     *
     * supports Text Generation, Vision Understanding, Function Calling, JSON Mode. Context length is 256k.
     */
    @JvmField
    public val Qwen3_5_9B: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/Qwen3.5-9B",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 256_000,
    )

    /**
     * Qwen/QwQ-32B.
     *
     * supports Text Generation, Function Calling. Context length is 128k.
     */
    @JvmField
    public val QwQ_32B: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Qwen/QwQ-32B",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
        ),
        contextLength = 128_000,
    )

    /**
     * stepfun-ai/Step-3.5-Flash.
     *
     * supports Text Generation, Function Calling, Prefix Completion. Context length is 256k.
     */
    @JvmField
    public val Step3_5_Flash: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "stepfun-ai/Step-3.5-Flash",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
        ),
        contextLength = 256_000,
    )

    /**
     * TeleAI/TeleSpeechASR.
     *
     * supports Speech-to-Text. Context length is not provided in the SiliconFlow catalog.
     */
    @JvmField
    public val TeleSpeechASR: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "TeleAI/TeleSpeechASR",
        capabilities = listOf(
            LLMCapability.Audio,
            LLMCapability.Completion,
        ),
    )

    /**
     * tencent/Hunyuan-A13B-Instruct.
     *
     * supports Text Generation, JSON Mode. Context length is 128k.
     */
    @JvmField
    public val Hunyuan_A13B_Instruct: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "tencent/Hunyuan-A13B-Instruct",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 128_000,
    )

    /**
     * tencent/Hunyuan-MT-7B.
     *
     * supports Text Generation, Prefix Completion, JSON Mode. Context length is 32k.
     */
    @JvmField
    public val Hunyuan_MT_7B: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "tencent/Hunyuan-MT-7B",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 32_000,
    )

    /**
     * THUDM/GLM-4-32B-0414.
     *
     * supports Text Generation, Function Calling, JSON Mode. Context length is 32k.
     */
    @JvmField
    public val GLM4_32B_0414: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "THUDM/GLM-4-32B-0414",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 32_000,
    )

    /**
     * THUDM/GLM-4-9B-0414.
     *
     * supports Text Generation, Function Calling, JSON Mode. Context length is 32k.
     */
    @JvmField
    public val GLM4_9B_0414: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "THUDM/GLM-4-9B-0414",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 32_000,
    )

    /**
     * THUDM/GLM-4.1V-9B-Thinking.
     *
     * supports Text Generation, Vision Understanding. Context length is 64k.
     */
    @JvmField
    public val GLM4_1V_9B_Thinking: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "THUDM/GLM-4.1V-9B-Thinking",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
        ),
        contextLength = 64_000,
    )

    /**
     * THUDM/GLM-Z1-32B-0414.
     *
     * supports Text Generation, Function Calling, JSON Mode. Context length is 128k.
     */
    @JvmField
    public val GLM_Z1_32B_0414: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "THUDM/GLM-Z1-32B-0414",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 128_000,
    )

    /**
     * THUDM/GLM-Z1-9B-0414.
     *
     * supports Text Generation, Function Calling, JSON Mode. Context length is 128k.
     */
    @JvmField
    public val GLM_Z1_9B_0414: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "THUDM/GLM-Z1-9B-0414",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 128_000,
    )

    /**
     * Wan-AI/Wan2.2-I2V-A14B.
     *
     * supports Image-to-Video. Context length is not provided in the SiliconFlow catalog.
     */
    @JvmField
    public val Wan2_2_I2V_A14B: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Wan-AI/Wan2.2-I2V-A14B",
        capabilities = listOf(
            LLMCapability.Vision.Video,
            LLMCapability.Completion,
        ),
    )

    /**
     * Wan-AI/Wan2.2-T2V-A14B.
     *
     * supports Text-to-Video. Context length is not provided in the SiliconFlow catalog.
     */
    @JvmField
    public val Wan2_2_T2V_A14B: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "Wan-AI/Wan2.2-T2V-A14B",
        capabilities = listOf(
            LLMCapability.Vision.Video,
            LLMCapability.Completion,
        ),
    )

    /**
     * zai-org/GLM-4.5-Air.
     *
     * supports Text Generation, Function Calling. Context length is 128k.
     */
    @JvmField
    public val GLM4_5_Air: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "zai-org/GLM-4.5-Air",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
        ),
        contextLength = 128_000,
    )

    /**
     * zai-org/GLM-4.5V.
     *
     * supports Text Generation, Vision Understanding, Function Calling. Context length is 64k.
     */
    @JvmField
    public val GLM4_5V: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "zai-org/GLM-4.5V",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
        ),
        contextLength = 64_000,
    )

    /**
     * zai-org/GLM-4.6.
     *
     * supports Text Generation, Function Calling, Prefix Completion, JSON Mode. Context length is 198k.
     */
    @JvmField
    public val GLM4_6: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "zai-org/GLM-4.6",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
        ),
        contextLength = 198_000,
    )

    /**
     * zai-org/GLM-4.6V.
     *
     * supports Text Generation, Vision Understanding, Function Calling, Prefix Completion. Context length is 128k.
     */
    @JvmField
    public val GLM4_6V: LLModel = LLModel(
        provider = LLMProvider.SiliconFlow,
        id = "zai-org/GLM-4.6V",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Speculation,
            LLMCapability.Vision.Image,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
        ),
        contextLength = 128_000,
    )

    /**
     * Supported non-embedding models for SiliconFlow.
     * Embedding models are separated under [Embeddings].
     */
    public val supportedModels: List<LLModel> = listOf(
        PanguProMoE,
        BgeRerankerV2M3,
        ERNIE_4_5_300B_A47B,
        SeedOSS_36B_Instruct,
        DeepSeekOCR,
        DeepSeekR1,
        DeepSeekR1_0528_Qwen3_8B,
        DeepSeekR1_Distill_Qwen_14B,
        DeepSeekR1_Distill_Qwen32B,
        DeepSeekR1_Distill_Qwen_7B,
        DeepSeekV2_5,
        DeepSeekV3,
        DeepSeekV3_1_Terminus,
        DeepSeekV3_2,
        MOSS_TTSD_V0_5,
        CosyVoice2_0_5B,
        SenseVoiceSmall,
        LingFlash_2_0,
        LingMini_2_0,
        RingFlash_2_0,
        IndexTTS_2,
        Internlm2_5_7b_Chat,
        Kolors,
        KAT_Dev,
        Kimi_K2_Instruct_0905,
        Kimi_K2_Thinking,
        BceRerankerBaseV1,
        PaddleOCR_VL,
        PaddleOCR_VL_1_5,
        ProBgeReranker_V2_M3,
        ProDeepSeekR1,
        ProDeepSeekV3,
        ProDeepSeekV3_1_Terminus,
        ProDeepSeekV3_2,
        ProMiniMax_M2_5,
        ProKimi_K2_Instruct_0905,
        ProKimi_K2_Thinking,
        ProKimi_K2_5,
        ProQwen2_5_7B_Instruct,
        ProGLM4_7,
        ProGLM5,
        ProGLM5_1,
        QwenImage,
        QwenImageEdit,
        QwenImageEdit_2509,
        Qwen2_VL_72B_Instruct,
        Qwen2_5_14B_Instruct,
        Qwen2_5_32B_Instruct,
        Qwen2_5_72B_Instruct,
        Qwen2_5_72B_Instruct_128K,
        Qwen2_5_7B_Instruct,
        Qwen2_5_Coder_32B_Instruct,
        Qwen2_5_VL_32B_Instruct,
        Qwen2_5_VL_72B_Instruct,
        Qwen3_14B,
        Qwen3_235B_A22B_Instruct_2507,
        Qwen3_235B_A22B_Thinking_2507,
        Qwen3_30B_A3B_Instruct_2507,
        Qwen3_30B_A3B_Thinking_2507,
        Qwen3_32B,
        Qwen3_8B,
        Qwen3_Coder_30B_A3B_Instruct,
        Qwen3_Coder_480B_A35B_Instruct,
        Qwen3_Omni_30B_A3B_Captioner,
        Qwen3_Omni_30B_A3B_Instruct,
        Qwen3_Omni_30B_A3B_Thinking,
        Qwen3_Reranker_0_6B,
        Qwen3_Reranker_4B,
        Qwen3_Reranker_8B,
        Qwen3_VL_235B_A22B_Instruct,
        Qwen3_VL_235B_A22B_Thinking,
        Qwen3_VL_30B_A3B_Instruct,
        Qwen3_VL_30B_A3B_Thinking,
        Qwen3_VL_32B_Instruct,
        Qwen3_VL_32B_Thinking,
        Qwen3_VL_8B_Instruct,
        Qwen3_VL_8B_Thinking,
        Qwen3_5_122B_A10B,
        Qwen3_5_27B,
        Qwen3_5_35B_A3B,
        Qwen3_5_397B_A17B,
        Qwen3_5_4B,
        Qwen3_5_9B,
        QwQ_32B,
        Step3_5_Flash,
        TeleSpeechASR,
        Hunyuan_A13B_Instruct,
        Hunyuan_MT_7B,
        GLM4_32B_0414,
        GLM4_9B_0414,
        GLM4_1V_9B_Thinking,
        GLM_Z1_32B_0414,
        GLM_Z1_9B_0414,
        Wan2_2_I2V_A14B,
        Wan2_2_T2V_A14B,
        GLM4_5_Air,
        GLM4_5V,
        GLM4_6,
        GLM4_6V,
    )

    /**
     * Custom models added to the SiliconFlow provider.
     */
    private val customModels: MutableList<LLModel> = mutableListOf()

    override val models: List<LLModel>
        get() = supportedModels + customModels

    override fun addCustomModel(model: LLModel) {
        require(model.provider == LLMProvider.SiliconFlow) { "Model provider must be SiliconFlow" }
        customModels.add(model)
    }

    /**
     * Embedding models supported by SiliconFlow.
     */
    public object Embeddings {
        /**
         * BAAI/bge-large-en-v1.5.
         *
         * supports Embedding. Context length is 512.
         */
        @JvmField
        public val BgeLarge_En_V1_5: LLModel = LLModel(
            provider = LLMProvider.SiliconFlow,
            id = "BAAI/bge-large-en-v1.5",
            capabilities = listOf(
                LLMCapability.Embed,
            ),
            contextLength = 512,
        )

        /**
         * BAAI/bge-large-zh-v1.5.
         *
         * supports Embedding. Context length is 512.
         */
        @JvmField
        public val BgeLarge_Zh_V1_5: LLModel = LLModel(
            provider = LLMProvider.SiliconFlow,
            id = "BAAI/bge-large-zh-v1.5",
            capabilities = listOf(
                LLMCapability.Embed,
            ),
            contextLength = 512,
        )

        /**
         * BAAI/bge-m3.
         *
         * supports Embedding. Context length is 8k.
         */
        @JvmField
        public val BgeM3: LLModel = LLModel(
            provider = LLMProvider.SiliconFlow,
            id = "BAAI/bge-m3",
            capabilities = listOf(
                LLMCapability.Embed,
            ),
            contextLength = 8_000,
        )

        /**
         * netease-youdao/bce-embedding-base_v1.
         *
         * supports Embedding. Context length is 512.
         */
        @JvmField
        public val BceEmbedding_Base_V1: LLModel = LLModel(
            provider = LLMProvider.SiliconFlow,
            id = "netease-youdao/bce-embedding-base_v1",
            capabilities = listOf(
                LLMCapability.Embed,
            ),
            contextLength = 512,
        )

        /**
         * Pro/BAAI/bge-m3.
         *
         * supports Embedding. Context length is 8k.
         */
        @JvmField
        public val ProBgeM3: LLModel = LLModel(
            provider = LLMProvider.SiliconFlow,
            id = "Pro/BAAI/bge-m3",
            capabilities = listOf(
                LLMCapability.Embed,
            ),
            contextLength = 8_000,
        )

        /**
         * Qwen/Qwen3-Embedding-0.6B.
         *
         * supports Embedding. Context length is 32k.
         */
        @JvmField
        public val Qwen3_Embedding_0_6B: LLModel = LLModel(
            provider = LLMProvider.SiliconFlow,
            id = "Qwen/Qwen3-Embedding-0.6B",
            capabilities = listOf(
                LLMCapability.Embed,
            ),
            contextLength = 32_000,
        )

        /**
         * Qwen/Qwen3-Embedding-4B.
         *
         * supports Embedding. Context length is 32k.
         */
        @JvmField
        public val Qwen3_Embedding_4B: LLModel = LLModel(
            provider = LLMProvider.SiliconFlow,
            id = "Qwen/Qwen3-Embedding-4B",
            capabilities = listOf(
                LLMCapability.Embed,
            ),
            contextLength = 32_000,
        )

        /**
         * Qwen/Qwen3-Embedding-8B.
         *
         * supports Embedding. Context length is 32k.
         */
        @JvmField
        public val Qwen3_Embedding_8B: LLModel = LLModel(
            provider = LLMProvider.SiliconFlow,
            id = "Qwen/Qwen3-Embedding-8B",
            capabilities = listOf(
                LLMCapability.Embed,
            ),
            contextLength = 32_000,
        )

    }
}

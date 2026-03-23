package ai.koog.prompt.executor.clients.google.genai

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import com.google.genai.types.Candidate
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.Tool
import com.google.genai.types.ToolConfig

/**
 * Custom subclass of [GoogleGenaiLLMClient] that demonstrates and verifies
 * the extensibility of the protected open methods.
 *
 * Each overridden method sets a tracking flag and delegates to `super`.
 * Tests verify that the flags are set, proving the override was invoked.
 *
 * Additionally applies a real customization:
 * - Adds a `"source" to "test"` label to every request config
 */
class CustomizedGoogleGenaiLLMClient(
    client: com.google.genai.Client
) : GoogleGenaiLLMClient(client) {

    var contentsCustomized = false
        private set

    var configCustomized = false
        private set

    var toolsCustomized = false
        private set

    var toolConfigCustomized = false
        private set

    var responseCustomized = false
        private set

    var candidateCustomized = false
        private set

    var metaInfoCustomized = false
        private set

    public override fun buildSdkContents(prompt: Prompt, model: LLModel): Pair<List<Content>, Content?> {
        contentsCustomized = true
        return super.buildSdkContents(prompt, model)
    }

    public override fun buildConfig(
        params: LLMParams,
        model: LLModel,
        tools: List<ToolDescriptor>,
        systemInstruction: Content?
    ): GenerateContentConfig.Builder {
        configCustomized = true
        return super.buildConfig(params, model, tools, systemInstruction)
            .labels(mapOf("source" to "test"))
    }

    public override fun buildSdkTools(tools: List<ToolDescriptor>): List<Tool.Builder>? {
        toolsCustomized = true
        return super.buildSdkTools(tools)
    }

    public override fun buildSdkToolConfig(toolChoice: LLMParams.ToolChoice?): ToolConfig? {
        toolConfigCustomized = true
        return super.buildSdkToolConfig(toolChoice)
    }

    public override fun processResponse(response: GenerateContentResponse): List<List<Message.Response>> {
        responseCustomized = true
        return super.processResponse(response)
    }

    public override fun processCandidate(candidate: Candidate, metaInfo: ResponseMetaInfo): List<Message.Response> {
        candidateCustomized = true
        return super.processCandidate(candidate, metaInfo)
    }

    public override fun extractResponseMetaInfo(response: GenerateContentResponse): ResponseMetaInfo {
        metaInfoCustomized = true
        return super.extractResponseMetaInfo(response)
    }
}

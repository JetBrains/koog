package ai.koog.integration.tests.executor

import ai.koog.integration.tests.utils.getLLMClientForProvider
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_TEST_KEY", matches = ".+")
class DeepSeekExecutorIntegrationTest : ExecutorIntegrationTestBase() {
    private val executor by lazy {
        MultiLLMPromptExecutor(getLLMClientForProvider(LLMProvider.DeepSeek))
    }

    override fun getExecutor(model: LLModel): PromptExecutor = executor

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.utils.Models#deepSeekModels")
    override fun integration_testExecute(model: LLModel) {
        super.integration_testExecute(model)
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.utils.Models#deepSeekModels")
    override fun integration_testToolWithRequiredParams(model: LLModel) {
        super.integration_testToolWithRequiredParams(model)
    }

    @ParameterizedTest
    @MethodSource("ai.koog.integration.tests.utils.Models#deepSeekModels")
    override fun integration_testToolChoiceRequired(model: LLModel) {
        super.integration_testToolChoiceRequired(model)
    }
}

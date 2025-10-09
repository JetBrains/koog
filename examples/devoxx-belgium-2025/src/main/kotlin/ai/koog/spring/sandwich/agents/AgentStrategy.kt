package ai.koog.spring.sandwich.agents

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMCompressHistory
import ai.koog.agents.core.tools.reflect.asTool
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.ext.agent.subgraphWithVerification
import ai.koog.agents.memory.feature.history.RetrieveFactsFromHistory
import ai.koog.agents.memory.model.Concept
import ai.koog.agents.memory.model.FactType
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.spring.sandwich.structs.OrderSupportRequest
import ai.koog.spring.sandwich.structs.OrderUpdateSummary
import ai.koog.spring.sandwich.tools.CommunicationTools
import ai.koog.spring.sandwich.tools.OrderTools
import ai.koog.spring.sandwich.tools.UserTools


fun interactiveSupportStrategy(
    userTools: UserTools,
) = strategy<String, OrderUpdateSummary>("support-agent") {
    val identifyProblem by subgraphWithTask<String, OrderSupportRequest>(
        tools = CommunicationTools.asTools() + userTools::readUserOrders.asTool(),
    ) { question ->
        """
            Your goal is to identify the actual problem with the order.
            Confirm the exact order details with the user, find the order in the database, 
             figure out what is the actual problem (if unclear).
             Initial question: $question
        """.trimIndent()
    }

    val fixProblem by subgraphWithTask<OrderSupportRequest, OrderUpdateSummary>(
        tools = OrderTools.asTools() + userTools.asTools(),
        llmModel = AnthropicModels.Sonnet_4
    ) { supportRequest ->
        "You must fix the issue with the order\n Here is concise summary of it $supportRequest"
    }

    // OrderUpdateSummary -> CriticResult<OrderUpdateSummary>
    val verifySolution by subgraphWithVerification<OrderUpdateSummary>(
        tools = listOf(userTools::readUserOrders.asTool(), userTools::readUserAccount.asTool()),
        llmModel = OpenAIModels.Reasoning.O3
    ) { summary ->
        """
            Verify that:
            1. The initially identified problem has been actually resolved.
            3. Order update summary mentions EXACTLY same order as user requested
            2. Nothing extra and unnecessary has been made
            3. If refund has been issued -- it was confirmed in the user's account
            4. Carrier was notified about the changes (if relevant)
            Update summary: $summary
        """.trimIndent()
    }

    val adjust by subgraphWithTask<String, OrderUpdateSummary>(
        tools = listOf(
            OrderTools::updateAddress.asTool(),
            OrderTools::contactCarrier.asTool(),
            userTools::issueRefund.asTool()
        ),
        llmModel = AnthropicModels.Sonnet_4_5
    ) { feedback ->
        "You must fix the following problems: $feedback"
    }

    // OrderUpdateSummary -> OrderUpdateSummary,     but state history will be compressed
    val compressHistory by nodeLLMCompressHistory<OrderUpdateSummary>(
        strategy = RetrieveFactsFromHistory(
            Concept(
                keyword = "root-cause",
                description = "What was the root cause of the user's original problem",
                factType = FactType.SINGLE
            ),
            Concept(
                keyword = "important-steps",
                description = "What steps were the most important to resolve the problem",
                factType = FactType.MULTIPLE
            )
        )
    )

    edge(nodeStart forwardTo identifyProblem)
    edge(identifyProblem forwardTo fixProblem onCondition { problem -> !problem.resolved })
    edge(identifyProblem forwardTo nodeFinish onCondition { it.resolved } transformed { it.emptyUpdate() })
    edge(fixProblem forwardTo compressHistory onCondition { tooManyTokensSpent() })
    edge(fixProblem forwardTo verifySolution)
    edge(verifySolution forwardTo nodeFinish onCondition { it.successful } transformed { it.input })
    edge(verifySolution forwardTo adjust onCondition { !it.successful } transformed { it.feedback })
    edge(adjust forwardTo compressHistory onCondition { tooManyTokensSpent() })
    edge(adjust forwardTo verifySolution)
    edge(compressHistory forwardTo verifySolution)
}

suspend fun AIAgentGraphContextBase.tooManyTokensSpent(): Boolean = llm.readSession {
    prompt.latestTokenUsage > 100500
}
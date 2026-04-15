package com.example.spring_ai_java.service.customersupport;

import ai.koog.agents.chatMemory.feature.ChatHistoryProvider;
import ai.koog.agents.chatMemory.feature.ChatMemory;
import ai.koog.agents.core.agent.AIAgent;
import ai.koog.agents.core.agent.config.AIAgentConfig;
import ai.koog.agents.core.agent.context.AIAgentGraphContextBase;
import ai.koog.agents.core.agent.entity.AIAgentEdge;
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy;
import ai.koog.agents.core.agent.entity.AIAgentNode;
import ai.koog.agents.core.agent.entity.AIAgentSubgraph;
import ai.koog.agents.core.dsl.extension.HistoryCompressionStrategy;
import ai.koog.agents.core.tools.ToolRegistry;
import ai.koog.agents.core.tools.reflect.ToolFromCallable;
import ai.koog.agents.features.persistence.jdbc.PostgresJdbcPersistenceStorageProvider;
import ai.koog.agents.longtermmemory.feature.LongTermMemory;
import ai.koog.agents.longtermmemory.retrieval.SearchStrategy;
import ai.koog.agents.longtermmemory.retrieval.augmentation.PromptAugmenter;
import ai.koog.agents.snapshot.feature.Persistence;
import ai.koog.agents.snapshot.feature.RollbackToolRegistry;
import ai.koog.prompt.dsl.Prompt;
import ai.koog.prompt.executor.clients.openai.OpenAIModels;
import ai.koog.prompt.executor.model.PromptExecutor;
import ai.koog.rag.base.TextDocument;
import ai.koog.rag.base.storage.SearchStorage;
import ai.koog.rag.base.storage.search.SimilaritySearchRequest;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;

@Service
public class CustomerSupportGraphService {

    // Java equivalent of Kotlin's sealed class CheckRequestResult
    public abstract static class CheckRequestResult {
        private final SupportRequest request;

        protected CheckRequestResult(SupportRequest request) {
            this.request = request;
        }

        public SupportRequest getRequest() {
            return request;
        }

        public static class RequestDetected extends CheckRequestResult {
            public RequestDetected(SupportRequest request) {
                super(request);
            }
        }

        public static class NeedsMoreInfo extends CheckRequestResult {
            private final String clarificationQuestion;

            public NeedsMoreInfo(SupportRequest request, String clarificationQuestion) {
                super(request);
                this.clarificationQuestion = clarificationQuestion;
            }

            public String getClarificationQuestion() {
                return clarificationQuestion;
            }
        }
    }

    private final PromptExecutor promptExecutor;
    private final ChatHistoryProvider chatStorage;
    private final DataSource dataSource;
    private final SearchStorage<TextDocument, SimilaritySearchRequest> knowledgeBase;
    private final PostgresJdbcPersistenceStorageProvider persistenceStorage;

    public CustomerSupportGraphService(
        PromptExecutor promptExecutor,
        ChatHistoryProvider chatStorage,
        DataSource dataSource,
        SearchStorage<TextDocument, SimilaritySearchRequest> knowledgeBase
    ) {
        this.promptExecutor = promptExecutor;
        this.chatStorage = chatStorage;
        this.dataSource = dataSource;
        this.knowledgeBase = knowledgeBase;
        this.persistenceStorage = new PostgresJdbcPersistenceStorageProvider(dataSource);
    }

    @PostConstruct
    public void initSchema() {
        persistenceStorage.migrateBlocking();
    }

    public String createAndRunAgent(String userPrompt, String sessionId) {
        var supportTools = new EcommerceSupportTools();
        var rollbackTools = new EcommerceSupportRollbackTools();

        var agentConfig = AIAgentConfig.builder()
            .model(OpenAIModels.Chat.GPT5Nano)
            .prompt(
                Prompt.builder("ecommerce-support-level1")
                    .system("""
                        You are an e-commerce support assistant.
                        Be concise and policy-aware.
                        Never invent order data.
                        If order context is missing for an order-specific request, ask for it.
                        """)
                    .build())
            .maxAgentIterations(20)
            .build();

        var retrievalSettings = new LongTermMemory.RetrievalSettingsBuilder()
            .withStorage(knowledgeBase)
            .withSearchStrategy(
                SearchStrategy.builder().similarity().withTopK(4).withSimilarityThreshold(0.70).build()
            )
            .withPromptAugmenter(PromptAugmenter.builder().user().build())
            .build();

        var agent = AIAgent.builder()
            .promptExecutor(promptExecutor)
            .agentConfig(agentConfig)
            .toolRegistry(ToolRegistry.builder().tools(supportTools).build())
            // See IntentProcessingFlowchart.mermaid
            .graphStrategy(createControllableWorkflow(supportTools.asTools()))
            // Let the agent remember previous conversations:
            .install(ChatMemory.Feature, config -> config
                .chatHistoryProvider(chatStorage)
                .windowSize(20))
            // Augment user requests with external knowledge from our vector database:
            .install(LongTermMemory.Feature, config -> config.retrieval(retrievalSettings))
            // Make agent fault-tolerant: recover from the exact graph node where it crashed:
            .install(Persistence.Feature, config -> {
                config.setStorage(persistenceStorage);
                config.setEnableAutomaticPersistence(true);
                config.setRollbackToolRegistry(
                    RollbackToolRegistry.builder()
                        .registerRollback(
                            supportTools.getTool("changeDeliveryAddress"),
                            rollbackTools.getTool("changeDeliveryAddressToHome")
                        )
                        .build()
                );
            })
            .build();

        return agent.run(userPrompt);
    }

    // See IntentProcessingFlowchart.mermaid
    private AIAgentGraphStrategy<String, String> createControllableWorkflow(List<ToolFromCallable<?>> supportTools) {
        var graph = AIAgentGraphStrategy.builder("ecommerce_support_level1")
            .withInput(String.class)
            .withOutput(String.class);

        // 1) Detect Intent
        var classifyRequest = AIAgentSubgraph.builder("classifyRequest")
            .limitedTools(List.of())
            .withInput(String.class)
            .withOutput(SupportRequest.class)
            .withTask(input -> """
                Classify the user's support intent. Examples:
                - "Check the status of order 84721" → intent=ORDER_STATUS, orderId=84721
                - "Change the delivery address for order 84721 to 12 King Street" → intent=CHANGE_ADDRESS, orderId=84721, newAddress=12 King Street
                - "I want to return order 84721" → intent=REFUND, orderId=84721
                - "What is your refund policy?" → intent=QUESTION
                - "Hello" → intent=OTHER
                """ + input)
            .build();

        // 2) Check Context
        var checkContext = AIAgentNode.builder("checkContext")
            .withInput(SupportRequest.class)
            .withOutput(CheckRequestResult.class)
            .withAction((request, ctx) -> {
                if (request.getIntent() == SupportIntent.OTHER) {
                    return new CheckRequestResult.NeedsMoreInfo(
                        request,
                        "Specify the intent: order status, refund, change address?"
                    );
                }
                if (request.getOrderId() == null || request.getOrderId().isBlank()) {
                    return new CheckRequestResult.NeedsMoreInfo(
                        request,
                        "Please provide your order number"
                    );
                }
                return new CheckRequestResult.RequestDetected(request);
            })
            .build();

        // 3a) Order status handler
        var orderStatusFlow = AIAgentSubgraph.builder("orderStatusFlow")
            .limitedTools(supportTools)
            .withInput(SupportRequest.class)
            .withOutput(String.class)
            .withTask(req -> """
                Handle this request as an ORDER STATUS case.
                Use the order status tool and then answer the user clearly.
                
                Request: %s
                Order ID: %s
                """.formatted(req.getUserRequest(), req.getOrderId()))
            .build();

        // 3b) Change address handler
        var changeAddressFlow = AIAgentSubgraph.builder("changeAddressFlow")
            .limitedTools(supportTools)
            .withInput(SupportRequest.class)
            .withOutput(String.class)
            .withTask(req -> """
                Handle this request as a CHANGE ADDRESS case.
                Use the address-change tool if possible and explain the result.
                
                Request: %s
                Order ID: %s
                New address: %s
                """.formatted(req.getUserRequest(), req.getOrderId(), req.getNewAddress()))
            .build();

        // 3c) Refund / return handler
        var refundFlow = AIAgentSubgraph.builder("refundFlow")
            .limitedTools(supportTools)
            .withInput(SupportRequest.class)
            .withOutput(String.class)
            .withTask(req -> """
                Handle this request as a REFUND OR RETURN case.
                Check eligibility first, then explain next steps.
                
                Request: %s
                Order ID: %s
                """.formatted(req.getUserRequest(), req.getOrderId()))
            .build();

        // 3d) Fallback FAQ / policy handler
        var faqFlow = AIAgentSubgraph.builder("faqFlow")
            .limitedTools(supportTools)
            .withInput(SupportRequest.class)
            .withOutput(String.class)
            .withTask(req -> """
                Handle this request as a GENERAL FAQ / POLICY case.
                Prefer using the policy tool when relevant.
                
                Request: %s
                """.formatted(req.getUserRequest()))
            .build();

        var compressLLMHistory = AIAgentNode.llmCompressHistory("compressLLMHistory")
            .withInput(String.class)
            .compressionStrategy(HistoryCompressionStrategy.Chunked(20))
            .build();

        var maybeCompressHistory = AIAgentNode.doNothing(String.class, "maybeCompressHistory");

        // Graph edges
        graph.edge(graph.nodeStart, classifyRequest);
        graph.edge(classifyRequest, checkContext);

        graph.edge(AIAgentEdge.builder()
            .from(checkContext)
            .to(orderStatusFlow)
            .onCondition(result -> result instanceof CheckRequestResult.RequestDetected
                && ((CheckRequestResult.RequestDetected) result).getRequest().getIntent() == SupportIntent.ORDER_STATUS)
            .transformed(CheckRequestResult::getRequest)
            .build());

        graph.edge(AIAgentEdge.builder()
            .from(checkContext)
            .to(changeAddressFlow)
            .onCondition(result -> result instanceof CheckRequestResult.RequestDetected
                && ((CheckRequestResult.RequestDetected) result).getRequest().getIntent() == SupportIntent.CHANGE_ADDRESS)
            .transformed(CheckRequestResult::getRequest)
            .build());

        graph.edge(AIAgentEdge.builder()
            .from(checkContext)
            .to(refundFlow)
            .onCondition(result -> result instanceof CheckRequestResult.RequestDetected
                && ((CheckRequestResult.RequestDetected) result).getRequest().getIntent() == SupportIntent.REFUND)
            .transformed(CheckRequestResult::getRequest)
            .build());

        graph.edge(AIAgentEdge.builder()
            .from(checkContext)
            .to(faqFlow)
            .onCondition(result -> result instanceof CheckRequestResult.RequestDetected
                && ((CheckRequestResult.RequestDetected) result).getRequest().getIntent() == SupportIntent.QUESTION)
            .transformed(CheckRequestResult::getRequest)
            .build());

        graph.edge(AIAgentEdge.builder()
            .from(checkContext)
            .to(maybeCompressHistory)
            .onIsInstance(CheckRequestResult.NeedsMoreInfo.class)
            .transformed(CheckRequestResult.NeedsMoreInfo::getClarificationQuestion)
            .build());

        graph.edge(orderStatusFlow, maybeCompressHistory);
        graph.edge(changeAddressFlow, maybeCompressHistory);
        graph.edge(refundFlow, maybeCompressHistory);

        graph.edge(AIAgentEdge.builder()
            .from(maybeCompressHistory)
            .to(compressLLMHistory)
            .onCondition((result, ctx) -> tooManyTokensSpent(ctx))
            .build());

        graph.edge(AIAgentEdge.builder()
            .from(maybeCompressHistory)
            .to(graph.nodeFinish)
            .onCondition((result, ctx) -> !tooManyTokensSpent(ctx))
            .build());

        graph.edge(compressLLMHistory, graph.nodeFinish);

        return graph.build();
    }

    private boolean tooManyTokensSpent(AIAgentGraphContextBase ctx) {
        return ctx.getLlm().prompt().latestTokenUsage() > 100500;
    }
}

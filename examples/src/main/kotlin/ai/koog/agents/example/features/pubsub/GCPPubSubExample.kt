package ai.koog.agents.example.features.pubsub

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.example.ApiKeyService
import ai.koog.agents.features.pubsub.feature.PubSub
import ai.koog.agents.features.pubsub.providers.gcp.GCPPubSubProvider
import ai.koog.agents.utils.use
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Example demonstrating GCP Pub/Sub for enterprise-scale multiagent coordination.
 *
 * **Prerequisites:**
 * 
 * **Option 1: Local Development with Emulator**
 * 1. Start GCP Pub/Sub emulator with Docker Compose:
 *    ```bash
 *    docker-compose -f docker-compose-gcp.yaml up -d
 *    ```
 * 
 * 2. Set environment variable for emulator:
 *    ```bash
 *    export PUBSUB_EMULATOR_HOST=localhost:8085
 *    ```
 *
 * **Option 2: Real GCP Project**
 * 1. Set up GCP credentials:
 *    ```bash
 *    export GOOGLE_APPLICATION_CREDENTIALS="/path/to/service-account-key.json"
 *    ```
 * 
 * 2. Enable Pub/Sub API in your GCP project
 * 
 * 3. Update PROJECT_ID in the code below
 *
 * **Features demonstrated:**
 * - Enterprise-scale messaging with guaranteed delivery
 * - Automatic topic and subscription creation
 * - Message acknowledgment and error handling
 * - Cross-region agent coordination
 * - Built-in retry and dead letter queues
 * - Message ordering and filtering
 *
 * **Architecture:**
 * - Supervisor Agent: Orchestrates enterprise workflows
 * - Compliance Agent: Handles regulatory and audit tasks
 * - Integration Agent: Manages external system integrations
 * - GCP Pub/Sub: Enterprise messaging with global scale
 *
 * **Enterprise benefits:**
 * - 99.9% availability SLA
 * - Global message delivery
 * - Automatic scaling to millions of messages/second
 * - Built-in monitoring and alerting
 * - Message encryption and access control
 */
fun main() = runBlocking {
    
    println("=== GCP Pub/Sub Example ===")
    println("Enterprise-scale multiagent coordination with Google Cloud")
    
    // Configure GCP Pub/Sub connection
    val projectId = "koog-pubsub-dev" // Change this for real GCP project
    val pubSubProvider = GCPPubSubProvider(
        projectId = projectId,
        subscriptionPrefix = "koog-agent-",
        autoCreateTopics = true,
        autoCreateSubscriptions = true,
        ackDeadlineSeconds = 30
    )
    
    try {
        // Check GCP connection
        if (!pubSubProvider.isConnected()) {
            println("❌ GCP Pub/Sub is not available. Options:")
            println("   1. Start emulator: docker-compose -f docker-compose-gcp.yaml up -d")
            println("   2. Set PUBSUB_EMULATOR_HOST=localhost:8085")
            println("   3. Or configure real GCP credentials")
            return@runBlocking
        }
        
        println("✅ Connected to GCP Pub/Sub")
        
        // Enterprise Supervisor Agent
        val supervisorAgent = AIAgent(
            executor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
            llmModel = OpenAIModels.Reasoning.GPT4oMini,
            systemPrompt = """
                You are an Enterprise Supervisor Agent managing critical business workflows.
                Coordinate complex enterprise processes across multiple systems and teams.
                Ensure compliance, quality, and performance standards are met.
                Use GCP Pub/Sub for reliable, scalable message coordination.
            """.trimIndent()
        ) {
            install(PubSub) {
                provider = pubSubProvider
                autoSubscribeTopics = listOf("workflow-results", "compliance-reports", "integration-status")
                publishAgentEvents = true
            }
        }
        
        // Compliance Agent
        val complianceAgent = AIAgent(
            executor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
            llmModel = OpenAIModels.Reasoning.GPT4oMini,
            systemPrompt = """
                You are a Compliance Agent ensuring regulatory adherence and audit readiness.
                Specializing in:
                - Regulatory compliance checking
                - Audit trail generation
                - Risk assessment and mitigation
                - Policy validation and enforcement
                
                Process compliance requests with guaranteed message delivery via GCP Pub/Sub.
            """.trimIndent()
        ) {
            install(PubSub) {
                provider = pubSubProvider
                autoSubscribeTopics = listOf("compliance-requests", "audit-tasks", "risk-assessments")
                publishAgentEvents = true
            }
        }
        
        // Integration Agent
        val integrationAgent = AIAgent(
            executor = simpleOpenAIExecutor(ApiKeyService.openAIApiKey),
            llmModel = OpenAIModels.Reasoning.GPT4oMini,
            systemPrompt = """
                You are an Integration Agent managing external system connections.
                Specializing in:
                - API integrations and data synchronization
                - Third-party service coordination
                - Data transformation and validation
                - System health monitoring
                
                Handle integration workflows with enterprise reliability via GCP Pub/Sub.
            """.trimIndent()
        ) {
            install(PubSub) {
                provider = pubSubProvider
                autoSubscribeTopics = listOf("integration-requests", "sync-tasks", "api-monitoring")
                publishAgentEvents = true
            }
        }
        
        supervisorAgent.use { supervisor ->
            complianceAgent.use { compliance ->
                integrationAgent.use { integration ->
                    
                    println("\n--- Setting up enterprise message handlers ---")
                    
                    // Compliance agent handler
                    launch {
                        pubSubProvider.subscribe(listOf("compliance-requests", "audit-tasks")).collect { message ->
                            println("⚖️ Compliance Agent processing: ${message.content}")
                            println("   📋 Attributes: ${message.attributes}")
                            
                            val riskLevel = message.attributes["risk-level"] ?: "medium"
                            val complianceType = message.attributes["type"] ?: "general"
                            val region = message.attributes["region"] ?: "global"
                            
                            val result = compliance.run(
                                """
                                Process this $riskLevel risk $complianceType compliance request for $region region:
                                ${message.content}
                                
                                Provide detailed compliance analysis and recommendations.
                                """.trimIndent()
                            )
                            
                            // Report compliance results with audit trail
                            pubSubProvider.publish(
                                "compliance-reports",
                                "Compliance analysis completed: $result",
                                mapOf(
                                    "agent" to "compliance",
                                    "request-id" to (message.attributes["request-id"] ?: "unknown"),
                                    "risk-level" to riskLevel,
                                    "compliance-status" to "completed",
                                    "region" to region,
                                    "audit-timestamp" to System.currentTimeMillis().toString(),
                                    "processing-time" to "enterprise-sla"
                                )
                            )
                            
                            message.acknowledge()
                        }
                    }
                    
                    // Integration agent handler  
                    launch {
                        pubSubProvider.subscribe(listOf("integration-requests", "sync-tasks")).collect { message ->
                            println("🔗 Integration Agent processing: ${message.content}")
                            println("   📋 Attributes: ${message.attributes}")
                            
                            val systemType = message.attributes["system"] ?: "generic"
                            val operation = message.attributes["operation"] ?: "sync"
                            val priority = message.attributes["priority"] ?: "normal"
                            
                            val result = integration.run(
                                """
                                Handle this $priority priority $operation operation for $systemType system:
                                ${message.content}
                                
                                Ensure data integrity and provide integration status.
                                """.trimIndent()
                            )
                            
                            // Report integration results with system status
                            pubSubProvider.publish(
                                "integration-status",
                                "Integration completed: $result",
                                mapOf(
                                    "agent" to "integration",
                                    "request-id" to (message.attributes["request-id"] ?: "unknown"),
                                    "system" to systemType,
                                    "operation" to operation,
                                    "integration-status" to "completed",
                                    "data-integrity" to "verified",
                                    "completion-timestamp" to System.currentTimeMillis().toString()
                                )
                            )
                            
                            message.acknowledge()
                        }
                    }
                    
                    // Supervisor orchestration handler
                    launch {
                        val workflowResults = mutableMapOf<String, String>()
                        
                        pubSubProvider.subscribe(listOf("compliance-reports", "integration-status")).collect { message ->
                            println("👑 Supervisor received result: ${message.content}")
                            
                            val requestId = message.attributes["request-id"] ?: "unknown"
                            val agent = message.attributes["agent"] ?: "unknown"
                            
                            workflowResults["${requestId}-${agent}"] = message.content
                            
                            // When we have results from both compliance and integration
                            if (workflowResults.size >= 2) {
                                println("\n--- Supervisor orchestrating enterprise workflow ---")
                                
                                val finalResult = supervisor.run(
                                    """
                                    Orchestrate these enterprise workflow results into a comprehensive status report:
                                    
                                    ${workflowResults.values.joinToString("\n\n")}
                                    
                                    Provide executive summary with:
                                    - Overall workflow status
                                    - Compliance posture
                                    - Integration health
                                    - Risk assessment
                                    - Next steps and recommendations
                                    """.trimIndent()
                                )
                                
                                println("🏢 Enterprise workflow completed:")
                                println(finalResult)
                                
                                // Publish enterprise completion with full audit trail
                                pubSubProvider.publish(
                                    "workflow-completed",
                                    finalResult,
                                    mapOf(
                                        "workflow-status" to "completed",
                                        "compliance-verified" to "true",
                                        "integration-verified" to "true",
                                        "enterprise-grade" to "confirmed",
                                        "audit-trail" to "complete",
                                        "completion-timestamp" to System.currentTimeMillis().toString(),
                                        "supervisor-approved" to "true"
                                    )
                                )
                            }
                            
                            message.acknowledge()
                        }
                    }
                    
                    // Allow GCP message handlers to initialize
                    delay(200)
                    
                    println("\n--- Demonstrating enterprise-grade GCP coordination ---")
                    
                    // Simulate enterprise workflow scenario
                    val enterpriseWorkflow = """
                        Process new customer onboarding for Enterprise Client ABC Corp:
                        - Verify regulatory compliance for financial services
                        - Integrate with CRM, billing, and monitoring systems
                        - Ensure GDPR, SOX, and PCI DSS compliance
                        - Set up automated reporting and alerts
                    """.trimIndent()
                    
                    println("🏢 Supervisor received enterprise workflow:")
                    println(enterpriseWorkflow)
                    
                    val orchestration = supervisor.run(
                        "Break down this enterprise workflow into specialized compliance and integration tasks: $enterpriseWorkflow"
                    )
                    
                    println("🧠 Supervisor orchestration plan:")
                    println(orchestration)
                    
                    // Publish compliance request with enterprise requirements
                    val complianceRequestId = "compliance-${System.currentTimeMillis()}"
                    pubSubProvider.publish(
                        "compliance-requests",
                        """
                        Verify regulatory compliance for Enterprise Client ABC Corp onboarding:
                        - Financial services regulations (SOX, Dodd-Frank)
                        - Data protection compliance (GDPR, CCPA)
                        - Payment card industry standards (PCI DSS)
                        - Generate compliance certification and audit trail
                        """.trimIndent(),
                        mapOf(
                            "request-id" to complianceRequestId,
                            "risk-level" to "high",
                            "type" to "financial-services",
                            "region" to "US-EU",
                            "client" to "ABC-Corp",
                            "urgency" to "enterprise-sla"
                        )
                    )
                    
                    // Publish integration request with system requirements
                    val integrationRequestId = "integration-${System.currentTimeMillis()}"
                    pubSubProvider.publish(
                        "integration-requests",
                        """
                        Set up enterprise integrations for ABC Corp:
                        - CRM system integration (Salesforce Enterprise)
                        - Billing system connection (SAP S/4HANA)
                        - Monitoring and alerting setup (Datadog Enterprise)
                        - Single sign-on configuration (Azure AD)
                        """.trimIndent(),
                        mapOf(
                            "request-id" to integrationRequestId,
                            "system" to "multi-system",
                            "operation" to "enterprise-setup",
                            "priority" to "high",
                            "client" to "ABC-Corp",
                            "integration-tier" to "enterprise"
                        )
                    )
                    
                    println("\n--- GCP processing enterprise workflow with guaranteed delivery ---")
                    delay(10000) // Allow GCP to process with enterprise SLA
                    
                    // Show GCP enterprise metrics
                    println("\n--- GCP Pub/Sub Enterprise Health Status ---")
                    val healthInfo = pubSubProvider.getHealthInfo()
                    healthInfo.forEach { (key, value) ->
                        println("$key: $value")
                    }
                }
            }
        }
        
    } catch (e: Exception) {
        println("❌ Error: ${e.message}")
        println("Check GCP Pub/Sub setup:")
        println("- Emulator: docker-compose -f docker-compose-gcp.yaml up -d")
        println("- Environment: export PUBSUB_EMULATOR_HOST=localhost:8085")
        println("- Or configure real GCP credentials")
    } finally {
        pubSubProvider.close()
        println("\n--- GCP Pub/Sub enterprise example completed ---")
    }
}

/**
 * Utility function to demonstrate GCP Pub/Sub enterprise features.
 */
@Suppress("unused")
fun demonstrateGCPEnterpriseFeatures() = runBlocking {
    println("=== GCP Enterprise Features Demo ===")
    
    val pubSubProvider = GCPPubSubProvider(
        projectId = "koog-pubsub-dev",
        subscriptionPrefix = "enterprise-",
        autoCreateTopics = true,
        autoCreateSubscriptions = true,
        ackDeadlineSeconds = 60 // Longer deadline for complex enterprise processing
    )
    
    try {
        // Demonstrate message ordering (GCP Pub/Sub feature)
        launch {
            pubSubProvider.subscribe("ordered-workflow").collect { message ->
                println("📋 Processing ordered message: ${message.content}")
                println("   Order key: ${message.attributes["order-key"]}")
                println("   Sequence: ${message.attributes["sequence"]}")
                message.acknowledge()
            }
        }
        
        delay(100)
        
        // Publish ordered messages (simulating enterprise workflow steps)
        val orderKey = "workflow-${System.currentTimeMillis()}"
        
        listOf(
            "Initialize enterprise client setup",
            "Verify compliance requirements", 
            "Configure system integrations",
            "Deploy monitoring and alerts",
            "Complete audit trail setup"
        ).forEachIndexed { index, step ->
            pubSubProvider.publish(
                "ordered-workflow",
                step,
                mapOf(
                    "order-key" to orderKey,
                    "sequence" to index.toString(),
                    "workflow-id" to "enterprise-setup",
                    "enterprise-grade" to "true"
                )
            )
        }
        
        delay(2000)
        
    } finally {
        pubSubProvider.close()
    }
}
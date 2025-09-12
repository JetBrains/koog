package ai.koog.agents.example.subgraphwithtask

import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.ext.agent.VerifiedSubgraphResult
import ai.koog.agents.ext.agent.subgraphWithTask
import ai.koog.agents.ext.agent.subgraphWithVerification
import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels

@OptIn(InternalAgentToolsApi::class)
fun customWizardStrategy(
    generateTools: List<Tool<*, *>>,
    verifyTools: List<Tool<*, *>>,
    fixTools: List<Tool<*, *>>
) = strategy<String, String>("wizard-with-checkstyle") {
    val generate by subgraphWithTask<Unit, String>(
        generateTools,
        llmModel = OpenAIModels.Chat.GPT4o,
    ) { input ->
        """
            You are an AI agent that can create files and folders inside some empty project repository.
            Your task is to help users with setting up a file and folder structure for the future development of their project.
            Please, make sure you create all necessary files and folders (with sub-folders).
            Please, make sure to prepare all sub-folders for all the packages of the project (for example,
            if you are creating a Java project with com.example packages, please make sure to create src/main/java/com/example folders).
            Important:
            1. You are working with the relative paths inside your project.
            2. Initially, the project is empty.
            3. DO NOT FINISH BEFORE CREATING ALL FILES AND FOLDERS.
            4. ONLY CALL TOOLS, DON'T CHAT WITH ME!!!!!!!!!!!!!!!!!!
        """.trimIndent()
    }

    val fix by subgraphWithTask<VerifiedSubgraphResult, String>(
        tools = fixTools,
        llmModel = AnthropicModels.Sonnet_3_7,
    ) { verificationResult ->
        """
            You are an AI agent that can create files, delete files, create folders, and delete folders.

            Your primary task is to fix the project build and makes sure that all the following problems are resolved:
            ${verificationResult.message}

            3. DO NOT FINISH BEFORE YOU CHANGED EVERYTHING THAT IS REQUIRED TO MAKE THE PROJECT WORK.
            4. ONLY CALL TOOLS, DON'T CHAT WITH ME!!!!!!!!!!!!!!!!!!
        """.trimIndent()
    }

    val verify by subgraphWithVerification(
        verifyTools
    ) { input: String ->
        """
                You have to check and verify that the created project in the current directory is not broken by calling appropriate tools.
                You have access to a shell terminal and can use shell commands to aid in the verification process.
                
                YOU ARE NOT ALLOWED TO MODIFY THE PROJECT! YOU ARE NOT ALLOWED TO CHANGE ANYTHING IN THE PROJECT!
                YOU CAN ONLY CALL SHELL COMMANDS THAT WOULD TRY TO BUILD OR RUN TESTS, OR ANALYZE THE PROJECT.
    
                YOU CAN ALSO READ FILES AND DIRECTORIES IF YOU LIKE.
                
                Once you are finished and 100% sure the project is correct, provide the result by calling the finalization tool.
        """.trimIndent()
    }

    edge(nodeStart forwardTo generate transformed { })
    edge(generate forwardTo verify transformed { "Project is generated and is ready for verification." })
    edge(verify forwardTo fix onCondition { !it.correct })
    edge(verify forwardTo nodeFinish onCondition { it.correct } transformed { "Project is correct." })
    edge(fix forwardTo verify)
}

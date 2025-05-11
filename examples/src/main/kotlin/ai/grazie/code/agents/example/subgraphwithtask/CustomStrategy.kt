package ai.grazie.code.agents.example.subgraphwithtask

import ai.grazie.code.agents.core.tools.Tool
import ai.grazie.code.agents.core.agent.entity.ContextTransitionPolicy
import ai.grazie.code.agents.core.dsl.builder.forwardTo
import ai.grazie.code.agents.core.dsl.builder.strategy
import ai.grazie.code.agents.core.dsl.extension.ProvideVerifiedSubgraphResult
import ai.grazie.code.agents.core.dsl.extension.VerifiedSubgraphResult
import ai.grazie.code.agents.core.dsl.extension.subgraphWithTask
import ai.grazie.code.agents.core.dsl.extension.subgraphWithVerification
import ai.jetbrains.code.prompt.executor.clients.anthropic.AnthropicModels
import ai.jetbrains.code.prompt.executor.clients.openai.OpenAIModels

fun customWizardStrategy(
    generateTools: List<Tool<*, *>>,
    verifyTools: List<Tool<*, *>>,
    fixTools: List<Tool<*, *>>
) = strategy("wizard-with-checkstyle", llmHistoryTransitionPolicy = ContextTransitionPolicy.COMPRESS_LLM_HISTORY) {
    stage("generate-verify-and-fix") {
        val generate by subgraphWithTask<Unit>(
            generateTools,
            model = OpenAIModels.General.GPT4o,
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

        val fix by subgraphWithTask<VerifiedSubgraphResult>(
            tools = fixTools,
            model = AnthropicModels.Sonnet_3_7,
            shouldTLDRHistory = true
        ) { verificationResult ->
            """
            You are an AI agent that can create files, delete files, create folders, and delete folders.

            Your primary task is to fix the project buld and makes sure that all the following problems are resolved:
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
                
                Once you are finished and 100% sure the project is correct, provide the result by calling the `${ProvideVerifiedSubgraphResult.name}` tool.
            """.trimIndent()
        }

        edge(nodeStart forwardTo generate)
        edge(generate forwardTo verify transformed { "Project is generated and is ready for verification." })
        edge(verify forwardTo fix onCondition { !it.correct })
        edge(verify forwardTo nodeFinish onCondition { it.correct } transformed { "Project is correct." })
        edge(fix forwardTo verify transformed { it.result })
    }
}

package com.example.spring_ai_kotlin.controller

import com.example.spring_ai_kotlin.service.customersupport.CustomerSupportGraphService
import com.example.spring_ai_kotlin.service.deepresearch.DeepLiteratureResearchService
import com.example.spring_ai_kotlin.service.knowledgebase.KnowledgeBaseNaiveService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

private val logger = KotlinLogging.logger {}

@RestController
class ChatController(
    val knowledgeBaseService: KnowledgeBaseNaiveService,
    val customerSupportService: CustomerSupportGraphService,
    val literatureResearchService: DeepLiteratureResearchService
) {

    @PostMapping(value = ["/knowledgeBase"])
    fun knowledgeBase(@RequestBody request: ChatRequest): ChatResponse {
        try {
            val result = runBlocking {
                knowledgeBaseService.createAndRunAgent(request.prompt)
            }
            return ChatResponse(result)
        } catch (e: Exception) {
            logger.error(e) { "Failed to run an agent" }
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to run an agent")
        }
    }

    @PostMapping(value = ["/customerSupport"])
    fun customerSupport(@RequestBody request: ChatRequest): ChatResponse {
        try {
            val result = runBlocking {
                customerSupportService.createAndRunAgent(request.prompt, "customer-123")
            }
            return ChatResponse(result)
        } catch (e: Exception) {
            logger.error(e) { "Failed to run an agent" }
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to run an agent")
        }
    }

    @PostMapping(value = ["/deepResearch"])
    fun deepResearch(@RequestBody request: ChatRequest): ChatResponse {
        try {
            val result = runBlocking {
                literatureResearchService.createAndRunAgent(request.prompt)
            }
            return ChatResponse(result)
        } catch (e: Exception) {
            logger.error(e) { "Failed to run an agent" }
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to run an agent")
        }
    }

}

data class ChatRequest(val prompt: String)

data class ChatResponse(val response: String)

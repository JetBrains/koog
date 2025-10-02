package com.example.agent.controller

import com.example.agent.service.AgentService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

private val logger = KotlinLogging.logger {}

@RestController
class ChatController(val agentService: AgentService) {

    @PostMapping(value = ["/chat"])
    suspend fun chat(@RequestBody request: ChatRequest): ChatResponse {
        try {
            val result = agentService.createAndRunAgent(request.prompt)
            return ChatResponse(result)
        } catch (e: Exception) {
            logger.error(e) { "Failed to run an agent" }
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to run an agent")
        }
    }
}


data class ChatRequest(val prompt: String)

data class ChatResponse(val response: String)
